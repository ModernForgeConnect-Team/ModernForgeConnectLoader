package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Runs original right-clicks, terrain and arrival-room code, including host-player/fake-player separation. */
final class ExtraUtilitiesPortalProbe {
    private static final UUID ID=new UUID(57,91);
    private static final class World implements HostWorldAccess {
        final int dimension;final Map<List<Integer>,Block> blocks=new HashMap<>();final List<Transfer> transfers=new ArrayList<>();
        boolean real=true;
        World(int dimension){this.dimension=dimension;}
        public String dimensionKey(){return dimension==0?"minecraft:overworld":"extrautils:deep_dark";}
        public int legacyDimensionId(){return dimension;}public boolean clientSide(){return false;}
        public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
        public Block block(int x,int y,int z){return blocks.getOrDefault(List.of(x,y,z),new Block("minecraft:air",0));}
        public boolean setBlock(int x,int y,int z,Block block,int flags){blocks.put(List.of(x,y,z),block);return true;}
        public void markTileDirty(int x,int y,int z,Object tile){}public void blockUpdated(int x,int y,int z){}
        public void schedule(int x,int y,int z,String key,int delay){}
        public void transferPlayer(Transfer transfer){transfers.add(transfer);}
        public List<Entity> players(){return real?List.of(new Entity(ID,"net.minecraft.entity.player.EntityPlayerMP","Portal",.5,65,.5,0,0,0,0,.6f,1.8f,false,true)):List.of();}
        public int[] findBlock(String key,int x,int y,int z,int radius){
            return blocks.entrySet().stream().filter(e->e.getValue().legacyKey().equals(key)).map(e->new int[]{e.getKey().get(0),e.getKey().get(1),e.getKey().get(2)}).findFirst().orElse(null);
        }
    }
    static void verify(LegacyServices services) throws Exception {
        var loader=services.loader();
        var definition=services.dimensions().byHostKey("extrautils:deep_dark");
        if(definition==null||!definition.provider().noSky()||!services.dimensions().hasTerrain(definition.hostKey()))throw new AssertionError("Original Deep Dark dimension was not mirrored");
        var overworld=new World(0);var dark=new World(definition.legacyId());
        Object ow=services.worlds().bind(overworld,overworld),dw=services.worlds().bind(dark,dark);
        try {
            services.items().inLegacy(()->{
                Object block=loader.loadClass("com.rwtema.extrautils.ExtraUtils").getField("portal").get(null);
                var activate=block.getClass().getMethod("func_149727_a",loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,float.class,float.class,float.class);
                overworld.blocks.put(List.of(0,64,0),new HostWorldAccess.Block("ExtraUtilities:dark_portal",0));
                Object actor=services.tiles().actor(ow,new LegacyTiles.Placer(ID,"Portal",.5,65,.5,0,0),null);
                if(!(boolean)activate.invoke(block,ow,0,64,0,actor,1,.5f,1f,.5f)||overworld.transfers.size()!=1||overworld.transfers.get(0).legacyDimension()!=definition.legacyId())throw new AssertionError("Original right-click did not enter Deep Dark");
                overworld.real=false;
                if((boolean)activate.invoke(block,ow,0,64,0,actor,1,.5f,1f,.5f)||overworld.transfers.size()!=1)throw new AssertionError("Unmapped fake player entered portal");
                var counts=new int[2];
                new LegacyGeneration(services).terrain(dark,0,0,(x,y,z,state)->{counts[0]++;if(state.legacyKey().equals("minecraft:bedrock"))counts[1]++;});
                if(counts[0]<10000||counts[1]<256)throw new AssertionError("Original terrain was empty: "+Arrays.toString(counts));
                var transfer=overworld.transfers.get(0);
                double[] position=LegacyTravel.place(dw,transfer.teleporter(),.5,65,.5,0);
                int[] portal=dark.findBlock("ExtraUtilities:dark_portal",0,0,0,80);
                if(portal==null||position[1]!=portal[1]+1||!dark.block(portal[0],portal[1]-1,portal[2]).legacyKey().equals("minecraft:cobblestone"))throw new AssertionError("Original arrival room was not built");
                Object back=services.tiles().actor(dw,new LegacyTiles.Placer(ID,"Portal",position[0],position[1],position[2],0,0),null);
                if(!(boolean)activate.invoke(block,dw,portal[0],portal[1],portal[2],back,1,.5f,1f,.5f)||dark.transfers.size()!=1||dark.transfers.get(0).legacyDimension()!=0)throw new AssertionError("Original right-click did not return to overworld");
                double[] returned=LegacyTravel.place(ow,dark.transfers.get(0).teleporter(),.5,position[1],.5,0);
                if(returned[1]!=65)throw new AssertionError("Return ignored original overworld portal");
                particles(services,dw);
                return null;
            });
        }finally{services.worlds().unbind(dark);services.worlds().unbind(overworld);}
    }
    private static void particles(LegacyServices services,Object world) throws Exception {
        var loader=services.loader();var mc=loader.loadClass("net.minecraft.client.Minecraft");Object client=mc.getMethod("func_71410_x").invoke(null);
        var renderer=loader.loadClass("net.minecraft.client.particle.EffectRenderer");
        var ctor=renderer.getConstructor(loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.client.renderer.texture.TextureManager"));
        Object first=ctor.newInstance(world,mc.getField("field_71446_o").get(client)),second=ctor.newInstance(world,mc.getField("field_71446_o").get(client));
        Object previous=mc.getField("field_71452_i").get(client);
        var helper=loader.loadClass("com.rwtema.extrautils.particle.ParticleHelperClient");var cached=helper.getDeclaredField("effectRenderer");cached.setAccessible(true);cached.set(null,null);
        try {
            var particle=loader.loadClass("com.rwtema.extrautils.particle.ParticlePortal").getConstructor(loader.loadClass("net.minecraft.world.World"),double.class,double.class,double.class,float.class,float.class,float.class);
            var add=helper.getMethod("addParticle",loader.loadClass("net.minecraft.client.particle.EntityFX"));
            mc.getField("field_71452_i").set(client,first);add.invoke(null,particle.newInstance(world,.5,65.,.5,1f,1f,1f));
            mc.getField("field_71452_i").set(client,second);add.invoke(null,particle.newInstance(world,.5,65.,.5,1f,1f,1f));
            if(!renderer.getMethod("func_78869_b").invoke(first).equals("1")||!renderer.getMethod("func_78869_b").invoke(second).equals("1"))throw new AssertionError("Particles did not follow current world renderer");
        }finally{mc.getField("field_71452_i").set(client,previous);}
    }
}
