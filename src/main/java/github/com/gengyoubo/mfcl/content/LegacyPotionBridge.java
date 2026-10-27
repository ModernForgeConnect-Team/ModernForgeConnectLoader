package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.registries.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Native status effects delegate behavior to the original potion with live entity/player state. */
public final class LegacyPotionBridge {
    private final ModernContentBridge content;
    private final LegacyServices services;
    private final Map<Integer,MobEffect> byOld=new HashMap<>();
    private final Map<MobEffect,Integer> toOld=new IdentityHashMap<>();
    private final Map<String,net.minecraft.resources.ResourceLocation> shaders;
    LegacyPotionBridge(ModernContentBridge content)throws Exception{
        this.content=content;services=content.services();services.potions().discover();
        var resources=new LinkedHashMap<String,net.minecraft.resources.ResourceLocation>();services.potions().screenFilters().forEach((key,value)->resources.put(key,LegacyPotionShaderPack.location(value)));shaders=Collections.unmodifiableMap(resources);
    }
    void register(RegisterEvent event){event.register(Registries.MOB_EFFECT,registry->{
        for(int id=1;id<=23;id++){MobEffect effect=BuiltInRegistries.MOB_EFFECT.get(net.minecraft.resources.ResourceLocation.parse(services.potions().name(id)));byOld.put(id,effect);toOld.put(effect,id);}
        for(var entry:services.potions().entries()){
            var effect=new Original(entry);registry.register(LegacyRegistryIds.id(entry.key()),effect);byOld.put(entry.id(),effect);toOld.put(effect,entry.id());
        }
    });}
    public MobEffect effect(int oldId){return byOld.get(oldId);}
    public MobEffect effect(String key){Integer id=services.potions().id(key);return id==null?null:effect(id);}
    public Integer oldId(MobEffect effect){return toOld.get(effect);}
    public Map<String,net.minecraft.resources.ResourceLocation> shaders(){return shaders;}
    public List<LegacyPlayerBody.Effect> snapshot(LivingEntity entity){
        var values=new ArrayList<LegacyPlayerBody.Effect>();for(var effect:entity.getActiveEffects()){
            Integer id=oldId(effect.getEffect());if(id!=null){
                var cures=new ArrayList<LegacyClientData.Stack>();boolean mapped=true;
                for(var cure:effect.getCurativeItems())try{cures.add(LegacyPotions.cureStack(content.gameplayStack(cure)));}
                catch(Exception unmapped){mapped=false;break;}
                values.add(new LegacyPlayerBody.Effect(id,effect.getDuration(),effect.getAmplifier(),effect.isAmbient(),mapped?cures:null));
            }
        }return List.copyOf(values);
    }
    public void apply(LivingEntity target,LegacyPlayerBody.Effect state){
        MobEffect effect=effect(state.id());if(effect==null){LegacyDiagnostics.missingMapping("potion "+state.id(),"Original potion has no registered native effect");return;}
        var instance=new MobEffectInstance(effect,state.duration(),state.amplifier(),state.ambient(),true);
        if(state.curatives()!=null)instance.setCurativeItems(new ArrayList<>(state.curatives().stream().map(content::gameplayStack).toList()));
        target.addEffect(instance);
        var active=target.getEffect(effect);if(active!=null&&state.curatives()!=null)active.setCurativeItems(new ArrayList<>(instance.getCurativeItems()));
    }
    @FunctionalInterface private interface Action{void run(Object world,Object actor)throws Exception;}
    private void invoke(LivingEntity target,Action action){
        if(target.level().isClientSide)return;
        try{
            if(target instanceof Player player)content.players().callback(player,actor->action.run(services.worlds().bound(player.level()),actor));
            else content.logicTransaction(target.level(),target.blockPosition(),()->services.items().inLegacy(()->{
                Object world=services.worlds().bound(target.level()),actor=services.potions().entity(world,target.getUUID());
                if(actor!=null)action.run(world,actor);return null;
            }),false);
        }catch(Exception failure){throw new IllegalStateException("Original potion callback failed for "+target.getUUID(),failure);}
    }
    private final class Original extends MobEffect {
        private final LegacyPotions.Entry entry;
        Original(LegacyPotions.Entry entry){
            super(entry.harmful()?MobEffectCategory.HARMFUL:MobEffectCategory.BENEFICIAL,entry.color());this.entry=entry;
            for(var modifier:entry.modifiers()){
                var attribute=HostPlayerAttributes.type(modifier.attribute());
                if(attribute!=null)addAttributeModifier(attribute,modifier.id().toString(),modifier.amount(),net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.fromValue(modifier.operation()));
                else LegacyDiagnostics.missingMapping("potion attribute "+modifier.attribute(),"Original potion "+entry.key()+" references an unregistered native attribute");
            }
        }
        @Override public double getAttributeModifierValue(int amplifier,net.minecraft.world.entity.ai.attributes.AttributeModifier modifier){
            try{return services.potions().modifierValue(entry,modifier.getId(),amplifier);}catch(Exception failure){throw new IllegalStateException("Original potion attribute modifier failed: "+entry.key(),failure);}
        }
        @Override public String getDescriptionId(){return entry.name();}
        @Override public boolean isInstantenous(){return entry.instant();}
        @Override public boolean isDurationEffectTick(int duration,int amplifier){
            try{return services.potions().ready(entry,duration,amplifier);}catch(Exception failure){throw new IllegalStateException("Original potion readiness failed: "+entry.key(),failure);}
        }
        @Override public void applyEffectTick(LivingEntity target,int amplifier){invoke(target,(world,actor)->services.potions().tick(entry,actor,amplifier));}
        @Override public void applyInstantenousEffect(Entity source,Entity indirect,LivingEntity target,int amplifier,double strength){
            invoke(target,(world,actor)->services.potions().instant(entry,source==null?null:services.potions().entity(world,source.getUUID()),actor,amplifier,strength));
        }
    }
    /** Export 18px old inventory-atlas icons as native mob-effect sprites. */
    void generateIcons()throws Exception {
        for(var entry:services.potions().entries()){
            if(entry.icon()<0)continue;
            var resource=net.minecraft.resources.ResourceLocation.parse(entry.texture());
            try(var input=services.loader().getResourceAsStream("assets/"+resource.getNamespace()+"/"+resource.getPath())){
                if(input==null)continue;var atlas=javax.imageio.ImageIO.read(input);int x=entry.icon()%8*18,y=198+entry.icon()/8*18;
                if(atlas==null||x+18>atlas.getWidth()||y+18>atlas.getHeight())continue;
                var id=LegacyRegistryIds.id(entry.key());var path=content.pack().directory().resolve("assets/"+id.getNamespace()+"/textures/mob_effect/"+id.getPath()+".png");
                java.nio.file.Files.createDirectories(path.getParent());javax.imageio.ImageIO.write(atlas.getSubimage(x,y,18,18),"PNG",path.toFile());
            }
        }
        var shaderPack=new LegacyPotionShaderPack(services,content.pack());for(var resource:services.potions().screenFilters().values())shaderPack.export(resource);
    }
}
