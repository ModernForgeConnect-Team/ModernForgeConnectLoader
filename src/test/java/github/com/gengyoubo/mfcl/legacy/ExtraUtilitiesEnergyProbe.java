package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Real CoFH interfaces and generator/node algorithms, including generated typed FE neighbor views. */
final class ExtraUtilitiesEnergyProbe {
    static void verify(LegacyServices services) throws Exception {
        var loader=services.loader();var tiles=new HashMap<Integer,Object>();
        Object identity=new Object();Object world=services.worlds().bind(identity,new HostWorldAccess(){
            public String dimensionKey(){return "mfcl:rf";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
            public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
            public Block block(int x,int y,int z){return new Block(x==0?"ExtraUtilities:generator":"minecraft:air",0);}
            public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
            public Object tileEntity(int x,int y,int z){return y==64&&z==0?tiles.get(x):null;}
            public void blockUpdated(int x,int y,int z){}
            public void markTileDirty(int x,int y,int z,Object tile){}
        });
        try {
            var energy=services.energy();Object generator=services.tiles().create("ExtraUtilities:generator",0,world,0,64,0);tiles.put(0,generator);
            Class<?> block=loader.loadClass("com.rwtema.extrautils.tileentity.generators.BlockGenerator");
            var classes=(Class<?>[])block.getField("tiles").get(null);
            for(int meta=0;meta<16;meta++)if(classes[meta]==null&&services.tiles().create("ExtraUtilities:generator",meta,world,0,64,0)!=null)
                throw new AssertionError("Preview created a generator for an unsupported metadata");
            Object storage=generator.getClass().getMethod("getStorage").invoke(generator);
            storage.getClass().getMethod("receiveEnergy",int.class,boolean.class).invoke(storage,1000,false);
            if(energy.extract(generator,5,37,true)!=37||energy.stored(generator,5)!=1000)throw new AssertionError("RF extraction simulation changed energy");
            if(energy.extract(generator,5,37,false)!=37||energy.stored(generator,5)!=963)throw new AssertionError("RF extraction did not debit exactly 37");
            if(energy.receive(generator,5,99,false)!=0||energy.extract(generator,5,-1,false)!=0)throw new AssertionError("RF generator accepted input/negative transfer");
            Object node=services.tiles().create("ExtraUtilities:extractor_base",12,world,10,64,0);
            int accepted=energy.receive(node,null,100,true);
            if(accepted<=0||energy.stored(node,null)!=0)throw new AssertionError("RF insertion simulation changed node storage");
            if(energy.receive(node,null,100,false)!=accepted||energy.stored(node,null)!=accepted)throw new AssertionError("RF node insertion lost energy");
            var endpoint=new Endpoint();Object peer=energy.hostTile(world,1,64,0,endpoint);tiles.put(1,peer);
            Class<?> receiver=loader.loadClass("cofh.api.energy.IEnergyReceiver"),provider=loader.loadClass("cofh.api.energy.IEnergyProvider");
            if(!receiver.isInstance(peer)||provider.isInstance(peer))throw new AssertionError("Receive-only FE peer advertised RF extraction");
            var dir=loader.loadClass("net.minecraftforge.common.util.ForgeDirection");Object west=dir.getEnumConstants()[4],east=dir.getEnumConstants()[5];
            var receive=receiver.getMethod("receiveEnergy",dir,int.class,boolean.class);
            if((int)receive.invoke(peer,east,100,false)!=0||endpoint.stored!=0)throw new AssertionError("FE side restriction ignored");
            if((int)receive.invoke(peer,west,100,true)!=23||endpoint.stored!=0)throw new AssertionError("FE simulation/rate limit failed");
            services.logic().tick(generator);
            if(endpoint.stored!=23||energy.stored(generator,5)!=940)throw new AssertionError("Original generator did not conserve RF while pushing to FE");
            var source=new Endpoint();source.input=false;source.output=true;source.stored=50;
            Object sourcePeer=energy.hostTile(world,2,64,0,source);
            if(receiver.isInstance(sourcePeer)||!provider.isInstance(sourcePeer))throw new AssertionError("Output-only FE peer advertised RF insertion");
            var extract=provider.getMethod("extractEnergy",dir,int.class,boolean.class);
            if((int)extract.invoke(sourcePeer,west,100,true)!=23||source.stored!=50)throw new AssertionError("FE source simulation failed");
            if((int)extract.invoke(sourcePeer,west,100,false)!=23||source.stored!=27)throw new AssertionError("RF pull from FE source lost energy");
            endpoint.live=false;
            if((int)receive.invoke(peer,west,10,false)!=0||energy.connected(peer,4))throw new AssertionError("Expired FE peer remained usable");
            services.worlds().unbind(identity);
            if(energy.extract(generator,5,10,false)!=0)throw new AssertionError("Unloaded RF world remained usable");
        }finally{services.worlds().unbind(identity);}
    }
    private static final class Endpoint implements HostEnergyAccess {
        int stored;boolean input=true,output,live=true;
        public boolean canReceive(int side){return live&&input&&side==4;}
        public boolean canExtract(int side){return live&&output&&side==4;}
        public int stored(int side){return live?stored:0;}
        public int capacity(int side){return live?1000:0;}
        public int receive(int side,int amount,boolean simulate){if(!canReceive(side)||amount<=0)return 0;int count=Math.min(Math.min(amount,23),1000-stored);if(!simulate)stored+=count;return count;}
        public int extract(int side,int amount,boolean simulate){if(!canExtract(side)||amount<=0)return 0;int count=Math.min(Math.min(amount,23),stored);if(!simulate)stored-=count;return count;}
    }
}
