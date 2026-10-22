package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import java.nio.file.*;
import java.util.*;

/** Original drum bucket exchange through the block activation bridge. */
public final class ExtraUtilitiesDrumProbe {
    public static void main(String[] args) throws Exception {
        Path game=Path.of(args[0]);
        try(var runtime=new EmbeddedLegacyRuntime(game,Path.of(args[1]),new LegacyRuntimeService().inspect(game))) {
            var report=runtime.boot();if(!Boolean.TRUE.equals(report.get("legacyFmlStarted")))throw new AssertionError(report.get("stackTrace"));
            var services=runtime.services();
            Object[] tile={null};Object identity=new Object();
            Object world=services.worlds().bind(identity,new HostWorldAccess(){
                public String dimensionKey(){return "mfcl:drum";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
                public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
                public Block block(int x,int y,int z){return x==0&&y==64&&z==0?new Block("ExtraUtilities:drum",0):new Block("minecraft:air",0);}
                public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
                public Object tileEntity(int x,int y,int z){return x==0&&y==64&&z==0?tile[0]:null;}
                public void blockUpdated(int x,int y,int z){}public void markTileDirty(int x,int y,int z,Object tile){}
                public void schedule(int x,int y,int z,String block,int delay){}
            });
            tile[0]=services.tiles().create("ExtraUtilities:drum",0,world,0,64,0);
            var actor=new LegacyTiles.Placer(new UUID(3,4),"Drum",0.5,65,2,0,0);
            var contents=new ArrayList<LegacyClientData.Stack>(Collections.nCopies(40,null));
            contents.set(0,new LegacyClientData.Stack("minecraft:water_bucket",0,1,"",""));
            var filled=services.logic().activate("ExtraUtilities:drum",world,0,64,0,actor,contents,0,1,0.5f,1,0.5f);
            System.out.println("MFCL_DRUM fill handled="+filled.handled()+" slot0="+filled.inventory().get(0)+" tank="+tank(services,tile[0]));
            contents.set(0,new LegacyClientData.Stack("minecraft:bucket",0,1,"",""));
            var drained=services.logic().activate("ExtraUtilities:drum",world,0,64,0,actor,contents,0,1,0.5f,1,0.5f);
            System.out.println("MFCL_DRUM drain handled="+drained.handled()+" slot0="+drained.inventory().get(0)+" tank="+tank(services,tile[0]));
            if(!filled.handled()||!"minecraft:bucket".equals(filled.inventory().get(0).key())||!drained.handled()||!"minecraft:water_bucket".equals(drained.inventory().get(0).key()))
                throw new AssertionError("Original drum bucket exchange failed");
            var fluids=services.fluids();Object drum=tile[0];
            if(!fluids.supports(drum)||fluids.tanks(drum,1).size()!=1||fluids.tanks(drum,1).get(0).fluid()!=null)throw new AssertionError("Drum tank not exposed empty");
            if(fluids.fill(drum,1,"water",3000,true)!=3000||fluids.tanks(drum,1).get(0).amount()!=0)throw new AssertionError("Simulated fill changed the drum");
            if(fluids.fill(drum,1,"water",3000,false)!=3000||fluids.tanks(drum,1).get(0).amount()!=3000)throw new AssertionError("Fill lost fluid");
            if(fluids.fill(drum,1,"lava",1000,false)!=0)throw new AssertionError("Drum accepted a second fluid");
            if(fluids.fill(drum,1,"no_such_fluid",1000,false)!=0)throw new AssertionError("Unregistered fluid accepted");
            var any=fluids.drain(drum,1,null,1000,true);
            if(any==null||!"water".equals(any.fluid())||any.amount()!=1000||fluids.tanks(drum,1).get(0).amount()!=3000)throw new AssertionError("Simulated drain failed: "+any);
            if(fluids.drain(drum,1,"lava",1000,false)!=null)throw new AssertionError("Drained a fluid the drum does not hold");
            var water=fluids.drain(drum,1,"water",1000,false);
            if(water==null||water.amount()!=1000||fluids.tanks(drum,1).get(0).amount()!=2000)throw new AssertionError("Drain failed: "+water);
            int capacity=fluids.tanks(drum,1).get(0).capacity();
            System.out.println("MFCL_XU_DRUM_PASS: original bucket exchange, fluid capability fill/drain conserved, capacity="+capacity);
            services.worlds().unbind(identity);
        }
    }
    private static String tank(LegacyServices services,Object tile) throws Exception {
        return services.items().inLegacy(()->{
            var loader=services.loader();var dir=loader.loadClass("net.minecraftforge.common.util.ForgeDirection");
            Object[] info=(Object[])tile.getClass().getMethod("getTankInfo",dir).invoke(tile,dir.getEnumConstants()[1]);
            Object fluid=info[0].getClass().getField("fluid").get(info[0]);
            return fluid==null?"empty":fluid.getClass().getMethod("getLocalizedName").invoke(fluid)+"x"+fluid.getClass().getField("amount").get(fluid);
        });
    }
}
