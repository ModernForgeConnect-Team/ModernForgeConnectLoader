package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import java.nio.file.*;
import java.util.*;

/** Builder's wand placement, QED pillar discovery and ender quarry mining into host storage, on mutable host worlds. */
public final class ExtraUtilitiesToolsProbe {
    static final class World implements HostWorldAccess {
        final Map<List<Integer>,Block> blocks=new HashMap<>();final Map<List<Integer>,Object> tiles=new HashMap<>();
        public String dimensionKey(){return "mfcl:tools";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
        public long dayTime(){return 0;}public void setDayTime(long time){}
        public Block block(int x,int y,int z){return blocks.getOrDefault(List.of(x,y,z),new Block("minecraft:air",0));}
        public boolean setBlock(int x,int y,int z,Block block,int flags){if(block.legacyKey().equals("minecraft:air"))blocks.remove(List.of(x,y,z));else blocks.put(List.of(x,y,z),block);return true;}
        public Object tileEntity(int x,int y,int z){return tiles.get(List.of(x,y,z));}
        public void setTileEntity(int x,int y,int z,Object tile){tiles.put(List.of(x,y,z),tile);}
        public void removeTileEntity(int x,int y,int z){tiles.remove(List.of(x,y,z));}
        public void blockUpdated(int x,int y,int z){}public void markTileDirty(int x,int y,int z,Object tile){}
        public void schedule(int x,int y,int z,String block,int delay){}
        long time;public long gameTime(){return time;}
        public void forceChunk(UUID ticket,int x,int z,boolean force){}
        public List<int[]> legacyTiles(int cx,int cz){return tiles.keySet().stream().filter(p->p.get(0)>>4==cx&&p.get(2)>>4==cz).map(p->new int[]{p.get(0),p.get(1),p.get(2)}).toList();}
        public List<Entity> players(){return List.of(new Entity(PLAYER,"net.minecraft.entity.player.EntityPlayerMP","Wand",3.5,64,3.5,0,0,0,0,.6f,1.8f,false,true));}
    }
    static final UUID PLAYER=new UUID(5,6);
    /** A 27-slot host chest of 64-item slots. */
    static final class Chest implements HostInventoryAccess {
        final LegacyClientData.Stack[] slots=new LegacyClientData.Stack[27];
        public boolean live(){return true;}public int size(){return slots.length;}
        public LegacyClientData.Stack get(int slot){return slots[slot];}public int limit(int slot){return 64;}
        public int insert(int slot,LegacyClientData.Stack stack,boolean simulate){
            var present=slots[slot];
            if(present!=null&&!(present.key().equals(stack.key())&&present.metadata()==stack.metadata()))return 0;
            int moved=Math.min(stack.count(),64-(present==null?0:present.count()));
            if(!simulate&&moved>0)slots[slot]=new LegacyClientData.Stack(stack.key(),stack.metadata(),(present==null?0:present.count())+moved,stack.name(),stack.tag());
            return moved;
        }
        public int extract(int slot,int amount,boolean simulate){
            var present=slots[slot];if(present==null)return 0;int moved=Math.min(amount,present.count());
            if(!simulate)slots[slot]=present.count()==moved?null:new LegacyClientData.Stack(present.key(),present.metadata(),present.count()-moved,present.name(),present.tag());
            return moved;
        }
    }
    public static void main(String[] args) throws Exception {
        Path game=Path.of(args[0]);
        try(var runtime=new EmbeddedLegacyRuntime(game,Path.of(args[1]),new LegacyRuntimeService().inspect(game))) {
            var report=runtime.boot();if(!Boolean.TRUE.equals(report.get("legacyFmlStarted")))throw new AssertionError(report.get("stackTrace"));
            var services=runtime.services();
            var host=new World();Object world=services.worlds().bind(host,host);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)host.blocks.put(List.of(x,63,z),new HostWorldAccess.Block("minecraft:stone",0));
            var actor=new LegacyTiles.Placer(PLAYER,"Wand",3.5,64,3.5,0,0);
            var wand=new LegacyClientData.Stack("ExtraUtilities:builderswand",0,1,"","");
            var contents=new ArrayList<LegacyClientData.Stack>(Collections.nCopies(36,null));
            contents.set(0,wand);contents.set(1,new LegacyClientData.Stack("minecraft:stone",0,64,"",""));
            var placement=new LegacyItemPlacement(services);
            {
                var result=placement.useFirst(world,actor,wand,contents,0,0,63,0,1,0.5f,1,0.5f);
                long placed=host.blocks.keySet().stream().filter(p->p.get(1)==64).count();
                if(!result.used()||placed!=9||result.inventory().get(1).count()!=55)throw new AssertionError("Builder's wand: used="+result.used()+" placed="+placed+" slot1="+result.inventory().get(1));
                System.out.println("MFCL_XU_WAND_PASS: 9 blocks placed, 9 consumed");
            }
            {
                host.blocks.put(List.of(0,70,0),new HostWorldAccess.Block("ExtraUtilities:endConstructor",0));
                host.blocks.put(List.of(3,70,0),new HostWorldAccess.Block("ExtraUtilities:endConstructor",2));
                Object qed=services.tiles().create("ExtraUtilities:endConstructor",0,world,0,70,0);host.tiles.put(List.of(0,70,0),qed);
                Object pillar=services.tiles().create("ExtraUtilities:endConstructor",2,world,3,70,0);host.tiles.put(List.of(3,70,0),pillar);
                // A queued recipe makes the QED request flux: an ender pearl alone in the grid is not needed for the scan itself.
                services.logic().tick(pillar);
                var targets=(java.util.Collection<?>)pillar.getClass().getField("targets").get(pillar);
                if(targets.size()!=1)throw new AssertionError("Ender pillar did not find the QED through its chunk tile map: "+targets.size());
                System.out.println("MFCL_XU_QED_PASS: ender pillar finds the QED through Chunk.chunkTileEntityMap");
            }
            services.worlds().unbind(host);
            quarry(services);
        }
    }
    private static void quarry(LegacyServices services) throws Exception {
        var host=new World();Object world=services.worlds().bind(host,host);
        try {
            for(int i=1;i<=6;i++)for(var p:List.of(List.of(i,64,0),List.of(i,64,6),List.of(0,64,i),List.of(6,64,i)))host.blocks.put(p,new HostWorldAccess.Block("minecraft:fence",0));
            for(int x=0;x<=6;x++)for(int z=0;z<=6;z++)for(int y=60;y<=63;y++)host.blocks.put(List.of(x,y,z),new HostWorldAccess.Block("minecraft:stone",0));
            host.blocks.put(List.of(0,64,0),new HostWorldAccess.Block("ExtraUtilities:enderQuarry",0));
            Object quarry=services.tiles().create("ExtraUtilities:enderQuarry",0,world,0,64,0);host.tiles.put(List.of(0,64,0),quarry);
            var chest=new Chest();host.tiles.put(List.of(0,65,0),LegacyInventoryPeers.create(services,world,0,65,0,chest));
            var actor=new LegacyTiles.Placer(PLAYER,"Wand",-1.5,64,-1.5,0,0);
            var activation=services.logic().activate("ExtraUtilities:enderQuarry",world,0,64,0,actor,new ArrayList<>(Collections.nCopies(36,null)),0,1,0.5f,1,0.5f);
            if(!activation.handled())throw new AssertionError("Quarry activation was not handled");
            Object storage=quarry.getClass().getField("energy").get(quarry);
            storage.getClass().getMethod("receiveEnergy",int.class,boolean.class).invoke(storage,10_000_000,false);
            for(int tick=0;tick<400;tick++){host.time++;services.logic().tick(quarry);}
            long stone=host.blocks.values().stream().filter(b->b.legacyKey().equals("minecraft:stone")).count();
            int stored=Arrays.stream(chest.slots).filter(Objects::nonNull).mapToInt(LegacyClientData.Stack::count).sum();
            if(stone>=196||stored!=196-stone)throw new AssertionError("Quarry did not deliver every mined block to host storage: mined="+(196-stone)+" stored="+stored);
            System.out.println("MFCL_XU_QUARRY_PASS: fake player, ranged packets and host storage output");
        } finally{services.worlds().unbind(host);}
    }
}
