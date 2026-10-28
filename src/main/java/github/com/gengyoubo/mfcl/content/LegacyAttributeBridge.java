package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Publish captured original attributes so player callbacks and equipment can retain their custom modifiers. */
public final class LegacyAttributeBridge {
    private final Map<String,Attribute> types=new LinkedHashMap<>();
    private final Map<String,String> keys=new LinkedHashMap<>();
    LegacyAttributeBridge(LegacyServices services)throws Exception {
        services.items().inLegacy(()->{
            var type=services.loader().loadClass("net.minecraft.entity.ai.attributes.IAttribute");
            for(var registration:LegacyRegistrations.attributes(services.loader())){
                Object old=registration.value();String name=(String)type.getMethod("func_111108_a").invoke(old);
                if(name.startsWith("generic.")||name.equals("horse.jumpStrength")||name.equals("zombie.spawnReinforcements"))continue;
                double base=(double)type.getMethod("func_111110_b").invoke(old),min=(double)type.getMethod("func_111109_a",double.class).invoke(old,-Double.MAX_VALUE),max=(double)type.getMethod("func_111109_a",double.class).invoke(old,Double.MAX_VALUE);
                String owner=registration.owner().equals("minecraft")?"mfcl":registration.owner();
                String key=LegacyRegistryIds.id(owner+":legacy_attribute/"+name.replace(':','/')).toString();
                var attribute=new RangedAttribute(name,base,min,max).setSyncable((boolean)type.getMethod("func_111111_c").invoke(old));
                if(types.putIfAbsent(name,attribute)!=null)continue;keys.put(name,key);
            }return null;
        });
    }
    public Attribute type(String original){return types.get(original);}
    Map<String,Attribute> types(){return Collections.unmodifiableMap(types);}
    void register(RegisterEvent event){event.register(Registries.ATTRIBUTE,registry->types.forEach((name,value)->registry.register(net.minecraft.resources.ResourceLocation.parse(keys.get(name)),value)));}
    void players(EntityAttributeModificationEvent event){types.values().forEach(attribute->event.add(EntityType.PLAYER,attribute));}
}
