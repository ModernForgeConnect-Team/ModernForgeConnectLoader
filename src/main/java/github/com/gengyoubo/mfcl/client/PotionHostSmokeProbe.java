package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real original potion callbacks, native entities, client sync, resources and disk reload in a disposable world. */
final class PotionHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<?> pending;
    private int stage,frames,started,remaining;
    private String world;
    private PotionHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new PotionHostSmokeProbe(content)::tick);}
    private void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private MobEffect effect(String name){return Objects.requireNonNull(content.potions().effect("thaumcraft:"+name),name);}
    private void server(Minecraft mc,Work work){pending=mc.getSingleplayerServer().submit(()->{try{work.run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Exception failure){throw new IllegalStateException(failure);}});}
    @FunctionalInterface private interface Work{void run(ServerPlayer player)throws Exception;}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Potion acceptance timed out at "+stage);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(++frames<12)return;
            if(stage==0){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                world="mfcl-potions-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("MFCL potion acceptance",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1){
                if(mc.player==null||mc.screen!=null)return;server(mc,this::audit);
            }else if(stage==2){
                check(mc.player.hasEffect(effect("warpward"))&&mc.player.hasEffect(effect("visexhaust")),"Native custom effects did not sync to the client");
                for(String name:List.of("warpward","visexhaust","blurredvision")){
                    var type=effect(name);var sprite=mc.getMobEffectTextures().get(type);
                    check(!sprite.contents().name().getPath().equals("missingno"),"Missing native potion sprite: "+name);
                    check(!type.getDisplayName().getString().equals(type.getDescriptionId()),"Missing potion translation: "+name);
                    var id=BuiltInRegistries.MOB_EFFECT.getKey(type);
                    try(var input=mc.getResourceManager().open(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/mob_effect/"+id.getPath()+".png"))){
                        var icon=javax.imageio.ImageIO.read(input);boolean visible=false;for(int y=0;y<icon.getHeight();y++)for(int x=0;x<icon.getWidth();x++)visible|=(icon.getRGB(x,y)>>>24)!=0;
                        check(visible,"Native potion sprite is an empty atlas crop: "+name);
                    }
                }
                proof.put("nativeClientEffectsNamesAndIcons",true);
                for(String name:List.of("deathgaze","unhunger","sunscorned"))mc.player.addEffect(new MobEffectInstance(effect(name),200,0));
                mc.setScreen(new InventoryScreen(mc.player));
            }else if(stage==3){
                check(LegacyPotionPostEffects.activeEffects().containsAll(List.of("thaumcraft:deathgaze","thaumcraft:blurredvision","thaumcraft:unhunger","thaumcraft:sunscorned")),"Original potion screen filters did not all compile/render");
                proof.put("fourOriginalScreenFiltersRendered",true);
                var image=mc.gameDirectory.toPath().resolve("screenshots/potion-inventory.png");java.nio.file.Files.createDirectories(image.getParent());
                try(var screenshot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(image);}
                mc.screen.onClose();server(mc,player->{
                    remaining=player.getEffect(effect("warpward")).getDuration();
                    check(remaining==1200-(player.tickCount-started),"Custom effect duration advanced more than once per native tick");
                    check(!player.hasEffect(effect("infvisexhaust")),"Expired custom effect was resurrected by a legacy actor");
                    proof.put("nativeDurationAndExpiry",true);
                });
            }else if(stage==4){mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());
            }else if(stage==5){if(mc.getSingleplayerServer()!=null)return;mc.createWorldOpenFlows().loadLevel(mc.screen,world);
            }else if(stage==6){
                if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm){for(var child:confirm.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.active&&button.getMessage().equals(net.minecraft.network.chat.CommonComponents.GUI_PROCEED)){button.onPress();return;}}
                if(mc.player==null||mc.screen!=null)return;server(mc,player->{
                    check(player.hasEffect(effect("warpward"))&&player.getEffect(effect("warpward")).getDuration()<=remaining,"Native potion identity/duration did not survive world reload");
                    check(player.getEffect(effect("visexhaust")).getCurativeItems().isEmpty(),"Original no-milk cure restriction did not survive reload");
                    player.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
                    check(player.hasEffect(effect("visexhaust")),"Milk removed an originally incurable potion");
                    player.removeEffect(effect("visexhaust"));
                    content.players().callback(player,actor->{check(!(boolean)actor.getClass().getMethod("func_82165_m",int.class).invoke(actor,content.services().potions().id("thaumcraft:visexhaust")),"Native removal was not reflected in the old actor");});
                    proof.put("saveReloadCurativesAndNativeRemoval",true);
                });
            }else if(stage==7){proof.put("status","PASS");finish(mc);return;}
            stage++;frames=0;
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("stage",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL potion acceptance failed",failure);try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}}
    }
    private void audit(ServerPlayer player)throws Exception {
        var services=content.services();var loader=services.loader();var oldEffect=loader.loadClass("net.minecraft.potion.PotionEffect");
        check(services.potions().entries().stream().filter(entry->entry.key().startsWith("thaumcraft:")).count()==9,"Expected the nine potions actually present in TC4 4.2.3.5");
        for(var entry:services.potions().entries())check(BuiltInRegistries.MOB_EFFECT.get(LegacyRegistryIds.id(entry.key()))==content.potions().effect(entry.id()),"Native effect mapping mismatch: "+entry.key());
        proof.put("registeredOriginalEffects",services.potions().entries());
        var level=player.serverLevel();player.removeAllEffects();player.getInventory().clearContent();
        int taint=services.potions().id("thaumcraft:fluxtaint");
        content.players().callback(player,actor->{
            Object effect=oldEffect.getConstructor(int.class,int.class,int.class,boolean.class).newInstance(taint,80,1,true);
            actor.getClass().getMethod("func_70690_d",oldEffect).invoke(actor,effect);
            check((boolean)actor.getClass().getMethod("func_82165_m",int.class).invoke(actor,taint),"Old add/query disagreed within one callback");
        });
        check(player.hasEffect(effect("fluxtaint"))&&player.getEffect(effect("fluxtaint")).getAmplifier()==1,"Original addPotionEffect missed the native player");
        content.players().callback(player,actor->actor.getClass().getMethod("func_82170_o",int.class).invoke(actor,taint));
        check(!player.hasEffect(effect("fluxtaint")),"Original removePotionEffect missed the native player");
        proof.put("originalPlayerAddReadAndRemove",true);
        var pig=EntityType.PIG.create(level);pig.moveTo(player.getX()+2,player.getY(),player.getZ());level.addFreshEntity(pig);float hp=pig.getHealth();
        effect("fluxtaint").applyEffectTick(pig,0);check(pig.getHealth()<hp,"Original taint damage did not reach a native mob");
        proof.put("originalTaintDamagesNativeMob",true);
        player.addEffect(new MobEffectInstance(effect("deathgaze"),80,0));
        player.setYRot(-90);player.setXRot(15);
        content.players().callback(player,actor->loader.loadClass("thaumcraft.common.lib.WarpEvents").getMethod("checkDeathGaze",loader.loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(null,actor));
        check(pig.hasEffect(MobEffects.WITHER),"Original death gaze did not wither a native mob in view");
        player.removeEffect(effect("deathgaze"));proof.put("originalDeathGazeWithersNativeMob",true);
        var other=EntityType.PIG.create(level);other.moveTo(pig.getX()+1,pig.getY(),pig.getZ());level.addFreshEntity(other);
        pig.addEffect(new MobEffectInstance(effect("infvisexhaust"),600,1));effect("infvisexhaust").applyEffectTick(pig,1);
        check(other.hasEffect(effect("infvisexhaust"))&&other.getEffect(effect("infvisexhaust")).getAmplifier()==0,"Original infectious potion did not spread to nearby native entities");
        check(pig.getEffect(effect("infvisexhaust")).getAmplifier()==1,"Infection treated its source as an unaffected mirror");proof.put("originalInfectionAndMirrorQueries",true);
        pig.discard();other.discard();player.removeAllEffects();
        float exhaustion=player.getFoodData().getExhaustionLevel();effect("unhunger").applyEffectTick(player,1);
        check(Math.abs(player.getFoodData().getExhaustionLevel()-exhaustion-.05f)<.0001f,"Original unnatural hunger did not add native exhaustion exactly once");proof.put("originalUnnaturalHungerExhaustion",true);
        player.addEffect(new MobEffectInstance(effect("unhunger"),1200,2));
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish(player,new ItemStack(Items.ROTTEN_FLESH),0,ItemStack.EMPTY));
        var hunger=player.getEffect(effect("unhunger"));check(hunger!=null&&hunger.getAmplifier()==1&&hunger.getDuration()==600,"Original rotten-flesh cure did not lower hunger strength/duration");
        player.removeEffect(effect("unhunger"));proof.put("originalUnnaturalHungerFoodCure",true);
        level.setDayTime(18000);player.setHealth(10);
        content.players().callback(player,actor->{
            Object oldWorld=services.worlds().bound(level);var random=(java.util.Random)loader.loadClass("net.minecraft.world.World").getField("field_73012_v").get(oldWorld);random.setSeed(0);
            var entry=services.potions().entries().stream().filter(value->value.key().equals("thaumcraft:sunscorned")).findFirst().orElseThrow();services.potions().tick(entry,actor,0);
        });
        check(player.getHealth()>10,"Original Sun Scorned dark-area healing did not reach the native player");proof.put("originalSunScornedHealing",true);
        var feet=player.blockPosition();level.removeBlock(feet,false);
        content.players().callback(player,actor->{
            Object oldWorld=services.worlds().bound(level);var random=(java.util.Random)loader.loadClass("net.minecraft.world.World").getField("field_73012_v").get(oldWorld);
            long seed=0;while(new java.util.Random(seed).nextInt(15)!=0)seed++;random.setSeed(seed);
            services.potions().tick(services.potions().entries().stream().filter(value->value.key().equals("thaumcraft:thaumarhia")).findFirst().orElseThrow(),actor,0);
        });
        check(content.legacyBlock(level.getBlockState(feet)).legacyKey().equals("Thaumcraft:blockFluxGoo"),"Original Thaumarhia did not place flux goo in the native world");level.removeBlock(feet,false);proof.put("originalThaumarhiaPlacesGoo",true);
        var potion=content.gameplayStack(new LegacyClientData.Stack("minecraft:potion",0,1,"","{CustomPotionEffects:[{Id:"+taint+"b,Duration:120,Amplifier:1b}]}"));
        check(net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(potion).stream().anyMatch(effect->effect.getEffect()==effect("fluxtaint")),"Old custom potion item kept the wrong numeric effect ID");
        check(content.gameplayStack(potion).tag().contains("Id:"+taint+"b"),"Potion item did not round-trip to the original ID");proof.put("customPotionItemIdRoundTrip",true);
        content.players().callback(player,actor->{
            Object effect=oldEffect.getConstructor(int.class,int.class,int.class,boolean.class).newInstance(services.potions().id("thaumcraft:visexhaust"),1200,0,false);
            ((List<?>)oldEffect.getMethod("getCurativeItems").invoke(effect)).clear();actor.getClass().getMethod("func_70690_d",oldEffect).invoke(actor,effect);
        });
        player.addEffect(new MobEffectInstance(effect("warpward"),1200,0));player.addEffect(new MobEffectInstance(effect("blurredvision"),600,0));player.addEffect(new MobEffectInstance(effect("infvisexhaust"),1,0));started=player.tickCount;
    }
    private void finish(Minecraft mc)throws Exception {
        var path=mc.gameDirectory.toPath().resolve("logs/mfcl/potion-smoke.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        com.mojang.logging.LogUtils.getLogger().info("MFCL_POTION_SMOKE: {}",proof);stage=99;if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
