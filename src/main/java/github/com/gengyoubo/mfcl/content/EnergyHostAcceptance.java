package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.*;
import java.util.*;

/** Opt-in generator acceptance only: real native FE capabilities attached to disposable vanilla chests. */
public final class EnergyHostAcceptance {
    private static final Map<BlockPos,Battery> BATTERIES=new HashMap<>();
    private EnergyHostAcceptance() {}
    public static void install(){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class,(net.minecraftforge.event.AttachCapabilitiesEvent<BlockEntity> event)->{
        Battery battery=BATTERIES.get(event.getObject().getBlockPos());if(battery==null||event.getObject().getType()!=net.minecraft.world.level.block.entity.BlockEntityType.CHEST)return;
        event.addCapability(ResourceLocation.fromNamespaceAndPath("mfcl","energy_acceptance"),battery);event.addListener(battery.capability::invalidate);
    });}
    private static final class Battery implements ICapabilitySerializable<CompoundTag>,IEnergyStorage {
        int energy,lastSide=-1;boolean input,output;final int limit;
        final LazyOptional<IEnergyStorage> capability=LazyOptional.of(()->this);
        Battery(int energy,boolean input,boolean output,int limit){this.energy=energy;this.input=input;this.output=output;this.limit=limit;}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){
            if(cap!=ForgeCapabilities.ENERGY||side!=Direction.WEST)return LazyOptional.empty();lastSide=side.get3DDataValue();return capability.cast();
        }
        public int receiveEnergy(int amount,boolean simulate){if(!input||amount<=0)return 0;int n=Math.min(Math.min(amount,limit),10000-energy);if(!simulate)energy+=n;return n;}
        public int extractEnergy(int amount,boolean simulate){if(!output||amount<=0)return 0;int n=Math.min(Math.min(amount,limit),energy);if(!simulate)energy-=n;return n;}
        public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return 10000;}public boolean canExtract(){return output;}public boolean canReceive(){return input;}
        public CompoundTag serializeNBT(){var tag=new CompoundTag();tag.putInt("energy",energy);return tag;}
        public void deserializeNBT(CompoundTag tag){energy=tag.getInt("energy");}
    }
    private static Battery place(ServerLevel level,BlockPos pos,int energy,boolean input,boolean output,int limit) {
        var battery=new Battery(energy,input,output,limit);BATTERIES.put(pos.immutable(),battery);level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());return battery;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public static Map<String,Object> verify(ModernContentBridge content,ServerLevel level,BlockPos position) throws Exception {
        var proof=new LinkedHashMap<String,Object>();var services=content.services();var loader=services.loader();
        Object world=services.worlds().bound(level);Class<?> oldWorld=loader.loadClass("net.minecraft.world.World");
        var getTile=oldWorld.getMethod("func_147438_o",int.class,int.class,int.class);
        var generator=(LegacyBlockEntity)level.getBlockEntity(position);byte[] saved=services.tiles().save(generator.legacy());
        BlockPos sinkPos=position.east(),sourcePos=position.offset(4,0,0),nodePos=position.offset(0,0,4),removedPos=position.offset(0,0,6);
        var sink=place(level,sinkPos,0,true,false,17);var source=place(level,sourcePos,1000,false,true,19);
        try {
            var cap=generator.getCapability(ForgeCapabilities.ENERGY,Direction.EAST).orElseThrow(()->new IllegalStateException("RF generator exposes no FE capability"));
            Object original=generator.legacy(),storage=original.getClass().getMethod("getStorage").invoke(original);
            storage.getClass().getMethod("setEnergyStored",int.class).invoke(storage,1000);
            original.getClass().getField("coolDown").setDouble(original,0);
            require(cap.extractEnergy(37,true)==37&&cap.getEnergyStored()==1000,"Modern extraction simulation changed RF storage");
            require(cap.receiveEnergy(100,false)==0&&cap.extractEnergy(-1,false)==0,"Generator accepted unsupported/negative FE transfer");
            require(cap.extractEnergy(37,false)==37&&cap.getEnergyStored()==963,"Modern FE extraction is not 1:1");
            Object sinkPeer=getTile.invoke(world,sinkPos.getX(),sinkPos.getY(),sinkPos.getZ());
            require(sinkPeer==getTile.invoke(world,sinkPos.getX(),sinkPos.getY(),sinkPos.getZ()),"FE neighbor facade identity was not retained");
            Class<?> receiver=loader.loadClass("cofh.api.energy.IEnergyReceiver"),provider=loader.loadClass("cofh.api.energy.IEnergyProvider");
            require(receiver.isInstance(sinkPeer)&&!provider.isInstance(sinkPeer),"Receive-only FE tile advertises RF extraction");
            var direction=loader.loadClass("net.minecraftforge.common.util.ForgeDirection");Object west=direction.getEnumConstants()[4],east=direction.getEnumConstants()[5];
            var receive=receiver.getMethod("receiveEnergy",direction,int.class,boolean.class);
            require((int)receive.invoke(sinkPeer,east,100,false)==0&&sink.energy==0,"Native FE face restriction lost");
            require((int)receive.invoke(sinkPeer,west,100,true)==17&&sink.energy==0,"Native FE simulation/rate limit lost");
            content.logicTransaction(level,position,()->services.logic().tick(original),false);
            require(sink.energy==17&&cap.getEnergyStored()==946&&sink.lastSide==4,"Original RF generator push failed energy conservation/side mapping");
            proof.put("rfPushToNativeFe",true);proof.put("nativeFePullFromRf",true);proof.put("sidesRatesSimulation",true);
            level.setBlockAndUpdate(nodePos,content.blocks().get("ExtraUtilities:extractor_base").defaultBlockState().setValue(ModernContentBridge.METADATA,12));
            var node=(LegacyBlockEntity)level.getBlockEntity(nodePos);var nodeCap=node.getCapability(ForgeCapabilities.ENERGY,null).orElseThrow(IllegalStateException::new);
            require(nodeCap.receiveEnergy(91,true)==91&&nodeCap.getEnergyStored()==0,"Native FE -> RF receive simulation changed state");
            require(nodeCap.receiveEnergy(91,false)==91&&nodeCap.getEnergyStored()==91,"Native FE -> RF receive is not 1:1");
            Object sourcePeer=getTile.invoke(world,sourcePos.getX(),sourcePos.getY(),sourcePos.getZ());
            require(provider.isInstance(sourcePeer)&&!receiver.isInstance(sourcePeer),"Output-only FE tile advertises RF insertion");
            var extract=provider.getMethod("extractEnergy",direction,int.class,boolean.class);
            require((int)extract.invoke(sourcePeer,west,100,true)==19&&source.energy==1000,"RF -> native FE extract simulation changed state");
            content.logicTransaction(level,nodePos,()->{
                int pulled=(int)extract.invoke(sourcePeer,west,100,false);
                require(services.energy().receive(node.legacy(),null,pulled,false)==pulled,"RF receiver rejected extracted FE");return null;
            },false);
            require(source.energy==981&&nodeCap.getEnergyStored()==110,"RF pull from native FE source is not conserved");
            proof.put("nativeFePushToRf",true);proof.put("rfPullFromNativeFe",true);
            source.input=true;source.output=false;
            Object reconfigured=getTile.invoke(world,sourcePos.getX(),sourcePos.getY(),sourcePos.getZ());
            require(receiver.isInstance(reconfigured)&&!provider.isInstance(reconfigured)&&(int)extract.invoke(sourcePeer,west,1,false)==0,"Native port reconfiguration left a stale RF interface");
            source.input=false;source.output=true;proof.put("nativePortReconfiguration",true);
            int generatorBefore=cap.getEnergyStored(),sinkBefore=sink.energy;
            try {
                content.logicTransaction(level,position,()->{
                    require(services.energy().extract(original,5,17,false)==17,"Rollback setup cannot debit RF");
                    require((int)receive.invoke(sinkPeer,west,17,false)==17,"Rollback setup cannot credit FE");
                    throw new IllegalStateException("intentional-energy-rollback");
                },false);
                throw new AssertionError("Failed energy transaction committed");
            }catch(IllegalStateException expected){if(!expected.getMessage().equals("intentional-energy-rollback"))throw expected;}
            require(cap.getEnergyStored()==generatorBefore&&sink.energy==sinkBefore,"Failed old callback duplicated/lost energy");
            proof.put("crossRuntimeRollback",true);
            var nodeTag=node.saveWithFullMetadata();nodeCap.receiveEnergy(1,false);node.load(nodeTag);
            require(nodeCap.getEnergyStored()==110,"RF capability mutation did not survive NBT restoration");
            var nativeTile=level.getBlockEntity(sourcePos);var nativeTag=nativeTile.saveWithFullMetadata();source.energy=0;nativeTile.load(nativeTag);
            require(source.energy==981,"Native FE persistence lost bridged transfer");proof.put("bothSidesNbtPersistence",true);
            level.removeBlock(sinkPos,false);place(level,sinkPos,0,true,false,17);
            require((int)receive.invoke(sinkPeer,west,10,false)==0,"Cached RF facade mutated a replacement native tile");
            require(!sink.capability.isPresent(),"Removed native capability was not invalidated");
            level.setBlockAndUpdate(removedPos,content.blocks().get("ExtraUtilities:generator").defaultBlockState());
            var removed=(LegacyBlockEntity)level.getBlockEntity(removedPos);var removedCap=removed.getCapability(ForgeCapabilities.ENERGY,null);var held=removedCap.orElseThrow(IllegalStateException::new);
            level.removeBlock(removedPos,false);require(!removedCap.isPresent()&&held.extractEnergy(1,false)==0,"Removed RF capability remained usable");
            proof.put("removalReplacementInvalidation",true);
            require(generator.getCapability(ForgeCapabilities.ENERGY,null).orElseThrow(IllegalStateException::new).getMaxEnergyStored()>0,"UNKNOWN RF side mapping failed");
            proof.put("ratio","1 RF = 1 FE");return proof;
        }finally{
            services.tiles().load(generator.legacy(),saved);generator.synchronize();
            for(BlockPos pos:List.of(sinkPos,sourcePos,nodePos,removedPos)){level.removeBlock(pos,false);BATTERIES.remove(pos);}
        }
    }
}
