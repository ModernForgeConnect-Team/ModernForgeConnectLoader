package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Original right-click / held-use callbacks. The old player is cached per UUID so research, item-in-use and
 * other per-player state survive across the begin/tick/stop sequence the modern engine drives.
 */
public final class LegacyItemUse {
    public record Result(boolean used, int useDuration, String prompt, List<LegacyPackets.Message> messages) {
        public Result(boolean used,int useDuration,String prompt) { this(used,useDuration,prompt,List.of()); }
    }
    /** A finished legacy scan, ready to be replayed against the authoritative player. */
    public record Scan(byte type, int id, int meta, String phenomena) {}
    public record AppliedScan(boolean accepted,byte[] data,List<LegacyPackets.Message> messages) {}
    private final LegacyServices services;
    // The same UUID exists on both the integrated server and the client; keep their actors apart.
    private final Map<String, Object> actors = new HashMap<>();
    public LegacyItemUse(LegacyServices services) { this.services = services; }
    public Result rightClick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            try(var knowledge=github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftKnowledge.server(loader,world)) {
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            Object returned; List<LegacyPackets.Message> messages;
            try(var packets=LegacyPackets.capture(placer.id())) {
                returned = itemType.getMethod("func_77659_a", stackType, worldType, playerType).invoke(item, stack, world, player);
                messages=packets.messages();
            }
            int duration = (int) itemType.getMethod("func_77626_a", stackType).invoke(item, stack);
            boolean used = duration > 0 || !messages.isEmpty() || returned==null || !services.items().snapshot(returned).equals(snapshot);
            String prompt = scanPrompt(item, loader);
            boolean remote = loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world);
            LogUtils.getLogger().info("MFCL legacy use {}@{}: remote={}, duration={}, target={}", snapshot.key(), snapshot.metadata(), remote, duration, prompt);
            return new Result(used, duration, prompt, messages);
            }
        });
    }
    /**
     * A held-use item that records a target while scanning (Thaumcraft's thaumometer stores it in the package-private
     * {@code startScan}) exposes no modern overlay yet, so surface the target as text instead of leaving the click silent.
     */
    private static String scanPrompt(Object item, ClassLoader loader) {
        try {
            Object scan = scanField(item);
            if (scan == null) return null;
            byte type = scan.getClass().getField("type").getByte(scan);
            if (type == 2) { Object entity = scan.getClass().getField("entity").get(scan); return entity == null ? null : (String) entity.getClass().getMethod("func_70005_c_").invoke(entity); }
            if (type == 3) return (String) scan.getClass().getField("phenomena").get(scan);
            int id = scan.getClass().getField("id").getInt(scan), meta = scan.getClass().getField("meta").getInt(scan);
            Object scanned = loader.loadClass("net.minecraft.item.Item").getMethod("func_150899_d", int.class).invoke(null, id);
            if (scanned == null) return null;
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Object stack = stackType.getConstructor(loader.loadClass("net.minecraft.item.Item"), int.class, int.class).newInstance(scanned, 1, meta);
            String key = (String) scanned.getClass().getMethod("func_77667_c", stackType).invoke(scanned, stack);
            Method translate = loader.loadClass("net.minecraft.util.StatCollector").getMethod("func_74838_a", String.class);
            translate.setAccessible(true);
            String localized = (String) translate.invoke(null, key + ".name");
            return localized.equals(key + ".name") ? (String) translate.invoke(null, key) : localized;
        } catch (Throwable absent) { return null; }
    }
    private static Object scanField(Object item) throws Exception {
        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField("startScan"); field.setAccessible(true); return field.get(item); }
            catch (NoSuchFieldException absent) { }
        }
        return null;
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
            Object request;
            try(var packets=LegacyPackets.captureScan(placer.id())) {
                itemType.getMethod("onUsingTick", stackType, playerType, int.class).invoke(item, stack, player, remaining);
                request=packets.scanRequest();
            }
            if (remaining <= 6) LogUtils.getLogger().info("MFCL legacy use tick {}@{}: remaining={}, target={}",
                    snapshot.key(), snapshot.metadata(), remaining, scanPrompt(item, loader));
            // Only the original successful completion branch emits this packet; clearing startScan also happens on cancellation.
            if(request!=null) {
                return new Scan((byte)packetField(request,"type"),(int)packetField(request,"id"),(int)packetField(request,"md"),(String)packetField(request,"phenomena"));
            }
            return null;
        });
    }
    private static Object packetField(Object packet,String name) throws Exception {var field=packet.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(packet);}
    public Object clientActor(Object world,LegacyTiles.Placer placer,LegacyClientData.Stack snapshot) throws Exception {return actor(world,placer,snapshot);}
    /** Restores previously persisted old player data into the cached actor before any legacy logic reads it. */
    public void loadPersisted(Object world, LegacyTiles.Placer placer, byte[] data) throws Exception {
        services.items().inLegacy(() -> {try(var state=github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftKnowledge.server(services.loader(),world)) {
            Object player=actor(world,placer,null);if(data!=null&&data.length!=0)services.playerData().write(player,data);return null;
        }});
    }
    public byte[] savePersisted(Object world,LegacyTiles.Placer placer) throws Exception {
        return services.items().inLegacy(()->{try(var state=github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftKnowledge.server(services.loader(),world)){return services.playerData().read(actor(world,placer,null));}});
    }
    /**
     * Runs the original {@code ScanManager.completeScan} against the authoritative (server) old player. The
     * caller passes the player's previously persisted old entity data (or null) and gets the updated bytes back,
     * so research survives across sessions.
     */
    public AppliedScan applyScan(Object world, LegacyTiles.Placer placer, byte type, int id, int meta, String phenomena, String prefix, byte[] priorData) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            try(var knowledge=github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftKnowledge.server(loader,world);var packets=LegacyPackets.capture(placer.id())) {
            var held=new LegacyClientData.Stack("Thaumcraft:ItemThaumometer",0,1,"","");
            Object player = actor(world, placer, held);
            if (priorData != null && priorData.length != 0) services.playerData().write(player, priorData);
            Object stack=services.items().materialize(held);Object item=loader.loadClass("net.minecraft.item.ItemStack").getMethod("func_77973_b").invoke(stack);
            var target=item.getClass().getDeclaredMethod("doScan",loader.loadClass("net.minecraft.item.ItemStack"),loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class);target.setAccessible(true);
            Object scan=target.invoke(item,stack,world,player,0);
            boolean accepted=false;
            if(scan!=null&&scan.getClass().getField("type").getByte(scan)==type&&scan.getClass().getField("id").getInt(scan)==id&&scan.getClass().getField("meta").getInt(scan)==meta&&Objects.equals(scan.getClass().getField("phenomena").get(scan),phenomena))
                accepted=(boolean)loader.loadClass("thaumcraft.common.lib.research.ScanManager")
                    .getMethod("completeScan", loader.loadClass("net.minecraft.entity.player.EntityPlayer"), loader.loadClass("thaumcraft.api.research.ScanResult"), String.class)
                    .invoke(null, player, scan, "@");
            syncPlayer(player);
            return new AppliedScan(accepted,services.playerData().read(player),packets.messages());
            }
        });
    }
    public List<LegacyPackets.Message> sync(Object world,LegacyTiles.Placer placer) throws Exception {
        return services.items().inLegacy(()->{try(var state=github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftKnowledge.server(services.loader(),world);var packets=LegacyPackets.capture(placer.id())) {
            syncPlayer(actor(world,placer,null));return packets.messages();
        }});
    }
    private void syncPlayer(Object player) throws Exception {
        var loader=services.loader();if(loader.getResource("thaumcraft/common/lib/network/PacketHandler.class")==null)return;
        Object wrapper=loader.loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null);
        var send=wrapper.getClass().getMethod("sendTo",loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage"),loader.loadClass("net.minecraft.entity.player.EntityPlayerMP"));
        for(String name:new String[]{"PacketSyncResearch","PacketSyncAspects","PacketSyncScannedItems","PacketSyncScannedEntities","PacketSyncScannedPhenomena"})
            send.invoke(wrapper,loader.loadClass("thaumcraft.common.lib.network.playerdata."+name).getConstructor(loader.loadClass("net.minecraft.entity.player.EntityPlayer")).newInstance(player),player);
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
            github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftKnowledge.initialize(loader,actor);
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
        if (inventory != null && snapshot != null)
            inventory.getClass().getMethod("func_70299_a", int.class, services.loader().loadClass("net.minecraft.item.ItemStack"))
                    .invoke(inventory, 0, services.items().materialize(snapshot));
    }
}
