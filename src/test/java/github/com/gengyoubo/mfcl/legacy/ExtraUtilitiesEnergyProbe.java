package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Real CoFH interfaces and generator/node algorithms, including generated typed FE neighbor views. */
final class ExtraUtilitiesEnergyProbe {
    static void verify(LegacyServices services) throws Exception {
        pipeHeights(services);
        solar(services);
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
    private static void solar(LegacyServices services)throws Exception{
        long[] clock={6000,0};boolean[] powered={false};var tiles=new HashMap<Integer,Object>();Object identity=new Object();
        Object world=services.worlds().bind(identity,new HostWorldAccess(){
            public String dimensionKey(){return "mfcl:solar";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
            public long gameTime(){return clock[1];}public long dayTime(){return clock[0];}public void setDayTime(long time){clock[0]=time;}
            public Block block(int x,int y,int z){return new Block(x==0?"ExtraUtilities:generator":"minecraft:air",x==0?7:0);}
            public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
            public boolean powered(int x,int y,int z){return powered[0];}
            public Object tileEntity(int x,int y,int z){return y==64&&z==0?tiles.get(x):null;}
            public void blockUpdated(int x,int y,int z){}public void markTileDirty(int x,int y,int z,Object tile){}
        });
        try{
            Object solar=services.tiles().create("ExtraUtilities:generator",7,world,0,64,0);tiles.put(0,solar);
            var gen=solar.getClass().getMethod("genLevel");
            double noon=((Number)gen.invoke(solar)).doubleValue();clock[0]=18000;
            double night=((Number)gen.invoke(solar)).doubleValue();
            if(noon!=40||night!=0)throw new AssertionError("Solar must follow host sun: noon="+noon+", midnight="+night);
            clock[0]=6000;var endpoint=new Endpoint();tiles.put(1,services.energy().hostTile(world,1,64,0,endpoint));
            for(int tick=0;tick<30;tick++){clock[1]++;services.logic().tick(solar);}
            int charged=services.energy().stored(solar,5);
            if(charged<=0||endpoint.stored!=0||services.energy().extract(solar,5,100,true)!=0)throw new AssertionError("Charging solar leaked energy or failed to charge");
            powered[0]=true;
            for(int tick=0;tick<240;tick++){clock[1]++;services.logic().tick(solar);}
            if(endpoint.stored<=0||services.energy().stored(solar,5)+endpoint.stored!=charged)throw new AssertionError("Powered solar did not finish its charge cycle and transfer conserved energy");
            System.out.println("MFCL_XU_SOLAR_PASS: host noon/midnight, charging blocks extraction, redstone releases after cooldown, RF conserved");
        }finally{services.worlds().unbind(identity);}
    }
    private static void pipeHeights(LegacyServices services)throws Exception{
        var tiles=new HashMap<Integer,Object>();Object identity=new Object();
        Object world=services.worlds().bind(identity,new HostWorldAccess(){
            public String dimensionKey(){return "mfcl:pipe-heights";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
            public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
            public Block block(int x,int y,int z){return new Block("ExtraUtilities:extractor_base",12);}public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
            public Object tileEntity(int x,int y,int z){return tiles.get(y);}public boolean loaded(int x,int y,int z){return y>=-64&&y<320&&x==0&&z==0;}
        });
        try{
            var loader=services.loader();var lookup=loader.loadClass("com.rwtema.extrautils.tileentity.transfernodes.TNHelper").getMethod("getPipe",loader.loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class);
            for(int y:new int[]{-64,-60,0,64,255,256,319}){
                Object node=services.tiles().create("ExtraUtilities:extractor_base",12,world,0,y,0);tiles.put(y,node);
                if(lookup.invoke(null,world,0,y,0)!=node)throw new AssertionError("Original pipe lookup rejected valid host height "+y);
            }
            tiles.put(-65,tiles.get(0));tiles.put(320,tiles.get(0));
            if(lookup.invoke(null,world,0,-65,0)!=null||lookup.invoke(null,world,0,320,0)!=null||lookup.invoke(null,world,1,64,0)!=null)throw new AssertionError("Pipe lookup ignored host bounds or unloaded position");
            if(LegacyWorlds.validPosition(new Object(),0,-1,0)||LegacyWorlds.validPosition(new Object(),0,256,0)||!LegacyWorlds.validPosition(new Object(),0,255,0))throw new AssertionError("Unbound old-world height policy changed");
            System.out.println("MFCL_XU_PIPE_HEIGHTS_PASS: -64..319, old range retained, host bounds/loading respected");
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
