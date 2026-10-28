package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Pull native vanilla attributes before original item code, and commit only modifiers it actually changed. */
public final class LegacyPlayerAttributes {
    private LegacyPlayerAttributes(){}
    private record State(HostWorldAccess host,UUID id,List<HostWorldAccess.Attribute> before){}
    private static final Map<Object,State> STATES=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    public static void initialize(Object actor) throws Exception {
        var player=actor.getClass().getClassLoader().loadClass("net.minecraft.entity.player.EntityPlayer");
        var init=player.getDeclaredMethod("func_110147_ax");init.setAccessible(true);init.invoke(actor);
        pull(actor);
    }
    public static void pull(Object actor) throws Exception {
        var loader=actor.getClass().getClassLoader();var entity=loader.loadClass("net.minecraft.entity.Entity");
        var host=LegacyWorlds.hostOrNull(entity.getField("field_70170_p").get(actor));
        UUID id=(UUID)entity.getMethod("func_110124_au").invoke(actor);
        var values=host==null||id==null?null:host.attributes(id);if(values==null){STATES.remove(actor);return;}
        Object map=actor.getClass().getMethod("func_110140_aT").invoke(actor);
        var type=loader.loadClass("net.minecraft.entity.ai.attributes.IAttributeInstance");
        var modifier=loader.loadClass("net.minecraft.entity.ai.attributes.AttributeModifier");
        for(var value:values){
            Object attribute=map.getClass().getMethod("func_111152_a",String.class).invoke(map,value.name());
            if(attribute==null)for(var candidate:LegacyRegistrations.attributes(loader)){
                var original=loader.loadClass("net.minecraft.entity.ai.attributes.IAttribute");
                if(value.name().equals(original.getMethod("func_111108_a").invoke(candidate.value()))){attribute=map.getClass().getMethod("func_111150_b",original).invoke(map,candidate.value());break;}
            }
            if(attribute==null)continue;
            type.getMethod("func_111128_a",double.class).invoke(attribute,value.base());type.getMethod("func_142049_d").invoke(attribute);
            for(var m:value.modifiers()){
                Object original=modifier.getConstructor(UUID.class,String.class,double.class,int.class).newInstance(m.id(),m.name(),m.amount(),m.operation());
                modifier.getMethod("func_111168_a",boolean.class).invoke(original,m.permanent());
                type.getMethod("func_111121_a",modifier).invoke(attribute,original);
            }
        }
        STATES.put(actor,new State(host,id,values));
    }
    public static void push(Object actor) throws Exception {
        var state=STATES.get(actor);if(state==null||state.host().clientSide())return;
        var loader=actor.getClass().getClassLoader();var type=loader.loadClass("net.minecraft.entity.ai.attributes.IAttributeInstance");
        var modifier=loader.loadClass("net.minecraft.entity.ai.attributes.AttributeModifier");
        Object map=actor.getClass().getMethod("func_110140_aT").invoke(actor);
        var next=new ArrayList<HostWorldAccess.Attribute>();
        for(var prior:state.before()){
            Object instance=map.getClass().getMethod("func_111152_a",String.class).invoke(map,prior.name());if(instance==null){next.add(prior);continue;}
            var modifiers=new ArrayList<HostWorldAccess.Modifier>();
            for(Object m:(Collection<?>)type.getMethod("func_111122_c").invoke(instance))modifiers.add(new HostWorldAccess.Modifier(
                    (UUID)modifier.getMethod("func_111167_a").invoke(m),(String)modifier.getMethod("func_111166_b").invoke(m),
                    (double)modifier.getMethod("func_111164_d").invoke(m),(int)modifier.getMethod("func_111169_c").invoke(m),(boolean)modifier.getMethod("func_111165_e").invoke(m)));
            next.add(new HostWorldAccess.Attribute(prior.name(),(double)type.getMethod("func_111125_b").invoke(instance),List.copyOf(modifiers)));
        }
        state.host().attributes(state.id(),state.before(),next);
    }
}
