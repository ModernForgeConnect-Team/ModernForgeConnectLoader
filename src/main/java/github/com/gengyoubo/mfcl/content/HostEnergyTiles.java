package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Callable;

/** Live sided views of native FE tiles for old RF neighbor lookups. Cached views never outlive their native tile. */
final class HostEnergyTiles {
    private final ModernContentBridge content;
    private final ServerLevel level;
    private record Peer(int mask,WeakReference<Object> view) {}
    private final Map<BlockEntity,Peer> peers=new WeakHashMap<>();
    HostEnergyTiles(ModernContentBridge content,ServerLevel level){this.content=content;this.level=level;}
    private <T> T host(Callable<T> action) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("FE bridge requires the server thread");
        Thread thread=Thread.currentThread();ClassLoader previous=thread.getContextClassLoader();
        try{thread.setContextClassLoader(getClass().getClassLoader());return action.call();}
        catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException("Cannot access native FE tile",failure);}
        finally{thread.setContextClassLoader(previous);}
    }
    Object find(BlockPos position) {
        return host(()->{
            if(!level.hasChunkAt(position)||!content.services().energy().available())return null;
            var tile=level.getBlockEntity(position);
            if(tile==null||tile.isRemoved()||tile instanceof LegacyBlockEntity)return null;
            var endpoint=new Endpoint(tile);int mask=0;
            for(int side=0;side<=6;side++){if(endpoint.canReceive(side))mask|=1;if(endpoint.canExtract(side))mask|=2;}
            if(mask==0){peers.remove(tile);return null;}
            var cached=peers.get(tile);Object peer=cached==null?null:cached.view().get();if(peer!=null&&cached.mask()==mask)return peer;
            peer=content.services().energy().hostTile(content.services().worlds().bound(level),position.getX(),position.getY(),position.getZ(),endpoint);
            if(peer!=null)peers.put(tile,new Peer(mask,new WeakReference<>(peer)));return peer;
        });
    }
    private final class Endpoint implements HostEnergyAccess {
        private final BlockEntity tile;
        private final BlockPos position;
        Endpoint(BlockEntity tile){this.tile=tile;position=tile.getBlockPos().immutable();}
        private IEnergyStorage storage(int side) {
            if(side<0||side>6)throw new IllegalArgumentException("Invalid RF side: "+side);
            if(content.services().worlds().findBound(level)==null||tile.isRemoved()||!level.hasChunkAt(position)||level.getBlockEntity(position)!=tile)return null;
            return tile.getCapability(ForgeCapabilities.ENERGY,side==6?null:Direction.from3DDataValue(side)).orElse(null);
        }
        @Override public boolean canReceive(int side){return host(()->{var energy=storage(side);return energy!=null&&energy.canReceive();});}
        @Override public boolean canExtract(int side){return host(()->{var energy=storage(side);return energy!=null&&energy.canExtract();});}
        @Override public int stored(int side){return host(()->{var energy=storage(side);return energy==null?0:Math.max(0,energy.getEnergyStored());});}
        @Override public int capacity(int side){return host(()->{var energy=storage(side);return energy==null?0:Math.max(0,energy.getMaxEnergyStored());});}
        @Override public int receive(int side,int amount,boolean simulate){return transfer(side,amount,simulate,true);}
        @Override public int extract(int side,int amount,boolean simulate){return transfer(side,amount,simulate,false);}
        private int transfer(int side,int amount,boolean simulate,boolean insert) {
            if(amount<=0)return 0;
            return host(()->{
                var energy=storage(side);if(energy==null||!(insert?energy.canReceive():energy.canExtract()))return 0;
                Callable<Integer> action=()->{
                    int moved=LegacyEnergyAccess.transferred(insert?energy.receiveEnergy(amount,simulate):energy.extractEnergy(amount,simulate),amount);
                    if(!simulate&&moved>0)tile.setChanged();return moved;
                };
                return simulate?action.call():content.logicTransaction(level,position,action,false);
            });
        }
    }
}
