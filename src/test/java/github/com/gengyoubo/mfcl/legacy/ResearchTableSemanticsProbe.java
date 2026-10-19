package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import java.nio.file.*;
import java.util.*;

/** Real TC4 container, serialized packets and completion algorithm in a disposable JVM/world. */
public final class ResearchTableSemanticsProbe {
    private final LegacyServices services;
    private final ClassLoader loader;
    private final Object world,player,tile,wrapper;
    private Object knowledge;
    private final LegacyDeviceContainers.Descriptor device=new LegacyDeviceContainers.Descriptor("Thaumcraft",10,0,0,0,false);
    private final Class<?> stackType,aspectType,manager;
    private ResearchTableSemanticsProbe(LegacyServices services) throws Exception {
        this.services=services;loader=services.loader();stackType=loader.loadClass("net.minecraft.item.ItemStack");
        aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");manager=loader.loadClass("thaumcraft.common.lib.research.ResearchManager");
        Object[] table={null};
        world=services.worlds().bind(this,new HostWorldAccess() {
            public String dimensionKey(){return "mfcl:research";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
            public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
            public Block block(int x,int y,int z){return new Block("Thaumcraft:blockTable",2);}
            public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
            public Object tileEntity(int x,int y,int z){return x==0&&y==0&&z==0?table[0]:null;}
            public void markTileDirty(int x,int y,int z,Object tile){}public void blockUpdated(int x,int y,int z){}
            public void blockEvent(int x,int y,int z,String block,int event,int data){}
        });
        tile=services.tiles().create("Thaumcraft:blockTable",2,world,0,0,0);table[0]=tile;
        player=services.tiles().actor(world,new LegacyTiles.Placer(new UUID(41,42),"Research",0,0,0,0,0),null);
        wrapper=loader.loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null);
    }
    private Object aspect(String tag) throws Exception {return aspectType.getMethod("getAspect",String.class).invoke(null,tag);}
    private Object stack(String key) throws Exception {return services.items().materialize(new LegacyClientData.Stack(key,0,1,"",""));}
    private Object note() throws Exception {return tile.getClass().getMethod("func_70301_a",int.class).invoke(tile,1);}
    private Object data() throws Exception {return manager.getMethod("getData",stackType).invoke(null,note());}
    private int pool(String tag) throws Exception {return ((Number)knowledge.getClass().getMethod("getAspectPoolFor",String.class,aspectType).invoke(knowledge,"Research",aspect(tag))).intValue();}
    private void pool(String tag,int amount) throws Exception {
        knowledge.getClass().getMethod("addDiscoveredAspect",String.class,aspectType).invoke(knowledge,"Research",aspect(tag));
        knowledge.getClass().getMethod("setAspectPool",String.class,aspectType,short.class).invoke(knowledge,"Research",aspect(tag),(short)amount);
    }
    @SuppressWarnings("unchecked") private Object fixture() throws Exception {
        Object note=manager.getMethod("createNote",stackType,String.class,loader.loadClass("net.minecraft.world.World")).invoke(null,stack("Thaumcraft:ItemResearchNotes"),"NITOR",world);
        Object data=manager.getMethod("getData",stackType).invoke(null,note);
        Map<String,Object> hexes=(Map<String,Object>)data.getClass().getField("hexes").get(data),entries=(Map<String,Object>)data.getClass().getField("hexEntries").get(data);
        hexes.clear();entries.clear();
        // Air -- light -- fire is a real component connection; only the middle cell is editable.
        for(int q=-1;q<=1;q++) {
            Object hex=loader.loadClass("thaumcraft.common.lib.utils.HexUtils$Hex").getConstructor(int.class,int.class).newInstance(q,0);
            Object entry=loader.loadClass("thaumcraft.common.lib.research.ResearchManager$HexEntry").getConstructor(aspectType,int.class).newInstance(q==0?null:aspect(q<0?"aer":"ignis"),q==0?0:1);
            hexes.put(hex.toString(),hex);entries.put(hex.toString(),entry);
        }
        manager.getMethod("updateData",stackType,data.getClass()).invoke(null,note,data);return note;
    }
    private LegacyPackets.Message packet(int q,int r,String tag) throws Exception {
        Object packet=loader.loadClass("thaumcraft.common.lib.network.playerdata.PacketAspectPlaceToServer")
                .getConstructor(loader.loadClass("net.minecraft.entity.player.EntityPlayer"),byte.class,byte.class,int.class,int.class,int.class,aspectType)
                .newInstance(player,(byte)q,(byte)r,0,0,0,tag==null?null:aspect(tag));
        return LegacyPackets.encode(wrapper,packet,"SERVER");
    }
    private void apply(int q,int r,String tag) throws Exception {LegacyPackets.receiveServer(services,packet(q,r,tag),player,device);}
    private void reject(int q,int r,String tag) throws Exception {
        String before=services.items().snapshot(note()).tag();
        try {apply(q,r,tag);throw new AssertionError("Invalid research operation accepted: "+q+","+r+":"+tag);}
        catch(IllegalArgumentException expected){}
        if(!before.equals(services.items().snapshot(note()).tag()))throw new AssertionError("Rejected packet changed the note");
    }
    private void verify() throws Exception {
        Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
        knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
        services.playerState().initialize(player);
        Object inventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        Object container=loader.loadClass("thaumcraft.common.container.ContainerResearchTable").getConstructor(inventory.getClass(),tile.getClass()).newInstance(inventory,tile);
        var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        playerType.getField("field_71070_bA").set(player,container);
        Object ink=stack("Thaumcraft:ItemInkwell"),note=fixture();
        var carried=inventory.getClass().getMethod("func_70437_b",stackType);
        var click=container.getClass().getMethod("func_75144_a",int.class,int.class,int.class,playerType);
        carried.invoke(inventory,ink);click.invoke(container,0,0,0,player);
        carried.invoke(inventory,note);click.invoke(container,1,0,0,player);
        if(note()==null||inventory.getClass().getMethod("func_70445_o").invoke(inventory)!=null)throw new AssertionError("Original note slot rejected insertion");
        pool("aer",3);pool("ignis",3);pool("lux",2);
        reject(40,40,"lux");reject(-1,0,"lux");reject(0,0,null);
        pool("lux",0);reject(0,0,"lux");pool("lux",2);
        var responses=new ArrayList<LegacyPackets.Message>();
        try(var capture=LegacyPackets.capture(new UUID(41,42))) {
            apply(0,0,"aer"); // A wrong connection stays editable and can be erased.
            if((boolean)data().getClass().getMethod("isComplete").invoke(data())||pool("aer")!=2)throw new AssertionError("Wrong connection completed or did not consume a point");
            reject(0,0,"lux");
            apply(0,0,null);
            if(services.items().snapshot(note()).metadata()!=0)throw new AssertionError("Erasing completed the note");
            byte[] saved=services.tiles().save(tile);services.tiles().load(tile,saved);
            // The adapter must refresh cached data after NBT reload before validating the next cell.
            apply(0,0,"lux");responses.addAll(capture.messages());
        }
        if(pool("lux")!=1||services.items().snapshot(note()).metadata()!=64||!(boolean)data().getClass().getMethod("isComplete").invoke(data()))throw new AssertionError("Original handler did not complete the connected note");
        if(services.items().snapshot(tile.getClass().getMethod("func_70301_a",int.class).invoke(tile,0)).metadata()!=3)throw new AssertionError("Place/erase actions did not consume exactly three ink");
        if(responses.size()<2)throw new AssertionError("Original aspect pool replies were lost");
        reject(0,0,null);
        byte[] saved=services.tiles().save(tile);services.tiles().load(tile,saved);
        if(services.items().snapshot(note()).metadata()!=64||!(boolean)data().getClass().getMethod("isComplete").invoke(data()))throw new AssertionError("Completed note did not survive NBT reload");
        click.invoke(container,1,0,0,player);
        Object returned=inventory.getClass().getMethod("func_70445_o").invoke(inventory);
        if(note()!=null||returned==null||services.items().snapshot(returned).metadata()!=64)throw new AssertionError("Completed note could not be taken from the table");
        System.out.println("MFCL_RESEARCH_TABLE_PASS");
    }
    public static void main(String[] args) throws Exception {
        Path game=Path.of(args[0]);
        try(var runtime=new EmbeddedLegacyRuntime(game,Path.of(args[1]),new LegacyRuntimeService().inspect(game))) {
            var report=runtime.boot();if(!Boolean.TRUE.equals(report.get("legacyFmlStarted")))throw new AssertionError(report);
            var services=runtime.services();services.items().inLegacy(()->{
                var probe=new ResearchTableSemanticsProbe(services);
                try(var state=services.playerState().enter(probe.world)) {
                    probe.verify();
                }
                return null;
            });
        }
    }
}
