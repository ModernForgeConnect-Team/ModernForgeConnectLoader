package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;

final class HostPlayerAttributes {
    static Attribute type(String name){return TYPES.get(name);}
    private static final Map<String,Attribute> TYPES=Map.of(
            "generic.maxHealth",Attributes.MAX_HEALTH,"generic.followRange",Attributes.FOLLOW_RANGE,
            "generic.knockbackResistance",Attributes.KNOCKBACK_RESISTANCE,"generic.movementSpeed",Attributes.MOVEMENT_SPEED,
            "generic.attackDamage",Attributes.ATTACK_DAMAGE);
    static List<HostWorldAccess.Attribute> read(Player player){
        var values=new ArrayList<HostWorldAccess.Attribute>();
        TYPES.forEach((name,type)->{
            var instance=player.getAttribute(type);if(instance==null)return;
            var permanent=new HashSet<UUID>();for(var tag:instance.save().getList("Modifiers",10))permanent.add(((net.minecraft.nbt.CompoundTag)tag).getUUID("UUID"));
            var modifiers=instance.getModifiers().stream().map(m->new HostWorldAccess.Modifier(m.getId(),m.getName(),m.getAmount(),m.getOperation().toValue(),permanent.contains(m.getId()))).toList();
            values.add(new HostWorldAccess.Attribute(name,instance.getBaseValue(),modifiers));
        });return List.copyOf(values);
    }
    static void apply(Player player,List<HostWorldAccess.Attribute> before,List<HostWorldAccess.Attribute> after){
        for(var next:after){
            var prior=before.stream().filter(a->a.name().equals(next.name())).findFirst().orElse(null);
            var type=TYPES.get(next.name());if(prior==null||type==null)continue;
            var instance=player.getAttribute(type);if(instance==null)continue;
            if(prior.base()!=next.base())instance.setBaseValue(next.base());
            var ids=new HashSet<UUID>();prior.modifiers().forEach(m->ids.add(m.id()));next.modifiers().forEach(m->ids.add(m.id()));
            for(UUID id:ids){
                var old=prior.modifiers().stream().filter(m->m.id().equals(id)).findFirst().orElse(null);
                var value=next.modifiers().stream().filter(m->m.id().equals(id)).findFirst().orElse(null);
                if(Objects.equals(old,value))continue;
                instance.removeModifier(id);
                if(value!=null){
                    var modifier=new AttributeModifier(id,value.name(),value.amount(),AttributeModifier.Operation.fromValue(value.operation()));
                    if(value.permanent())instance.addPermanentModifier(modifier);else instance.addTransientModifier(modifier);
                }
            }
        }
        if(player.getHealth()>player.getMaxHealth())player.setHealth(player.getMaxHealth());
    }
}
