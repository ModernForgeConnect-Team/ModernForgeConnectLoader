package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

final class ExtraUtilitiesCaptureAndTicketsProbe {
    private static final UUID PIG=new UUID(83,99);
    private static final class World implements HostWorldAccess {
        final Map<UUID,Set<List<Integer>>> tickets=new HashMap<>();boolean removed,restored;
        public String dimensionKey(){return "mfcl:item-repair";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
        public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
        public Block block(int x,int y,int z){return new Block("minecraft:stone",0);}public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
        public Entity entity(UUID id){return id.equals(PIG)&&!removed?new Entity(PIG,"net.minecraft.entity.passive.EntityPig","Named Pig",0,64,0,0,0,0,0,.6f,.9f,false,true):null;}
        public String entityData(UUID id){return id.equals(PIG)&&!removed?"native opaque state":null;}
        public void removeEntity(UUID id){if(!id.equals(PIG))throw new AssertionError("Wrong captured identity");removed=true;}
        public boolean restoreEntity(String data,double x,double y,double z,String name){
            if(!data.equals("native opaque state")||x!=.5||y!=65||z!=.5||!name.equals("Named Pig"))throw new AssertionError("Capture payload/placement/name lost");restored=true;return true;
        }
        public void forceChunk(UUID owner,int x,int z,boolean add){var chunks=tickets.computeIfAbsent(owner,k->new HashSet<>());if(add)chunks.add(List.of(x,z));else chunks.remove(List.of(x,z));if(chunks.isEmpty())tickets.remove(owner);}
    }
    static void verify(LegacyServices services) throws Exception {
        var host=new World();Object world=services.worlds().bind(host,host);
        try{services.items().inLegacy(()->{
            var loader=services.loader();var use=new LegacyItemUse(services);var source=new LegacyTiles.Placer(new UUID(88,99),"Capture",0,64,0,0,0);
            var held=new LegacyClientData.Stack("ExtraUtilities:golden_lasso",0,1,"","");
            if(!use.interact(world,source,held,PIG,false).used()||!host.removed)throw new AssertionError("Original lasso did not capture hosted native entity");
            var inventory=services.playerInventory().read(use.clientActor(world,source,null));held=inventory.get(0);
            if(held.metadata()!=2||!held.tag().contains("native opaque state"))throw new AssertionError("Original lasso did not retain capture payload/name state");
            var release=new LegacyItemPlacement(services).use(world,source,held,inventory,0,0,64,0,1,.5f,1,.5f);
            if(!release.used()||!host.restored||release.stack().metadata()!=0)throw new AssertionError("Original lasso did not restore hosted native entity");
            var manager=loader.loadClass("net.minecraftforge.common.ForgeChunkManager");var ticket=loader.loadClass("net.minecraftforge.common.ForgeChunkManager$Ticket");var kind=loader.loadClass("net.minecraftforge.common.ForgeChunkManager$Type");
            Object mod=loader.loadClass("com.rwtema.extrautils.ExtraUtilsMod").getField("instance").get(null);
            var request=manager.getMethod("requestTicket",Object.class,loader.loadClass("net.minecraft.world.World"),kind);Object type=kind.getEnumConstants()[0];
            Object first=request.invoke(null,mod,world,type),second=request.invoke(null,mod,world,type);if(first==null||second==null)throw new AssertionError("Original chunk ticket request was rejected");
            var pair=loader.loadClass("net.minecraft.world.ChunkCoordIntPair");Object a=pair.getConstructor(int.class,int.class).newInstance(1,2),b=pair.getConstructor(int.class,int.class).newInstance(3,4);
            var force=manager.getMethod("forceChunk",ticket,pair);var releaseTicket=manager.getMethod("releaseTicket",ticket);
            force.invoke(null,first,a);force.invoke(null,second,a);if(host.tickets.size()!=2)throw new AssertionError("Shared chunk lost independent ticket owners");
            releaseTicket.invoke(null,first);if(host.tickets.size()!=1)throw new AssertionError("Releasing one owner removed another owner's ticket");
            ticket.getMethod("setChunkListDepth",int.class).invoke(second,1);force.invoke(null,second,b);
            if(!host.tickets.values().iterator().next().equals(Set.of(List.of(3,4))))throw new AssertionError("Original maximum depth/eviction was not mirrored");
            releaseTicket.invoke(null,second);if(!host.tickets.isEmpty())throw new AssertionError("Released original ticket retained host chunks");
            System.out.println("MFCL_XU_CAPTURE_TICKETS_PASS: original lasso, named native payload round trip, shared chunk owners, depth eviction and release");return null;
        });}finally{services.worlds().unbind(host);}
    }
}
