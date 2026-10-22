package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyDeviceContainers;
import net.minecraft.world.entity.player.Player;
import java.util.concurrent.Callable;

/** Original item/container drops join the enclosing host transaction. */
final class LegacyItemDrops {
    static <T> T capture(ModernContentBridge content,Player player,Callable<T> action) throws Exception {
        return content.services().items().inLegacy(()->LegacyDeviceContainers.withDrops(entity->{
            content.afterCommit(player.level(),()->{
                try {
                    var drop=content.services().items().inLegacy(()->{
                        var type=content.services().loader().loadClass("net.minecraft.entity.Entity");
                        if(type.getField("field_70128_L").getBoolean(entity))return null;
                        var snapshot=content.services().items().snapshot(entity.getClass().getMethod("func_92059_d").invoke(entity));if(snapshot.count()<=0)return null;
                        var stack=content.gameplayStack(snapshot);
                        var result=new net.minecraft.world.entity.item.ItemEntity(player.level(),type.getField("field_70165_t").getDouble(entity),type.getField("field_70163_u").getDouble(entity),type.getField("field_70161_v").getDouble(entity),stack);
                        result.setDeltaMovement(type.getField("field_70159_w").getDouble(entity),type.getField("field_70181_x").getDouble(entity),type.getField("field_70179_y").getDouble(entity));return result;
                    });
                    if(drop!=null){drop.setDefaultPickUpDelay();drop.setThrower(player.getUUID());player.level().addFreshEntity(drop);}
                }catch(Exception failure){throw new IllegalStateException("Cannot commit original item drop",failure);}
            });
        },action));
    }
}
