package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import java.nio.file.*;
import java.util.*;

/** Real generator/client tick and original codec round trip, without a legacy connection or audio engine. */
public final class ExtraUtilitiesGeneratorProbe {
    private static final class World implements HostWorldAccess {
        final boolean client;Object tile;
        final List<LegacyPackets.Message> messages=new ArrayList<>();
        World(boolean client){this.client=client;}
        public String dimensionKey(){return "mfcl:generator";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return client;}
        public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
        public Block block(int x,int y,int z){return x==0&&y==64&&z==0?new Block("ExtraUtilities:generator",0):new Block("minecraft:air",0);}
        public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
        public Object tileEntity(int x,int y,int z){return x==0&&y==64&&z==0?tile:null;}
        public void markTileDirty(int x,int y,int z,Object tile){}
        public void blockUpdated(int x,int y,int z){}
        public void sendPacket(UUID recipient,LegacyPackets.Message packet){messages.add(packet);}
    }
    public static void main(String[] args) throws Exception {
        Path game=Path.of(args[0]);
        try(var runtime=new EmbeddedLegacyRuntime(game,Path.of(args[1]),new LegacyRuntimeService().inspect(game))) {
            var report=runtime.boot();if(!Boolean.TRUE.equals(report.get("legacyFmlStarted")))throw new AssertionError(report.get("stackTrace"));
            var services=runtime.services();var loader=services.loader();
            services.items().inLegacy(()->{
                Class.forName("com.rwtema.extrautils.item.filters.AdvancedNodeUpgrades",true,loader);
                var factory=loader.loadClass("com.rwtema.extrautils.asm.RemoteCallFactory");
                Object evaluator=factory.getMethod("getEvaluator",String.class,String.class,Class.class).invoke(null,"java.lang.Boolean","parseBoolean",String.class);
                if(evaluator==null||!(boolean)loader.loadClass("com.rwtema.extrautils.asm.RemoteCallFactory$IObjectEvaluate").getMethod("evaluate",Object.class).invoke(evaluator,"true"))
                    throw new AssertionError("Original dynamic caller did not define/evaluate a class in the private loader");
                var define=ClassLoader.class.getDeclaredMethod("defineClass",String.class,byte[].class,int.class,int.class);
                if(define.trySetAccessible())throw new AssertionError("java.lang was opened globally");
                return null;
            });
            var serverHost=new World(false);var clientHost=new World(true);
            Object serverWorld=services.worlds().bind(serverHost,serverHost),clientWorld=services.worlds().bind(clientHost,clientHost);
            var generator=loader.loadClass("com.rwtema.extrautils.tileentity.generators.TileEntityGenerator");
            serverHost.tile=services.tiles().create("ExtraUtilities:generator",0,serverWorld,0,64,0);
            clientHost.tile=services.tiles().create("ExtraUtilities:generator",0,clientWorld,0,64,0);
            UUID id=new UUID(21,42);var placer=new LegacyTiles.Placer(id,"Generator",0,64,0,0,0);
            Object serverPlayer=services.tiles().actor(serverWorld,placer,null),clientPlayer=services.tiles().actor(clientWorld,placer,null);
            Class<?> player=loader.loadClass("net.minecraft.entity.player.EntityPlayer"),mc=loader.loadClass("net.minecraft.client.Minecraft");
            Object client=mc.getMethod("func_71410_x").invoke(null);mc.getField("field_71439_g").set(client,clientPlayer);mc.getField("field_71441_e").set(client,clientWorld);
            var sounds=new ArrayList<Object>();var completions=new ArrayList<Runnable>();
            LegacyAudio.attach(loader,(sound,finished)->{
                if(Thread.currentThread().getContextClassLoader()==loader)throw new AssertionError("Host sound callback inherited private runtime context");
                sounds.add(sound);completions.add(finished);
            });
            for(int tick=0;tick<5;tick++)services.logic().tick(clientHost.tile);
            if(sounds.size()!=1)throw new AssertionError("Client tick did not register one hosted generator sound");
            loader.loadClass("com.rwtema.extrautils.sounds.Sounds").getMethod("tryAddSound",loader.loadClass("net.minecraft.client.audio.ISound")).invoke(null,sounds.get(0));
            if(sounds.size()!=1)throw new AssertionError("Generator sound was registered twice");
            completions.get(0).run();
            if(!(boolean)loader.loadClass("com.rwtema.extrautils.sounds.Sounds").getMethod("canAddSound",loader.loadClass("net.minecraft.client.audio.ISound")).invoke(null,sounds.get(0)))
                throw new AssertionError("Completed host sound remained in old sound bookkeeping");
            Object fuel=services.items().materialize(new LegacyClientData.Stack("minecraft:coal",0,2,"",""));
            serverHost.tile.getClass().getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(serverHost.tile,0,fuel);
            for(int tick=0;tick<10;tick++)services.logic().tick(serverHost.tile);
            Object storage=generator.getMethod("getStorage").invoke(serverHost.tile);
            int energy=(int)storage.getClass().getMethod("getEnergyStored").invoke(storage);
            if(energy<=0)throw new AssertionError("Original generator did not burn fuel/generate energy");
            var containerType=loader.loadClass("com.rwtema.extrautils.tileentity.generators.DynamicContainerGenerator");
            var constructor=containerType.getConstructor(loader.loadClass("net.minecraft.inventory.IInventory"),generator);
            Object serverContainer=constructor.newInstance(player.getField("field_71071_by").get(serverPlayer),serverHost.tile);
            Object clientContainer=constructor.newInstance(player.getField("field_71071_by").get(clientPlayer),clientHost.tile);
            var window=loader.loadClass("net.minecraft.inventory.Container").getField("field_75152_c");window.setInt(clientContainer,7);player.getField("field_71070_bA").set(clientPlayer,clientContainer);
            List<LegacyPackets.Message> initial;
            try(var outgoing=LegacyPackets.capture(id)) {
                LegacyDeviceContainers.attachViewer(new LegacyDeviceContainers.Open(new LegacyDeviceContainers.Descriptor("ExtraUtilities",0,0,64,0,false),serverContainer,serverPlayer),7);
                initial=outgoing.messages();
                if(!serverHost.messages.isEmpty()||initial.isEmpty())throw new AssertionError("Initial description bypassed packet capture");
            }
            for(var packet:initial)LegacyPackets.receiveClient(services,packet);
            assertEnergy(loader,clientContainer,energy);
            storage.getClass().getMethod("receiveEnergy",int.class,boolean.class).invoke(storage,123,false);
            serverContainer.getClass().getMethod("func_75142_b").invoke(serverContainer);
            if(serverHost.messages.isEmpty())throw new AssertionError("Live GUI update did not cross host transport");
            for(var packet:serverHost.messages)LegacyPackets.receiveClient(services,packet);
            assertEnergy(loader,clientContainer,energy+123);
            window.setInt(clientContainer,8);
            for(var packet:initial)LegacyPackets.receiveClient(services,packet);
            assertEnergy(loader,clientContainer,energy+123);
            ExtraUtilitiesEnergyProbe.verify(services);
            ExtraUtilitiesPortalProbe.verify(services);
            System.out.println("MFCL_XU_GENERATOR_PASS: client ticks, sound lifecycle, fuel generation, captured initial GUI data, live codec delivery, stale-window rejection, RF energy bridge");
        }
    }
    private static void assertEnergy(ClassLoader loader,Object container,int expected) throws Exception {
        for(Object widget:(List<?>)container.getClass().getField("widgets").get(container))if(widget.getClass().getName().endsWith(".WidgetEnergy")) {
            var field=widget.getClass().getDeclaredField("curEnergy");field.setAccessible(true);
            if(field.getInt(widget)!=expected)throw new AssertionError("Client energy widget: expected "+expected+", got "+field.getInt(widget));
            return;
        }
        throw new AssertionError("Original generator GUI has no energy widget");
    }
}
