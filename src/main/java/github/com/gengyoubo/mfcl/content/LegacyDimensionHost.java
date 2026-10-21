package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import github.com.gengyoubo.mfcl.bridge.LegacyGeneration;
import github.com.gengyoubo.mfcl.bridge.LegacyTravel;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import java.util.*;

/**
 * Host side of mirrored old dimensions: original chunk population after the host made an (empty) chunk, old world
 * saves, and player transfers between levels. Everything runs on the server thread, where legacy worlds are owned.
 */
final class LegacyDimensionHost {
    /** Soft budget between indivisible old populate calls; a single original callback may exceed it. */
    private static final long POPULATION_BUDGET_NANOS = 8_000_000L;
    private final ModernContentBridge content;
    private final LegacyGeneration generation;
    private final Map<ServerLevel, LongLinkedOpenHashSet> pending = new WeakHashMap<>();
    private final List<HostWorldAccess.Transfer> transfers = new ArrayList<>();
    private final Set<String> skippedBlocks = new HashSet<>();
    private final Map<ServerLevel,LongOpenHashSet> importing=new WeakHashMap<>(), failedTerrain=new WeakHashMap<>();
    private boolean generating;
    LegacyDimensionHost(ModernContentBridge content) {
        this.content = content; this.generation = new LegacyGeneration(content.services());
        MinecraftForge.EVENT_BUS.addListener(this::chunkLoaded); MinecraftForge.EVENT_BUS.addListener(this::save);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
    }
    boolean generating() { return generating; }
    /** An original block without a host representation is skipped while old generators run, like an absent mod block. */
    boolean skipUnsupported(HostWorldAccess.Block block, RuntimeException failure) {
        if (!generating) return false;
        if (skippedBlocks.add(block.legacyKey() + "@" + block.metadata())) LogUtils.getLogger().warn("MFCL original world generation skipped {}: {}", block, failure.getMessage());
        return true;
    }
    static java.io.File saveDirectory(ServerLevel level) { return level.getServer().getWorldPath(LevelResource.ROOT).resolve("mfcl/legacy-1.7.10").toFile(); }
    private boolean mirrored(ServerLevel level) { return content.services().dimensions().byHostKey(level.dimension().location().toString()) != null; }

