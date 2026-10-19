package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Real old container/network boundaries and entity queries without reference mods. */
final class RuntimeDeviceSemanticsProbe {
    static void verify(LegacyServices services,Map<String,Object> report) throws Exception {
        var loader=services.loader();var motions=new ArrayList<HostWorldAccess.Motion>();Object identity=new Object();Object[] chest={null};
        UUID id=new UUID(17,29);
        Object world=services.worlds().bind(identity,new HostWorldAccess() {
            public String dimensionKey(){return "mfcl:device";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
            public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
            public Block block(int x,int y,int z){return new Block("minecraft:chest",0);}public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
            public Object tileEntity(int x,int y,int z){return chest[0];}public void blockEvent(int x,int y,int z,String block,int event,int data){}
            public List<Entity> entities(double a,double b,double c,double d,double e,double f){return List.of(new Entity(id,"net.minecraft.entity.EntityLiving",1,2,3,0,-.1,0,5,.6f,1.8f,false,true));}
            public void applyMotion(Motion motion){motions.add(motion);}
        });
        try {
            Object actor=services.tiles().actor(world,new LegacyTiles.Placer(new UUID(1,2),"Device",0,0,0,0,0),null);
            chest[0]=loader.loadClass("net.minecraft.tileentity.TileEntityChest").getConstructor().newInstance();
            services.tiles().attach(chest[0],"minecraft:chest",0,world,0,0,0);
            try(var capture=LegacyDeviceContainers.capture(services)) {
                actor.getClass().getMethod("func_71007_a",loader.loadClass("net.minecraft.inventory.IInventory")).invoke(actor,chest[0]);
                var request=capture.opened();
                if(request==null||!request.descriptor().inventory()||LegacyDeviceContainers.slots(request.container()).size()!=63)throw new AssertionError("Vanilla inventory open entered the old network or lost its slots");
                request.container().getClass().getMethod("func_75134_a",loader.loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(request.container(),actor);
            }
            var drops=new ArrayList<LegacyClientData.Stack>();
            LegacyDeviceContainers.withDrops(entity->drops.add(services.items().snapshot(entity.getClass().getMethod("func_92059_d").invoke(entity))),()->{
                actor.getClass().getMethod("func_71019_a",loader.loadClass("net.minecraft.item.ItemStack"),boolean.class).invoke(actor,services.items().materialize(new LegacyClientData.Stack("minecraft:diamond",0,2,"","")),true);return null;
            });
            if(drops.size()!=1||drops.get(0).count()!=2||!drops.get(0).key().equals("minecraft:diamond"))throw new AssertionError("Original container drop could not leave the legacy network domain");
            Object box=loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a",double.class,double.class,double.class,double.class,double.class,double.class).invoke(null,0d,0d,0d,4d,4d,4d);
            var entity=loader.loadClass("net.minecraft.entity.Entity");var worldType=loader.loadClass("net.minecraft.world.World");
            LegacyWorlds.beginEntityQueries(world);
            List<?> query=(List<?>)worldType.getMethod("func_72872_a",Class.class,box.getClass()).invoke(world,loader.loadClass("net.minecraft.entity.EntityLivingBase"),box);
            if(query.size()!=1||!Boolean.TRUE.equals(entity.getMethod("func_70104_M").invoke(query.get(0))))throw new AssertionError("Entity query did not preserve living/pushable types");
            entity.getField("field_70181_x").setDouble(query.get(0),.4);entity.getField("field_70143_R").setFloat(query.get(0),0);
            LegacyWorlds.commitEntityMotion(world);
            if(!motions.equals(List.of(new HostWorldAccess.Motion(id,0,.4,0,0))))throw new AssertionError("Original entity motion was not committed to host");
            if(!LegacyWorlds.entities(world,loader.loadClass("net.minecraft.entity.item.EntityItem"),box).isEmpty())throw new AssertionError("Class-filtered entity query returned an unrelated entity");
            LegacyWorlds.clearEntityQueries(world);
            try(var frame=LegacyGuiGraphics.begin(320,240,0,0,Set.of())) {
                var layout=List.of(new LegacyContainers.Slot(8,18,null),new LegacyContainers.Slot(26,18,null));
                Object gui=services.containers().create(320,240,0,0,176,166,layout);
                if(!services.containers().sameLayout(gui,layout)||services.containers().sameLayout(gui,List.of(layout.get(0)))
                        ||services.containers().sameLayout(gui,List.of(layout.get(0),new LegacyContainers.Slot(44,18,null))))throw new AssertionError("Dynamic creative slot layout change was missed");
                try(var session=services.screens().open(gui,320,240);var input=new LegacyDeviceInput()) {
                    // Native slots must never enter the old PlayerController's absent network connection.
                    session.mousePressed(88,63,0);session.mouseReleased(88,63,0);
                    var buttons=loader.loadClass("net.minecraft.client.gui.GuiScreen").getDeclaredField("field_146292_n");buttons.setAccessible(true);
                    Object button=loader.loadClass("net.minecraft.client.gui.GuiButton").getConstructor(int.class,int.class,int.class,int.class,int.class,String.class).newInstance(7,40,40,20,20,"Go");
                    @SuppressWarnings("unchecked") var controls=(List<Object>)buttons.get(gui);controls.add(button);
                    session.mousePressed(45,45,0);session.mouseReleased(45,45,0);
                    if(!frame.hasClickSound()||!input.packets().isEmpty())throw new AssertionError("Native slot suppression also suppressed original GUI buttons");
                }
                if(LegacyDeviceInput.active())throw new AssertionError("Device input scope leaked into another screen");
                report.put("genericDeviceButtonInputVerified",true);
            }
            byte[] saved=services.tiles().save(chest[0]);
            if(services.tiles().snapshotInstance(chest[0],saved)!=chest[0])throw new AssertionError("Same-type synchronization replaced an open GUI tile");
            Object furnace=loader.loadClass("net.minecraft.tileentity.TileEntityFurnace").getConstructor().newInstance();
            Object converted=services.tiles().snapshotInstance(furnace,saved);
            Object reloaded=services.tiles().snapshotInstance(null,saved);
            if(converted==furnace||converted.getClass()!=chest[0].getClass()||reloaded.getClass()!=chest[0].getClass())throw new AssertionError("Saved tile registry ID did not control replacement/reload type");
            services.tiles().attach(reloaded,"minecraft:chest",0,world,0,0,0);services.tiles().load(reloaded,saved);
            report.put("genericTileSnapshotSubtypeVerified",true);
            report.put("genericDeviceContainersAndEntityQueriesVerified",true);
        }finally{services.worlds().unbind(identity);}
    }
}
