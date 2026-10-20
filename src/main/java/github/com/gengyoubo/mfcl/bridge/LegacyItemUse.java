package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Original right-click / held-use callbacks. The old player is cached per UUID so research, item-in-use and
 * other per-player state survive across the begin/tick/stop sequence the modern engine drives.
 */
public final class LegacyItemUse {
    public record Result(boolean used, int useDuration, String prompt, List<LegacyPackets.Message> messages,List<String> feedback) {
        public Result(boolean used,int useDuration,String prompt) { this(used,useDuration,prompt,List.of(),List.of()); }
        public Result(boolean used,int useDuration,String prompt,List<LegacyPackets.Message> messages) {this(used,useDuration,prompt,messages,List.of());}
    }
    /** A finished legacy scan, ready to be replayed against the authoritative player. */
    public record Scan(byte type, int id, int meta, int entityId, String phenomena) {}
    public record AppliedScan(boolean accepted,byte[] data,List<LegacyPackets.Message> messages) {}
    private final LegacyServices services;
    // The same UUID exists on both the integrated server and the client; keep their actors apart.
    private final Map<String, Object> actors = new HashMap<>();
    public LegacyItemUse(LegacyServices services) { this.services = services; }
    public Result rightClick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            try(var knowledge=services.playerState().enter(world)) {
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            Object inventory=playerType.getField("field_71071_by").get(player);
            int selected=inventory.getClass().getField("field_70461_c").getInt(inventory);
            // The callback and inventory must share the same stack, just as in the original game.
            inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,selected,stack);
            Object returned; List<LegacyPackets.Message> messages;List<String> feedback;
            try(var packets=LegacyPackets.capture(placer.id());var chat=new LegacyBlockLogic.ChatCapture()) {
                returned = itemType.getMethod("func_77659_a", stackType, worldType, playerType).invoke(item, stack, world, player);
                messages=packets.messages();feedback=chat.messages();
            }
            if(returned!=null&&stackType.getField("field_77994_a").getInt(returned)<=0)returned=null;
            inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,selected,returned);
            int duration = (int) itemType.getMethod("func_77626_a", stackType).invoke(item, stack);
            var after=returned==null?null:services.items().snapshot(returned);
            boolean used = duration > 0 || !messages.isEmpty() || !feedback.isEmpty() || after==null || !after.key().equals(snapshot.key())||after.metadata()!=snapshot.metadata()||after.count()!=snapshot.count()||!after.tag().equals(snapshot.tag());
            String prompt = prompt(snapshot.key(), item);
            boolean remote = loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world);
            LogUtils.getLogger().info("MFCL legacy use {}@{}: remote={}, duration={}, target={}", snapshot.key(), snapshot.metadata(), remote, duration, prompt);
            return new Result(used, duration, prompt, messages,feedback);
            }
        });
    }
    /** A held scanner's current target, surfaced as text because no modern scanning overlay exists yet. */
    private String prompt(String key, Object item) {
        try {
            var scanner = services.scanners().forItem(key);
            return scanner.isPresent() ? scanner.get().prompt(item) : null;
        } catch (Throwable absent) { return null; }
    }
    /**
     * Names a scan target exactly as the original thaumometer lens does: a dropped item shows its stack's display
     * name rather than EntityItem's "item.item.&lt;name&gt;" entity name, which has no translation in 1.7.10.
     */
    public static String scanTargetName(ClassLoader loader, Object entity, int id, int meta) throws Exception {
        Class<?> itemType = loader.loadClass("net.minecraft.item.Item"), stackType = loader.loadClass("net.minecraft.item.ItemStack");
        Object stack = null;
        if (entity != null) {
            if (!loader.loadClass("net.minecraft.entity.item.EntityItem").isInstance(entity)) return (String) entity.getClass().getMethod("func_70005_c_").invoke(entity);
            stack = entity.getClass().getMethod("func_92059_d").invoke(entity);
        } else {
            Object scanned = itemType.getMethod("func_150899_d", int.class).invoke(null, id);
            if (scanned != null) stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(scanned, 1, meta);
        }
        if (stack == null || stackType.getMethod("func_77973_b").invoke(stack) == null) return null;
        try { return (String) stackType.getMethod("func_82833_r").invoke(stack); }
        catch (InvocationTargetException failure) {
            return (String) itemType.getMethod("func_77653_i", stackType).invoke(stackType.getMethod("func_77973_b").invoke(stack), stack);
        }
    }
    public Scan usingTick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            var scanner = services.scanners().forItem(snapshot.key()).orElse(null);
            Object request;
            try(var packets=LegacyPackets.captureScan(placer.id(),scanner==null?packet->false:scanner::completion)) {
                itemType.getMethod("onUsingTick", stackType, playerType, int.class).invoke(item, stack, player, remaining);
                request=packets.scanRequest();
            }
            if (remaining <= 6) LogUtils.getLogger().info("MFCL legacy use tick {}@{}: remaining={}, target={}",
                    snapshot.key(), snapshot.metadata(), remaining, prompt(snapshot.key(), item));
            if (remaining <= 5) try { // TEMP-DIAG
                LogUtils.getLogger().info("MFCL TEMP-DIAG request={} notices={}", request,
                        loader.loadClass("thaumcraft.client.lib.PlayerNotifications").getField("notificationList").get(null));
            } catch (Throwable ignored) { }
            // Only the original successful completion branch emits this packet; clearing startScan also happens on cancellation.
            return request==null?null:scanner.read(request);
        });
    }
    public Object clientActor(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack snapshot) throws Exception {return actor(world,placer,snapshot);}
    /** Restores previously persisted old player data into the cached actor before any legacy logic reads it. */
    public void loadPersisted(Object world, LegacyTiles.Placer placer, byte[] data) throws Exception {
        services.items().inLegacy(() -> {try(var state=services.playerState().enter(world)) {
            Object player=actor(world,placer,null);if(data!=null&&data.length!=0)services.playerData().write(player,data);return null;
        }});
    }
    public byte[] savePersisted(Object world,LegacyTiles.Placer placer) throws Exception {
        return services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){return services.playerData().read(actor(world,placer,null));}});
    }
    /**
     * Lets the held item's scanner adapter re-evaluate a client-reported scan against the authoritative (server) old
     * player. The caller passes the player's previously persisted old entity data (or null) and gets the updated bytes
     * back, so research survives across sessions.
     */
    public AppliedScan applyScan(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack held, Scan scan, String prefix, byte[] priorData) throws Exception {
        var scanner = services.scanners().forItem(held.key()).orElseThrow(() -> new IllegalArgumentException("Not a held scanner: " + held.key()));
        return services.items().inLegacy(() -> {
            try(var knowledge=services.playerState().enter(world);var packets=LegacyPackets.capture(placer.id())) {
            Object player = actor(world, placer, held);
            if (priorData != null && priorData.length != 0) services.playerData().write(player, priorData);
            boolean accepted=scanner.complete(world,player,services.items().materialize(held),scan,prefix);
            services.playerState().synchronize(player);
            return new AppliedScan(accepted,services.playerData().read(player),packets.messages());
            }
        });
    }
    public List<LegacyPackets.Message> sync(Object world,LegacyTiles.Placer placer) throws Exception {
        return services.items().inLegacy(()->{try(var state=services.playerState().enter(world);var packets=LegacyPackets.capture(placer.id())) {
            services.playerState().synchronize(actor(world,placer,null));return packets.messages();
        }});
    }
    public void release(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            itemType.getMethod("func_77615_a", stackType, worldType, playerType, int.class).invoke(item, stack, world, player, remaining);
            return null;
        });
    }
    public int useDuration(LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Object stack = services.items().materialize(snapshot);
            return (int) itemType.getMethod("func_77626_a", stackType).invoke(stackType.getMethod("func_77973_b").invoke(stack), stack);
        });
    }
    private Object actor(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        var loader = services.loader();
        // Thaumcraft's held-use tick compares player names with `==`, so on the client the actor must be the very
        // old client player that Minecraft.getMinecraft().thePlayer exposes, not an equal-but-distinct instance.
        if (loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world)) {
            services.containers().preparePlayer();
            Class<?> mc = loader.loadClass("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("func_71410_x").invoke(null);
            Object client = mc.getField("field_71439_g").get(instance);
            if (client != null) { refresh(client, world, placer, snapshot);mc.getField("field_71451_h").set(instance,client);return client; }
        }
        String key = System.identityHashCode(world) + "|" + placer.id();
        Object actor = actors.get(key);
        if (actor == null) {
            actor = services.tiles().actor(world, placer, snapshot);
            services.playerState().initialize(actor);
            actors.put(key, actor);
        }
        else refresh(actor, world, placer, snapshot);
        return actor;
    }
    private void refresh(Object actor, Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        var playerType=services.loader().loadClass("net.minecraft.entity.player.EntityPlayer");
        var profile=playerType.getDeclaredField("field_146106_i");profile.setAccessible(true);
        profile.set(actor,services.loader().loadClass("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class).newInstance(placer.id(),placer.name()));
        Class<?> entity = services.loader().loadClass("net.minecraft.entity.Entity");
        // Entity queries exclude the actor by UUID; the reused client player must carry the host player's identity,
        // otherwise its own host entity is returned as the pointed target.
        var uuid=entity.getDeclaredField("field_96093_i");uuid.setAccessible(true);uuid.set(actor,placer.id());
        boolean client=services.loader().loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world);
        playerType.getField("eyeHeight").setFloat(actor,client?.12f:1.62f);
        entity.getField("field_70129_M").setFloat(actor,client?1.62f:0);
        entity.getField("field_70170_p").set(actor, world);
        double oldY=placer.y()+(client?1.62:0);
        entity.getField("field_70165_t").setDouble(actor, placer.x()); entity.getField("field_70163_u").setDouble(actor, oldY); entity.getField("field_70161_v").setDouble(actor, placer.z());
        for(String field:new String[]{"field_70169_q","field_70142_S"})entity.getField(field).setDouble(actor,placer.x());
        for(String field:new String[]{"field_70167_r","field_70137_T"})entity.getField(field).setDouble(actor,oldY);
        for(String field:new String[]{"field_70166_s","field_70136_U"})entity.getField(field).setDouble(actor,placer.z());
        entity.getField("field_70177_z").setFloat(actor, placer.yaw()); entity.getField("field_70125_A").setFloat(actor, placer.pitch());
        entity.getField("field_70126_B").setFloat(actor,placer.yaw());entity.getField("field_70127_C").setFloat(actor,placer.pitch());
        Class<?> sp=services.loader().loadClass("net.minecraft.client.entity.EntityPlayerSP");
        if(sp.isInstance(actor)) {
            for(String name:new String[]{"field_71155_g","field_71164_i"})sp.getField(name).setFloat(actor,placer.pitch());
            for(String name:new String[]{"field_71154_f","field_71163_h"})sp.getField(name).setFloat(actor,placer.yaw());
        }
        // Range/ray-trace helpers read the bounding box; the reused host player may never have had one built.
        Object box = services.loader().loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a", double.class, double.class, double.class, double.class, double.class, double.class)
                .invoke(null, placer.x() - 0.3, placer.y(), placer.z() - 0.3, placer.x() + 0.3, placer.y() + 1.8, placer.z() + 0.3);
        Field bbox = entity.getDeclaredField("field_70121_D"); bbox.setAccessible(true); bbox.set(actor, box);
        entity.getField("field_70130_N").setFloat(actor, 0.6f); entity.getField("field_70131_O").setFloat(actor, 1.8f);
        var inventory = services.loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(actor);
        if (inventory != null && snapshot != null) {
            int selected = inventory.getClass().getField("field_70461_c").getInt(inventory);
            inventory.getClass().getMethod("func_70299_a", int.class, services.loader().loadClass("net.minecraft.item.ItemStack"))
                    .invoke(inventory, selected, services.items().materialize(snapshot));
        }
    }
}
