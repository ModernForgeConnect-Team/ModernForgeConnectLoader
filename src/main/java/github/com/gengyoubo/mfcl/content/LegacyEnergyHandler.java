package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;
import java.util.concurrent.Callable;

/** Modern FE capability backed directly by the original RF methods and host transaction. */
final class LegacyEnergyHandler implements IEnergyStorage {
    private final LegacyBlockEntity tile;
    private final Integer side;
    private boolean reported;
    LegacyEnergyHandler(LegacyBlockEntity tile,Direction side){this.tile=tile;this.side=side==null?null:side.get3DDataValue();}
    private boolean live() {
        var level=tile.getLevel();
        return level!=null&&!tile.isRemoved()&&level.hasChunkAt(tile.getBlockPos())&&level.getBlockEntity(tile.getBlockPos())==tile;
    }
    private <T> T call(Callable<T> action,T rejected) {
        if(tile.getLevel()==null||tile.isRemoved())return rejected;
        try {
            tile.content().services().worlds().bound(tile.getLevel());
            if(!live())return rejected;
            return action.call();
        }catch(Exception failure){
            if(!reported){reported=true;com.mojang.logging.LogUtils.getLogger().warn("MFCL rejected RF capability operation at {}",tile.getBlockPos(),failure);}
            return rejected;
        }
    }
    @Override public boolean canReceive(){return call(()->tile.content().services().energy().canReceive(tile.legacyOrNull(),side),false);}
    @Override public boolean canExtract(){return call(()->tile.content().services().energy().canExtract(tile.legacyOrNull(),side),false);}
    @Override public int getEnergyStored(){return call(()->tile.content().services().energy().stored(tile.legacyOrNull(),side),0);}
    @Override public int getMaxEnergyStored(){return call(()->tile.content().services().energy().capacity(tile.legacyOrNull(),side),0);}
    @Override public int receiveEnergy(int amount,boolean simulate){return transfer(amount,simulate,true);}
    @Override public int extractEnergy(int amount,boolean simulate){return transfer(amount,simulate,false);}
    private int transfer(int amount,boolean simulate,boolean insert) {
        if(amount<=0)return 0;
        return call(()->{
            if(tile.getLevel().isClientSide)return 0;
            var content=tile.content();var energy=content.services().energy();Object original=tile.legacyOrNull();
            Callable<Integer> action=()->insert?energy.receive(original,side,amount,simulate):energy.extract(original,side,amount,simulate);
            return simulate?action.call():content.logicTransaction(tile.getLevel(),tile.getBlockPos(),action,false);
        },0);
    }
}
