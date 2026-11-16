package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/** TC4 brainy zombie with native movement and targeting, and an original counterpart for rendering and drops. */
public final class ModernBrainyZombie extends Zombie implements IEntityAdditionalSpawnData {
    private static final String ORIGINAL="thaumcraft.common.entities.monster.EntityBrainyZombie";
    private Object legacy;
    private byte[] saved;

    public ModernBrainyZombie(EntityType<? extends Zombie> type,Level level) { super(type,level); xpReward=8; }
    public static AttributeSupplier.Builder attributes() { return Zombie.createAttributes().add(Attributes.MAX_HEALTH,25).add(Attributes.ATTACK_DAMAGE,5); }
    static ModernBrainyZombie host(ModernContentBridge content,ServerLevel level,Object original) throws Exception {
        var host=new ModernBrainyZombie(content.brainyZombieType(),level);
        var hosting=content.services().entityHosting();var state=hosting.state(original);
        host.legacy=original;host.moveTo(state.x(),state.y(),state.z(),state.yaw(),state.pitch());
        hosting.adopt(original,content.services().worlds().bound(level),host.getUUID(),host.getId());
        hosting.nativeMirror(original);
        boolean baby=(boolean)content.services().loader().loadClass("net.minecraft.entity.monster.EntityZombie").getMethod("func_70631_g_").invoke(original);
        host.setBaby(baby);return host;
    }
    public Object legacyOrNull() { return legacy; }
    private boolean ensureLegacy(ModernContentBridge content) throws Exception {
        if(legacy!=null)return true;
        if(saved==null)return false;
        Object world=content.services().worlds().findBound(level());if(world==null)return false;
        legacy=content.services().entityHosting().restore(world,ORIGINAL,saved);
        content.services().entityHosting().adopt(legacy,world,getUUID(),getId());saved=null;return true;
    }
    @Override public void tick() {
        super.tick();var content=ModernContentBridge.active();if(content==null)return;
        try {
            if(!ensureLegacy(content))return;
            var hosting=content.services().entityHosting();
            if(level().isClientSide) hosting.follow(content.services().worlds().bound(level()),legacy,getX(),getY(),getZ(),getYRot(),getXRot());
            else {var velocity=getDeltaMovement();hosting.nativeMirror(legacy);hosting.nativeLivingState(legacy,getX(),getY(),getZ(),velocity.x,velocity.y,velocity.z,getYRot(),getXRot(),getHealth());}
        } catch(Exception | LinkageError failure) { LogUtils.getLogger().error("MFCL cannot synchronize brainy zombie",failure);legacy=null;saved=null; }
    }
    @Override protected void dropFromLootTable(DamageSource source,boolean recentlyHit) { /* Original TC4 drops below. */ }
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit) {
        var content=ModernContentBridge.active();if(content==null||level().isClientSide)return;
        try {
            if(ensureLegacy(content))content.logicTransaction(level(),blockPosition(),()->{
                content.services().entityHosting().brainyDrops(legacy,recentlyHit,looting);return null;
            },true);
        }catch(Exception | LinkageError failure){LogUtils.getLogger().error("MFCL cannot drop brainy zombie loot",failure);}
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);byte[] data=saved;var content=ModernContentBridge.active();
        try {if(legacy!=null&&content!=null)data=content.services().entityHosting().save(legacy);}
        catch(Exception failure){LogUtils.getLogger().error("MFCL cannot save brainy zombie",failure);}
        if(data!=null)tag.putByteArray("LegacyNbt",data);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {super.readAdditionalSaveData(tag);saved=tag.contains("LegacyNbt")?tag.getByteArray("LegacyNbt"):null;legacy=null;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void writeSpawnData(FriendlyByteBuf buffer) {
        byte[] data=saved;var content=ModernContentBridge.active();
        try {if(legacy!=null&&content!=null)data=content.services().entityHosting().save(legacy);}
        catch(Exception failure){LogUtils.getLogger().error("MFCL cannot describe brainy zombie",failure);}
        buffer.writeByteArray(data==null?new byte[0]:data);
    }
    @Override public void readSpawnData(FriendlyByteBuf buffer){byte[] data=buffer.readByteArray();saved=data.length==0?null:data;}
}
