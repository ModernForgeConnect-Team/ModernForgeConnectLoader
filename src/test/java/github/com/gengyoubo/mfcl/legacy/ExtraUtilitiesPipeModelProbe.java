package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Original pipe world model sees the same neighbour tiles in a render view as the live old worlds. */
final class ExtraUtilitiesPipeModelProbe {
    private record Cell(HostWorldAccess.Block block,boolean legacyTile,HostEnergyAccess energy){}
    private static final HostEnergyAccess FE=new HostEnergyAccess(){
        public boolean canReceive(int side){return true;}public boolean canExtract(int side){return false;}
        public int stored(int side){return 0;}public int capacity(int side){return 0;}
        public int receive(int side,int amount,boolean simulate){return 0;}public int extract(int side,int amount,boolean simulate){return 0;}
    };
    static void verify(LegacyServices services)throws Exception{
        // Standard pipe: pipe east, a native chest south (no original tile), an original chest north.
        var cells=new HashMap<List<Integer>,Cell>();
        cells.put(List.of(0,0,0),new Cell(new HostWorldAccess.Block("ExtraUtilities:pipes",0),true,null));
        cells.put(List.of(1,0,0),new Cell(new HostWorldAccess.Block("ExtraUtilities:pipes",0),true,null));
        cells.put(List.of(0,0,1),new Cell(new HostWorldAccess.Block("minecraft:chest",0),false,null));
        cells.put(List.of(0,0,-1),new Cell(new HostWorldAccess.Block("minecraft:chest",0),true,null));
        expect(services,cells,Map.of("pipe",2,"nozzle",1,"base",1),"standard pipe");
        // Energy pipe: a native FE machine west has no original tile but is an RF endpoint to the live world.
        cells.clear();
        cells.put(List.of(0,0,0),new Cell(new HostWorldAccess.Block("ExtraUtilities:pipes",11),true,null));
        cells.put(List.of(-1,0,0),new Cell(new HostWorldAccess.Block("minecraft:stone",0),false,FE));
        expect(services,cells,Map.of("pipe",1,"nozzle",1,"base",1),"energy pipe");
        System.out.println("MFCL_XU_PIPE_MODEL_PASS: native tiles hidden like live worlds, original tiles and FE endpoints connect");
    }
    private static void expect(LegacyServices services,Map<List<Integer>,Cell> cells,Map<String,Integer> expected,String name)throws Exception{
        var air=new Cell(new HostWorldAccess.Block("minecraft:air",0),true,null);
        var view=new LegacyBlockRendering.View(){
            public HostWorldAccess.Block block(int x,int y,int z){return cells.getOrDefault(List.of(x,y,z),air).block();}
            public boolean legacyTile(int x,int y,int z){return cells.getOrDefault(List.of(x,y,z),air).legacyTile();}
            public HostEnergyAccess energy(int x,int y,int z){return cells.getOrDefault(List.of(x,y,z),air).energy();}
        };
        var loader=services.loader();
        var labels=services.items().inLegacy(()->{
            try(var scene=new LegacyBlockScene(services,view)){
                Object world=scene.world();
                Object block=loader.loadClass("net.minecraft.world.IBlockAccess").getMethod("func_147439_a",int.class,int.class,int.class).invoke(world,0,0,0);
                Object model=block.getClass().getMethod("getWorldModel",loader.loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class).invoke(block,world,0,0,0);
                var counts=new TreeMap<String,Integer>();
                for(Object box:(Iterable<?>)model)counts.merge((String)box.getClass().getField("label").get(box),1,Integer::sum);
                return counts;
            }
        });
        if(!labels.equals(new TreeMap<>(expected)))throw new AssertionError(name+" model: expected "+new TreeMap<>(expected)+", got "+labels);
    }
}