    void loaded(ServerLevel level) {
        try { content.services().worlds().loaded(level); }
        catch (Exception failure) { LogUtils.getLogger().error("MFCL original world load hooks failed for {}", level.dimension().location(), failure); }
    }
    void unloading(ServerLevel level) { generation.forget(level); pending.remove(level);importing.remove(level);failedTerrain.remove(level); }
    private void save(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level) || content.services().worlds().findBound(level) == null) return;
        try { content.services().worlds().save(level); }
        catch (Exception failure) { LogUtils.getLogger().error("MFCL original world data save failed for {}", level.dimension().location(), failure); }
    }

    // ---- population -------------------------------------------------------------------------------------------
    /** Chunks of a mirrored dimension that already ran original population. Saved with the host level. */
    private static final class Populated extends SavedData {
        final LongOpenHashSet chunks = new LongOpenHashSet();
        final LongOpenHashSet terrain = new LongOpenHashSet();
        static Populated load(CompoundTag tag) { var data = new Populated(); for (long chunk : tag.getLongArray("chunks")) data.chunks.add(chunk); for(long chunk:tag.getLongArray("terrain"))data.terrain.add(chunk); return data; }
        @Override public CompoundTag save(CompoundTag tag) { tag.putLongArray("chunks", chunks.toLongArray()); tag.putLongArray("terrain",terrain.toLongArray()); return tag; }
    }
    private static Populated populated(ServerLevel level) { return level.getDataStorage().computeIfAbsent(Populated::load, Populated::new, "mfcl_legacy_population"); }
    /** Base terrain precedes any old feature reads/writes in that chunk; never replace a previously generated palette. */
    void terrain(ServerLevel level,int cx,int cz) {
        if(!content.services().dimensions().hasTerrain(level.dimension().location().toString()))return;
        var done=populated(level);long key=ChunkPos.asLong(cx,cz);
        if(done.terrain.contains(key))return;
        var active=importing.computeIfAbsent(level,ignored->new LongOpenHashSet());
        if(!active.add(key))return;
        var failed=failedTerrain.computeIfAbsent(level,ignored->new LongOpenHashSet());
        try {
            if(failed.contains(key))throw new IllegalStateException("Original terrain previously failed in this session at "+cx+", "+cz);
            HostTerrain.copy(content,generation,level,cx,cz);done.terrain.add(key);done.setDirty();
        }catch(Exception failure){failed.add(key);throw new IllegalStateException("Cannot generate original terrain at "+cx+", "+cz,failure);}
        finally{active.remove(key);}
    }
    private void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !mirrored(level)) return;
        long chunk = event.getChunk().getPos().toLong();
        synchronized (pending) { pending.computeIfAbsent(level, ignored -> new LongLinkedOpenHashSet()).add(chunk); }
    }
    /** Runs original population for a loaded host chunk once; later loads of the chunk keep what it wrote. */
    private void populate(ServerLevel level, long chunk) {
        var done = populated(level);
        if (done.chunks.contains(chunk)) return;
        int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
        if (level.getChunkSource().getChunkNow(x, z) == null) return; // Queued again when it loads.
        // Marked first: a generator that fails would otherwise be retried on every load.
        done.chunks.add(chunk); done.setDirty();
        boolean outer = generating; generating = true;
        try { terrain(level,x,z); generation.populate(level, x, z); }
        catch (Exception | LinkageError failure) { LogUtils.getLogger().error("MFCL original population of chunk [{}, {}] in {} failed", x, z, level.dimension().location(), failure); }
        finally { generating = outer; }
    }
    /** Loads the chunks around a column and, in a mirrored dimension, populates them before anyone arrives. */
    private void prepare(ServerLevel level, int blockX, int blockZ, int radius) {
        boolean baseTerrain=content.services().dimensions().hasTerrain(level.dimension().location().toString());
        if(baseTerrain)radius=Math.min(radius,1);
        int cx = blockX >> 4, cz = blockZ >> 4;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) level.getChunk(cx + dx, cz + dz);
        if (!mirrored(level)) return;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) terrain(level,cx+dx,cz+dz);
        // A Deep Dark room only needs its base palette. Decoration can wait for budgeted ticks after arrival.
        if(baseTerrain)return;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) populate(level, ChunkPos.asLong(cx + dx, cz + dz));
    }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        List<HostWorldAccess.Transfer> due;
        synchronized (transfers) { due = List.copyOf(transfers); transfers.clear(); }
        for (var transfer : due) {
            try { transfer(event.getServer(), transfer); }
            catch (Exception | LinkageError failure) { LogUtils.getLogger().error("MFCL original dimension transfer to {} failed", transfer.legacyDimension(), failure); }
        }
        long deadline = System.nanoTime() + POPULATION_BUDGET_NANOS;
        int decorated=0;
        for (var level : event.getServer().getAllLevels()) {
            LongLinkedOpenHashSet queue;
            synchronized (pending) { queue = pending.get(level); }
            int remaining; synchronized(pending){remaining=queue==null?0:queue.size();}
            while (remaining-->0 && decorated<1 && System.nanoTime() < deadline) {
                long chunk;
                synchronized (pending) { if (queue.isEmpty()) break; chunk = queue.removeFirstLong(); }
                if(populated(level).chunks.contains(chunk)||level.getChunkSource().getChunkNow(ChunkPos.getX(chunk),ChunkPos.getZ(chunk))==null)continue;
                // Feature lookups may load neighboring chunks. Do not decorate those endlessly away from players.
                if(content.services().dimensions().hasTerrain(level.dimension().location().toString())
                        &&level.getChunkSource().chunkMap.getPlayers(new ChunkPos(chunk),false).isEmpty()) {
                    synchronized(pending){queue.add(chunk);}continue;
                }
                populate(level, chunk);
                decorated++;
            }
        }
    }

    // ---- travel -----------------------------------------------------------------------------------------------
    void queue(HostWorldAccess.Transfer transfer) { synchronized (transfers) { transfers.add(transfer); } }
    private ServerLevel level(net.minecraft.server.MinecraftServer server, int legacyDimension) {
        for (var level : server.getAllLevels()) if (content.services().dimensions().legacyId(level.dimension().location().toString()) == legacyDimension) return level;
        return null;
    }
    private void transfer(net.minecraft.server.MinecraftServer server, HostWorldAccess.Transfer transfer) throws Exception {
        ServerPlayer player = server.getPlayerList().getPlayer(transfer.player());
        if (player == null || !player.isAlive() || player.isPassenger() || player.isVehicle()) return;
        ServerLevel target = level(server, transfer.legacyDimension());
        if (target == null) { LogUtils.getLogger().warn("MFCL: no host level mirrors legacy dimension {}", transfer.legacyDimension()); return; }
        // ServerConfigurationManager.transferEntityToWorld kept x/z, scaled only between dimensions of different movement factors.
        double scale = net.minecraft.world.level.dimension.DimensionType.getTeleportationScale(player.serverLevel().dimensionType(), target.dimensionType());
        var border = target.getWorldBorder();
        double x = border.getCenterX() + Math.max(-border.getSize() / 2 + 1, Math.min(border.getSize() / 2 - 1, transfer.x() * scale - border.getCenterX()));
        double z = border.getCenterZ() + Math.max(-border.getSize() / 2 + 1, Math.min(border.getSize() / 2 - 1, transfer.z() * scale - border.getCenterZ()));
        prepare(target, (int) Math.floor(x), (int) Math.floor(z), 2);
        double[] placed = content.services().items().inLegacy(() ->
                LegacyTravel.place(content.services().worlds().bound(target), transfer.teleporter(), x, transfer.y(), z, transfer.yaw()));
        player.teleportTo(target, placed[0], placed[1], placed[2], transfer.yaw(), transfer.pitch());
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); player.fallDistance = 0;
        LogUtils.getLogger().info("MFCL moved {} to {} at {}, {}, {}", player.getName().getString(), target.dimension().location(), placed[0], placed[1], placed[2]);
    }
    /** Nearest block of an old registry key in loaded host chunks, scanning only sections whose palette can contain it. */
    int[] findBlock(ServerLevel level, String legacyKey, int x, int y, int z, int radius) {
        return findBlock(level,legacyKey,0,0,x,y,z,radius);
    }
    int[] findBlock(ServerLevel level,String legacyKey,int mask,int metadata,int x,int y,int z,int radius) {
        var target = content.modernBlock(new HostWorldAccess.Block(legacyKey, 0)).getBlock();
        int[] best = null; double bestDistance = Double.MAX_VALUE;
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz); if (chunk == null) continue;
            var sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                var section = sections[index];
                if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> state.getBlock() == target)) continue;
                int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
                for (int dx = 0; dx < 16; dx++) for (int dy = 0; dy < 16; dy++) for (int dz = 0; dz < 16; dz++) {
                    if (section.getBlockState(dx, dy, dz).getBlock() != target) continue;
                    if(mask!=0&&(content.legacyBlock(section.getBlockState(dx,dy,dz)).metadata()&mask)!=(metadata&mask))continue;
                    int bx = (cx << 4) + dx, by = baseY + dy, bz = (cz << 4) + dz;
                    if (Math.abs(bx - x) > radius || Math.abs(bz - z) > radius) continue;
                    double distance = (bx + .5 - x) * (bx + .5 - x) + (by + .5 - y) * (by + .5 - y) + (bz + .5 - z) * (bz + .5 - z);
                    if (distance < bestDistance) { bestDistance = distance; best = new int[]{bx, by, bz}; }
                }
            }
        }
        return best;
    }
    /** Old EntityPlayerMP.timeUntilPortal on a freshly mirrored actor. */
    Object portalState(Object actor, ServerPlayer player) throws ReflectiveOperationException {
        var type = content.services().loader().loadClass("net.minecraft.entity.player.EntityPlayerMP");
        if (type.isInstance(actor)) type.getField("field_71088_bW").setInt(actor, player.getPortalCooldown());
        return actor;
    }
}
