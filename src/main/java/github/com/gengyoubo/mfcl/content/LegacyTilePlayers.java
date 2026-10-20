package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Player data and addressed packets produced by original tile ticks, committed with their block transaction. */
final class LegacyTilePlayers implements AutoCloseable {
    private static final ThreadLocal<LegacyTilePlayers> CURRENT = new ThreadLocal<>();
    private record Actor(ServerPlayer host,Object legacy,byte[] before) {}
    private final ModernContentBridge content;
    private final Map<UUID,Actor> actors = new LinkedHashMap<>();
    private final Map<UUID,byte[]> changed = new LinkedHashMap<>();
    private boolean committed;
    private LegacyTilePlayers(ModernContentBridge content) {
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested original tile player transaction");
        this.content=content;CURRENT.set(this);
    }
    /** Called lazily when a tile's entity query first resolves a real player. */
    static Object prepare(ModernContentBridge content,ServerPlayer player,Object actor) throws Exception {
        var current=CURRENT.get();
        if(current==null||current.content!=content||current.actors.containsKey(player.getUUID()))return actor;
        var services=content.services();
        byte[] persisted=player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
        if(persisted.length!=0)services.playerData().write(actor,persisted);
        services.playerState().initialize(actor);
        current.actors.put(player.getUUID(),new Actor(player,actor,services.playerData().read(actor)));
        return actor;
    }
    static boolean tick(ModernContentBridge content,ServerLevel level,BlockPos pos,Object tile) throws Exception {
        return run(content,level,pos,()->content.services().logic().tick(tile));
    }
    static boolean run(ModernContentBridge content,ServerLevel level,BlockPos pos,java.util.concurrent.Callable<Boolean> action) throws Exception {
        var services=content.services();
        return services.items().inLegacy(()->{
            Object world=services.worlds().bound(level);
            var recipients=new HashSet<UUID>();for(var player:level.getServer().getPlayerList().getPlayers())recipients.add(player.getUUID());
            try(var state=services.playerState().enter(world);var players=new LegacyTilePlayers(content);var packets=LegacyPackets.captureFor(recipients)){
                boolean ticked=content.logicTransaction(level,pos,()->{
                    boolean result=action.call();
                    // Serialize before committing blocks, so serialization errors also roll back player knowledge.
                    for(var actor:players.actors.values()){
                        byte[] after=services.playerData().read(actor.legacy());
                        if(!Arrays.equals(actor.before(),after))players.changed.put(actor.host().getUUID(),after);
                    }
                    return result;
                },false);
                players.committed=true;
                players.changed.forEach((id,data)->players.actors.get(id).host().getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,data));
                for(var player:level.getServer().getPlayerList().getPlayers())
                    for(var packet:packets.messages(player.getUUID()))ScanSyncChannel.sendToPlayer(player,packet);
                return ticked;
            }
        });
    }
    @Override public void close() throws Exception {
        try {
            if(!committed)for(var actor:actors.values())content.services().playerData().write(actor.legacy(),actor.before());
        }finally{CURRENT.remove();}
    }
}
