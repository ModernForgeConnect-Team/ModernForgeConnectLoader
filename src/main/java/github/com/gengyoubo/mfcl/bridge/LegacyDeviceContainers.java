package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Captures original FML/vanilla container requests without entering the old network stack. */
public final class LegacyDeviceContainers {
    public record Descriptor(String mod,int id,int x,int y,int z,boolean inventory) {}
    public record Open(Descriptor descriptor,Object container,Object player) {}
    private static final ThreadLocal<Capture> CURRENT=new ThreadLocal<>();
    @FunctionalInterface public interface DropSink {void accept(Object entity) throws Exception;}
    private static final ThreadLocal<DropSink> DROPS=new ThreadLocal<>();
    public static <T> T withDrops(DropSink sink,java.util.concurrent.Callable<T> action) throws Exception {
        DropSink previous=DROPS.get();DROPS.set(sink);
        try{return action.call();}finally{if(previous==null)DROPS.remove();else DROPS.set(previous);}
    }
    public static boolean drop(Object entity) throws Exception {var sink=DROPS.get();if(sink==null)return false;sink.accept(entity);return true;}
    public static final class Capture implements AutoCloseable {
        final LegacyServices services;Open opened;
        private Capture(LegacyServices services){this.services=services;}
        public Open opened(){return opened;}
        @Override public void close(){CURRENT.remove();}
    }
    private LegacyDeviceContainers() {}
    public static Capture capture(LegacyServices services) {
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested device activation");
        var capture=new Capture(services);CURRENT.set(capture);return capture;
    }
    private static Object registry(ClassLoader loader) throws Exception {
        return loader.loadClass("cpw.mods.fml.common.network.NetworkRegistry").getField("INSTANCE").get(null);
    }
    public static boolean openGui(Object player,Object mod,int id,Object world,int x,int y,int z) throws Exception {
        var capture=CURRENT.get();if(capture==null)return false;
        var loader=capture.services.loader();
        if(loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world))return true;
        Object common=loader.loadClass("cpw.mods.fml.common.FMLCommonHandler").getMethod("instance").invoke(null);
        Object owner=common.getClass().getMethod("findContainerFor",Object.class).invoke(common,mod);
        if(owner==null)throw new IllegalArgumentException("No original mod owns the GUI request");
        var ownerType=loader.loadClass("cpw.mods.fml.common.ModContainer");
        Object container=registry(loader).getClass().getMethod("getRemoteGuiContainer",ownerType,loader.loadClass("net.minecraft.entity.player.EntityPlayerMP"),int.class,loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class)
                .invoke(registry(loader),owner,player,id,world,x,y,z);
        if(container!=null)capture.opened=new Open(new Descriptor((String)ownerType.getMethod("getModId").invoke(owner),id,x,y,z,false),container,player);
        return true;
    }
    public static boolean openInventory(Object player,Object inventory) throws Exception {
        var capture=CURRENT.get();if(capture==null)return false;
        var loader=capture.services.loader();var inv=loader.loadClass("net.minecraft.inventory.IInventory");
        Object playerInventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        Object container=loader.loadClass("net.minecraft.inventory.ContainerChest").getConstructor(inv,inv).newInstance(playerInventory,inventory);
        var tile=loader.loadClass("net.minecraft.tileentity.TileEntity");
        if(!tile.isInstance(inventory))throw new UnsupportedOperationException("Inventory GUI needs a positioned original tile");
        capture.opened=new Open(new Descriptor("",0,tile.getField("field_145851_c").getInt(inventory),tile.getField("field_145848_d").getInt(inventory),tile.getField("field_145849_e").getInt(inventory),true),container,player);
        return true;
    }
    public static Object clientGui(LegacyServices services,Descriptor descriptor,Object player,Object world) throws Exception {
        var loader=services.loader();
        if(descriptor.inventory()) {
            Object inventory=loader.loadClass("net.minecraft.world.World").getMethod("func_147438_o",int.class,int.class,int.class).invoke(world,descriptor.x(),descriptor.y(),descriptor.z());
            Object playerInventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
            var inv=loader.loadClass("net.minecraft.inventory.IInventory");
            return loader.loadClass("net.minecraft.client.gui.inventory.GuiChest").getConstructor(inv,inv).newInstance(playerInventory,inventory);
        }
        Object fml=loader.loadClass("cpw.mods.fml.common.Loader").getMethod("instance").invoke(null);
        Object owner=((Map<?,?>)fml.getClass().getMethod("getIndexedModList").invoke(fml)).get(descriptor.mod());
        return registry(loader).getClass().getMethod("getLocalGuiContainer",loader.loadClass("cpw.mods.fml.common.ModContainer"),loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class)
                .invoke(registry(loader),owner,player,descriptor.id(),world,descriptor.x(),descriptor.y(),descriptor.z());
    }
    public static List<?> slots(Object container) throws Exception {return (List<?>)container.getClass().getField("field_75151_b").get(container);}
}
