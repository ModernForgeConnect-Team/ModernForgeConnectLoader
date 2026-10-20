package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import java.util.*;

/** Server levels own all authoritative mutations; the old World façade keeps its owner-thread checks. */
public final class LevelWorldBridge {
    private final ModernContentBridge content;
    private final LegacyDimensionHost dimensions;
    /**
     * Old scheduled updates on host vanilla blocks. 1.7.10 ran BlockCrops/BlockSapling growth from updateTick for both
     * scheduled and random ticks, so the lamp of growth schedules crops; the host only grows them in randomTick.
     */
    private final Map<ServerLevel, Map<BlockPos, Long>> hostTicks = new WeakHashMap<>();
    private LevelWorldBridge(ModernContentBridge content) { this.content = content; this.dimensions = new LegacyDimensionHost(content); }
    public static void install(ModernContentBridge content) {
        var bridge = new LevelWorldBridge(content);
        MinecraftForge.EVENT_BUS.addListener(bridge::load); MinecraftForge.EVENT_BUS.addListener(bridge::unload);
        MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    private void load(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String key = level.dimension().location().toString();
        int dimension = content.services().dimensions().legacyId(key);
        try { content.services().worlds().bind(level, access(level, dimension)); }
        catch (Exception error) { throw new IllegalStateException("Cannot bind legacy server world " + key, error); }
        dimensions.loaded(level);
    }
    private void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) { dimensions.unloading(level); content.services().worlds().unbind(level); hostTicks.remove(level); }
    }
    private void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var pending = hostTicks.get(level); if (pending == null || pending.isEmpty()) return;
        long now = level.getGameTime(); var due = new ArrayList<BlockPos>();
        pending.entrySet().removeIf(entry -> { if (entry.getValue() > now) return false; due.add(entry.getKey()); return true; });
        for (var pos : due) {
            if (!level.isLoaded(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.isRandomlyTicking()) state.randomTick(level, pos, level.random);
        }
    }
    /** Old DamageSource by damageType first, then by its flags (fire, projectile, magic, explosion) and attacker. */
    private static net.minecraft.world.damagesource.DamageSource damageSource(ServerLevel level, HostWorldAccess.Damage damage, net.minecraft.world.entity.Entity direct, net.minecraft.world.entity.Entity attacker) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> key = switch (damage.type()) {
            case "inFire" -> net.minecraft.world.damagesource.DamageTypes.IN_FIRE;
            case "onFire" -> net.minecraft.world.damagesource.DamageTypes.ON_FIRE;
            case "lava" -> net.minecraft.world.damagesource.DamageTypes.LAVA;
            case "inWall" -> net.minecraft.world.damagesource.DamageTypes.IN_WALL;
            case "drown" -> net.minecraft.world.damagesource.DamageTypes.DROWN;
            case "starve" -> net.minecraft.world.damagesource.DamageTypes.STARVE;
            case "cactus" -> net.minecraft.world.damagesource.DamageTypes.CACTUS;
            case "fall" -> net.minecraft.world.damagesource.DamageTypes.FALL;
            case "outOfWorld" -> net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD;
            case "wither" -> net.minecraft.world.damagesource.DamageTypes.WITHER;
            case "anvil" -> net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL;
            case "fallingBlock" -> net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK;
            case "thorns" -> net.minecraft.world.damagesource.DamageTypes.THORNS;
            case "fireball" -> net.minecraft.world.damagesource.DamageTypes.FIREBALL;
            case "arrow" -> net.minecraft.world.damagesource.DamageTypes.ARROW;
            case "thrown" -> net.minecraft.world.damagesource.DamageTypes.THROWN;
            case "indirectMagic" -> net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC;
            case "magic" -> net.minecraft.world.damagesource.DamageTypes.MAGIC;
            default -> null;
        };
        boolean player = attacker instanceof net.minecraft.world.entity.player.Player;
        // Armor-piercing original damage (TC4 air/order arrows) maps to the host's armor-bypassing magic types.
        if (key == null && damage.bypassArmor() && !damage.explosion() && !damage.fire())
            key = attacker != null ? net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC : net.minecraft.world.damagesource.DamageTypes.MAGIC;
        if (key == null) key = damage.explosion() ? (player ? net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION : net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
                : damage.projectile() ? (damage.fire() ? net.minecraft.world.damagesource.DamageTypes.FIREBALL : net.minecraft.world.damagesource.DamageTypes.THROWN)
                : damage.magic() ? (attacker != null ? net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC : net.minecraft.world.damagesource.DamageTypes.MAGIC)
                : damage.fire() ? net.minecraft.world.damagesource.DamageTypes.ON_FIRE
                : player ? net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK
                : attacker instanceof net.minecraft.world.entity.LivingEntity ? net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK
                : net.minecraft.world.damagesource.DamageTypes.GENERIC;
        var type = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new net.minecraft.world.damagesource.DamageSource(type, direct, attacker);
    }
    private HostWorldAccess access(ServerLevel level, int dimension) {
        return new HostWorldAccess() {
            private void check() {
                if (!level.getServer().isSameThread()) throw new IllegalStateException("Legacy server world requires the server thread");
            }
            @Override public String dimensionKey() { return level.dimension().location().toString(); }
            @Override public int legacyDimensionId() { return dimension; }
            @Override public boolean clientSide() { return false; }
            @Override public String biome(int x,int z) {check();return HostBiomeQueries.key(level,x,z);}
            @Override public void setBiome(int x,int z,String key) {check();content.afterCommit(level,()->HostBiomeQueries.set(level,x,z,key));}
            @Override public void playSound(Sound sound) {
                check();var id=net.minecraft.resources.ResourceLocation.parse(github.com.gengyoubo.mfcl.bridge.LegacySounds.modernId(sound.id()));
                var excluded=sound.excludedPlayer()==null?null:level.getServer().getPlayerList().getPlayer(sound.excludedPlayer());
                content.afterCommit(level,()->level.playSound(excluded,sound.x(),sound.y(),sound.z(),net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.MASTER,sound.volume(),sound.pitch()));
            }
            @Override public long gameTime() { check(); return level.getGameTime(); }
            @Override public java.util.List<HostWorldAccess.Entity> entities(double a,double b,double c,double d,double e,double f) {check();return HostEntityQueries.query(level,a,b,c,d,e,f);}
            @Override public void applyMotion(HostWorldAccess.Motion motion) {check();content.afterCommit(level,()->HostEntityQueries.apply(level,motion));}
            @Override public java.util.List<HostWorldAccess.Entity> players() {
                check(); return level.players().stream().map(player -> HostEntityQueries.snapshot(level, player)).filter(java.util.Objects::nonNull).toList();
            }
            @Override public void explode(double x, double y, double z, float power, boolean fire, boolean breakBlocks) {
                check();
                content.afterCommit(level,() -> level.explode(null, x, y, z, power, fire, breakBlocks ? net.minecraft.world.level.Level.ExplosionInteraction.BLOCK : net.minecraft.world.level.Level.ExplosionInteraction.NONE));
            }
            @Override public Object existingEntity(java.util.UUID id) {
                check();var entity=level.getEntity(id);
                if(entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    try {
                        Object actor=player.containerMenu instanceof LegacyDeviceMenu menu?menu.originalPlayer():content.itemUse().clientActor(content.services().worlds().bound(level),ModernContentBridge.placer(player),null);
                        return LegacyTilePlayers.prepare(content,player,dimensions.portalState(actor,player));
                    }
                    catch(Exception failure){throw new IllegalStateException("Cannot resolve original player for entity query",failure);}
                }
                return null;
            }
            @Override public void spawnProjectile(Projectile projectile) {
                check();
                var arrow = new net.minecraft.world.entity.projectile.Arrow(level, projectile.x(), projectile.y(), projectile.z());
                if (projectile.owner() != null) {
                    var owner = level.getServer().getPlayerList().getPlayer(projectile.owner());
                    if (owner != null) arrow.setOwner(owner);
                }
                arrow.setDeltaMovement(projectile.motionX(), projectile.motionY(), projectile.motionZ());
                if (projectile.damage() > 0) arrow.setBaseDamage(projectile.damage());
                if (projectile.burning()) arrow.setSecondsOnFire(100);
                content.afterCommit(level, () -> level.addFreshEntity(arrow));
            }
            @Override public boolean spawnLegacyEntity(Object entity) {
                check();
                try { var hosted = LegacyHostedEntity.host(content, level, entity); content.afterCommit(level, () -> level.addFreshEntity(hosted)); return true; }
                catch (Exception failure) { throw new IllegalStateException("Cannot host original entity " + entity.getClass().getName(), failure); }
            }
            @Override public Object hostedLegacyEntity(java.util.UUID id) {
                check(); return level.getEntity(id) instanceof LegacyHostedEntity hosted ? hosted.legacyOrNull() : null;
            }
            @Override public boolean damage(Damage damage) {
                check(); var target = level.getEntity(damage.target());
                if (target == null || !target.isAlive()) return false;
                var attacker = damage.attacker() == null ? null : level.getEntity(damage.attacker());
                var direct = damage.direct() == null ? attacker : level.getEntity(damage.direct());
                var source = damageSource(level, damage, direct == null ? attacker : direct, attacker);
                if (target.isInvulnerableTo(source)) return false;
                content.afterCommit(level, () -> target.hurt(source, damage.amount()));
                return true;
            }
            @Override public void ignite(java.util.UUID id, int seconds) {
                check(); var target = level.getEntity(id); if (target != null) content.afterCommit(level, () -> target.setSecondsOnFire(seconds));
            }
            @Override public void addEffect(java.util.UUID id, int potion, int duration, int amplifier, boolean ambient) {
                check();
                if (!(level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity target)) return;
                // 1.7.10 potion ids 1-23 are the vanilla effects in the same order; mod potions have no host form.
                var effect = potion >= 1 && potion <= 23 ? net.minecraft.world.effect.MobEffect.byId(potion) : null;
                if (effect == null) { github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.missingMapping("potion " + potion, "original potion has no host effect"); return; }
                content.afterCommit(level, () -> target.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, duration, amplifier, ambient, true)));
            }
            @Override public long dayTime() { check(); return level.getDayTime(); }
            @Override public void setDayTime(long time) { check(); level.setDayTime(time); }
            @Override public boolean powered(int x, int y, int z) { check(); return level.hasNeighborSignal(new BlockPos(x,y,z)); }
            @Override public int power(int x, int y, int z, int side) { check(); return level.getSignal(new BlockPos(x,y,z),net.minecraft.core.Direction.from3DDataValue(side)); }
            @Override public boolean loaded(int x, int y, int z) { check(); return level.isInWorldBounds(new BlockPos(x,y,z)) && level.hasChunkAt(new BlockPos(x,y,z)); }
            @Override public void updateComparators(int x, int y, int z) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                content.afterCommit(level,() -> level.updateNeighbourForOutputSignal(pos,block));
            }
            @Override public void blockEvent(int x, int y, int z, String key, int event, int data) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                if (!content.legacyBlock(level.getBlockState(pos)).legacyKey().equals(key)) return;
                content.afterCommit(level,() -> level.blockEvent(pos,block,event,data));
            }
            @Override public void schedule(int x, int y, int z, String key, int delay) {
                check(); var pos = new BlockPos(x,y,z);
                if (content.legacyBlock(level.getBlockState(pos)).legacyKey().equals(key)) {
                    var state = level.getBlockState(pos); var block = state.getBlock();
                    if (!state.hasProperty(ModernContentBridge.METADATA) && state.isRandomlyTicking()) {
                        long due = level.getGameTime() + Math.max(1, delay);
                        content.afterCommit(level,() -> hostTicks.computeIfAbsent(level, ignored -> new HashMap<>()).putIfAbsent(pos.immutable(), due));
                        return;
                    }
                    content.afterCommit(level,() -> level.scheduleTick(pos,block,Math.max(1,delay)));
                }
            }
            @Override public Block block(int x, int y, int z) {
                check(); var state = level.getBlockState(new BlockPos(x, y, z));
                // Post-1.7 neighbours (deepslate, copper, mangrove...) must not abort and suspend original tile ticks.
                var known = content.hostRepresentations().knownOpaque(state);
                if (known != null) return known;
                try { return content.legacyBlock(state); }
                catch (github.com.gengyoubo.mfcl.bridge.UnmappedLegacyBlockException absent) { return content.hostRepresentations().opaque(state); }
            }
            @Override public boolean setBlock(int x, int y, int z, Block block, int flags) {
                check(); var pos = new BlockPos(x,y,z); content.beforeMutation(level,pos);
                net.minecraft.world.level.block.state.BlockState state;
                try { state = content.modernBlock(block); }
                catch (UnsupportedOperationException unsupported) { if (dimensions.skipUnsupported(block, unsupported)) return false; throw unsupported; }
                return level.setBlock(pos, state, flags);
            }
            @Override public boolean canPlaceBlock(Block block, int x, int y, int z) {
                check(); var candidate = content.blocks().get(block.legacyKey());
                var state = candidate == null ? content.modernBlock(block) : candidate.defaultBlockState();
                return level.isInWorldBounds(new BlockPos(x,y,z)) && level.isUnobstructed(state,new BlockPos(x,y,z),net.minecraft.world.phys.shapes.CollisionContext.empty());
            }
            @Override public HostWorldAccess.Hit rayTrace(double sx, double sy, double sz, double ex, double ey, double ez) {
                check();
                var from = new net.minecraft.world.phys.Vec3(sx, sy, sz); var to = new net.minecraft.world.phys.Vec3(ex, ey, ez);
                var result = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
                if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
                var pos = result.getBlockPos();
                return new HostWorldAccess.Hit(pos.getX(), pos.getY(), pos.getZ(), result.getDirection().get3DDataValue(), result.getLocation().x, result.getLocation().y, result.getLocation().z);
            }
            @Override public boolean originalBlockRayTrace() { check(); return true; }
            @Override public Object tileEntity(int x, int y, int z) {
                check(); content.beforeMutation(level,new BlockPos(x,y,z)); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (tile == null) return null;
                if (tile instanceof LegacyBlockEntity legacy) return legacy.legacy();
                return null; // An unsupported native tile is not an instance of any legacy machine/interface.
            }
            @Override public void setTileEntity(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (!(tile instanceof LegacyBlockEntity legacy)) throw new UnsupportedOperationException("No legacy tile envelope at requested position");
                legacy.replace(old); legacy.synchronize();
            }
            @Override public void removeTileEntity(int x, int y, int z) { check(); level.removeBlockEntity(new BlockPos(x, y, z)); }
            @Override public void markTileDirty(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (!(tile instanceof LegacyBlockEntity legacy) || !legacy.owns(old)) throw new IllegalStateException("Legacy tile identity mismatch");
                legacy.markLegacyDirty();
            }
            @Override public int light(int x, int y, int z) { check(); return level.getMaxLocalRawBrightness(new BlockPos(x,y,z)); }
            @Override public int height(int x, int z) { check(); return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z); }
            @Override public boolean canSeeSky(int x, int y, int z) { check(); return level.canSeeSky(new BlockPos(x,y,z)); }
            @Override public void neighborsChanged(int x, int y, int z) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                content.afterCommit(level,() -> level.updateNeighborsAt(pos,block));
            }
            @Override public long seed() { return level.getSeed(); }
            @Override public java.io.File legacySaveDirectory() { return LegacyDimensionHost.saveDirectory(level); }
            // Queued only when the original tick commits; the move itself happens after the host tick.
            @Override public void transferPlayer(Transfer transfer) { check(); content.afterCommit(level, () -> dimensions.queue(transfer)); }
            @Override public void portalCooldown(java.util.UUID id, int ticks) {
                check(); var player = level.getServer().getPlayerList().getPlayer(id); if (player != null) content.afterCommit(level, () -> player.setPortalCooldown(ticks));
            }
            @Override public int portalCooldown(java.util.UUID id) {
                check(); var player = level.getServer().getPlayerList().getPlayer(id); return player == null ? 0 : player.getPortalCooldown();
            }
            @Override public int[] findBlock(String legacyKey, int x, int y, int z, int radius) { check(); return dimensions.findBlock(level, legacyKey, x, y, z, radius); }
            @Override public void blockUpdated(int x, int y, int z) {
                check(); BlockPos pos = new BlockPos(x, y, z); var state = level.getBlockState(pos);
                if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.markLegacyDirty();
                else level.sendBlockUpdated(pos, state, state, 3);
            }
        };
    }
}

