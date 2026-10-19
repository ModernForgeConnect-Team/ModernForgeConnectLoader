package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Snapshots host entities for legacy AABB queries without exposing native entities across class loaders. */
public final class HostEntityQueries {
    private HostEntityQueries() {}
    public static List<HostWorldAccess.Entity> query(Level level,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        return level.getEntities((Entity)null,new AABB(minX,minY,minZ,maxX,maxY,maxZ)).stream().map(entity->{
            String type=entity instanceof Player?"net.minecraft.entity.player."+(level.isClientSide?"EntityClientPlayerMP":"EntityPlayerMP"):
                    entity instanceof ItemEntity?"net.minecraft.entity.item.EntityItem":entity instanceof AbstractHorse?"net.minecraft.entity.passive.EntityHorse":
                    entity instanceof LivingEntity?"net.minecraft.entity.EntityLiving":"net.minecraft.entity.Entity";
            var motion=entity.getDeltaMovement();
            return new HostWorldAccess.Entity(entity.getUUID(),type,entity.getX(),entity.getY(),entity.getZ(),motion.x,motion.y,motion.z,entity.fallDistance,entity.getBbWidth(),entity.getBbHeight(),entity.isShiftKeyDown(),entity.isPushable());
        }).toList();
    }
    public static void apply(Level level,HostWorldAccess.Motion motion) {
        if(!(level instanceof net.minecraft.server.level.ServerLevel server))return;
        Entity entity=server.getEntity(motion.id());if(entity==null||entity.isRemoved())return;
        entity.setDeltaMovement(motion.x(),motion.y(),motion.z());entity.fallDistance=motion.fallDistance();entity.hurtMarked=true;
    }
}
