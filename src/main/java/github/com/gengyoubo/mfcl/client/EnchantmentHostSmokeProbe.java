package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Disposable real registry, command, menu, numeric NBT and original effect acceptance. */
final class EnchantmentHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<?> pending;
    private int stage,frames;
    private Enchantment repair,haste;
    private String pickKey;
    private String world;
    private EnchantmentHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new EnchantmentHostSmokeProbe(content)::tick);}
    @SuppressWarnings("removal")
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new IllegalStateException("Enchantment acceptance timed out at "+stage);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(++frames<12)return;
            if(stage==0){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                world="mfcl-enchantments-"+System.currentTimeMillis();mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("MFCL enchantment acceptance",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1){
                if(mc.player==null||mc.screen!=null)return;
                pending=mc.getSingleplayerServer().submit(()->{try{audit(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Exception failure){throw new IllegalStateException(failure);}});
            }else if(stage==2){
                movement(mc);
                pending=mc.getSingleplayerServer().submit(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var stack=pick();stack.enchant(repair,2);player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.inventoryMenu.broadcastChanges();check(player.server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).containsKey(new ResourceLocation("thaumcraft","outer_lands")),"TC4 adapter failed to register Outer Lands");});
            }else if(stage==3){mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());
            }else if(stage==4){if(mc.getSingleplayerServer()!=null)return;mc.createWorldOpenFlows().loadLevel(mc.screen,world);
            }else if(stage==5){
                if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm&&confirm.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                    for(var child:confirm.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.active&&button.getMessage().equals(net.minecraft.network.chat.CommonComponents.GUI_PROCEED)){button.onPress();return;}
                }
                if(mc.player==null||mc.screen!=null)return;
                check(mc.player.getMainHandItem().getEnchantmentLevel(repair)==2,"Saved repair enchant missing after login");proof.put("outerLandsDatapackAndEnchantmentSurviveWorldReload",true);proof.put("status","PASS");finish(mc);return;
            }
            stage++;frames=0;
        }catch(Throwable failure){com.mojang.logging.LogUtils.getLogger().error("MFCL enchantment acceptance failed",failure);proof.put("status","FAIL");proof.put("failure",failure.toString());try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}}
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private ItemStack pick(){return content.stack(pickKey,50,1,"");}
    private void audit(ServerPlayer player)throws Exception {
        repair=BuiltInRegistries.ENCHANTMENT.get(ResourceLocation.fromNamespaceAndPath("thaumcraft","repair"));haste=BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation("thaumcraft","haste"));
        check(repair!=null&&haste!=null&&repair.getMaxLevel()==2&&haste.getMaxLevel()==3,"Missing registry entries/levels");
        pickKey=content.items().keySet().stream().filter(key->key.toLowerCase(Locale.ROOT).endsWith("itempickthaumium")).findFirst().orElseThrow();
        check(repair.canEnchant(pick())&&!repair.canEnchant(new ItemStack(Items.DIAMOND_PICKAXE)),"Repair eligibility differs from original");
        check(haste.canEnchant(new ItemStack(Items.DIAMOND_BOOTS))&&!haste.canEnchant(new ItemStack(Items.DIAMOND_HELMET))&&haste.canEnchant(content.stack("Thaumcraft:HoverHarness",0,1,"")),"Haste eligibility differs from original");
        check(!repair.isCompatibleWith(Enchantments.UNBREAKING)&&haste.isCompatibleWith(Enchantments.UNBREAKING),"Original compatibility lost");
        proof.put("nativeRegistryLevelsAndEligibility",true);
        for(var enchantment:List.of(repair,haste)){
            var candidate=enchantment==repair?pick():new ItemStack(Items.DIAMOND_BOOTS);boolean found=false;
            for(int seed=0;seed<500&&!found;seed++)found=EnchantmentHelper.selectEnchantment(net.minecraft.util.RandomSource.create(seed),candidate,30,false).stream().anyMatch(data->data.enchantment==enchantment);
            check(found,"Enchantment absent from native table selection: "+enchantment);}
        proof.put("nativeEnchantmentTableSelection",true);
        player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,pick());
        int result=player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),"enchant @s thaumcraft:repair 2");
        check(result==1&&player.getMainHandItem().getEnchantmentLevel(repair)==2,"Native /enchant repair failed");
        var enchanted=player.getMainHandItem().copy();var old=content.gameplayStack(enchanted);
        check(oldLevel(old,"thaumcraft:repair")==2,"Native level absent in original numeric NBT");
        var round=content.gameplayStack(old);check(round.getEnchantmentLevel(repair)==2,"Numeric enchant lost on return");
        var saved=ItemStack.of(round.save(new CompoundTag()));check(saved.getEnchantmentLevel(repair)==2,"Saved enchant lost");
        var legacy=saved.copy();legacy.removeTagKey("Enchantments");legacy.getOrCreateTagElement(ModernContentBridge.STACK_TAG).remove("enchantments_mapped");
        content.enchantments().migrate(legacy);check(legacy.getEnchantmentLevel(repair)==2,"Old saved enchant was not migrated");
        var grindstone=new GrindstoneMenu(7,player.getInventory());grindstone.getSlot(0).set(legacy);grindstone.slotsChanged(grindstone.getSlot(0).container);
        var cleaned=grindstone.getSlot(2).getItem().copy();check(!cleaned.isEmpty()&&cleaned.getEnchantmentLevel(repair)==0,"Native grindstone failed");
        check(oldLevel(content.gameplayStack(cleaned),"thaumcraft:repair")==0,"Grindstone removal resurrected old enchant");
        // Enchanted books go through the same native anvil output as an ordinary item.
        var book=new ItemStack(Items.ENCHANTED_BOOK);EnchantedBookItem.addEnchantment(book,new EnchantmentInstance(repair,2));
        player.experienceLevel=50;var anvil=new AnvilMenu(8,player.getInventory());anvil.getSlot(0).set(pick());anvil.getSlot(1).set(book);anvil.createResult();
        check(anvil.getSlot(2).getItem().getEnchantmentLevel(repair)==2,"Native anvil rejected original eligible item");
        var conflict=pick();conflict.enchant(Enchantments.UNBREAKING,1);anvil.getSlot(0).set(conflict);anvil.createResult();
        check(anvil.getSlot(2).getItem().getEnchantmentLevel(repair)==0,"Native anvil accepted incompatible Unbreaking");
        proof.put("commandBooksAnvilGrindstoneAndSaveRoundTrip",true);
        var forced=enchanted.copy();forced.enchant(Enchantments.UNBREAKING,3);forced.enchant(Enchantments.MENDING,1);
        var changed=content.applyTo(forced,content.gameplayStack(forced));
        check(changed.getEnchantmentLevel(repair)==2&&changed.getEnchantmentLevel(Enchantments.UNBREAKING)==3&&changed.getEnchantmentLevel(Enchantments.MENDING)==1,"Forced/native-only enchant lost during original callback");
        proof.put("forcedCompatibilityAndNativeOnlyEnchantPreserved",true);
        player.setItemInHand(InteractionHand.MAIN_HAND,enchanted.copy());player.getInventory().setItem(1,wand(false));
        repairTick(player);check(player.getMainHandItem().getDamageValue()==50,"Repair worked without vis");
        var charged=wand(true);int before=vis(charged);player.getInventory().setItem(1,charged);repairTick(player);
        check(player.getMainHandItem().getDamageValue()==48,"Repair II did not restore two damage");
        int after=vis(player.getInventory().getItem(1));check(after<before,"Repair did not consume original vis");
        proof.put("originalRepairAndVisCost",Map.of("damageBefore",50,"damageAfter",48,"visBefore",before,"visAfter",after));
        String bootsKey=content.items().keySet().stream().filter(key->key.toLowerCase(Locale.ROOT).endsWith("boots_thaumium")||key.toLowerCase(Locale.ROOT).endsWith("bootsthaumium")).findFirst().orElseThrow();
        var boots=content.stack(bootsKey,50,1,"");boots.enchant(repair,2);player.setItemSlot(EquipmentSlot.FEET,boots);repairTick(player);
        check(player.getItemBySlot(EquipmentSlot.FEET).getDamageValue()==48,"Worn armor was not repaired");
        player.getAbilities().instabuild=true;int damage=player.getMainHandItem().getDamageValue();repairTick(player);check(player.getMainHandItem().getDamageValue()==damage,"Creative repair should not tick");player.getAbilities().instabuild=false;
        proof.put("repairRequiresVisAndRepairsWornArmorAndSkipsCreative",true);
        player.getInventory().clearContent();player.inventoryMenu.broadcastChanges();
    }
    private int oldLevel(LegacyClientData.Stack stack,String key)throws Exception{
        var services=content.services();return services.items().inLegacy(()->(int)services.loader().loadClass("net.minecraft.enchantment.EnchantmentHelper").getMethod("func_77506_a",int.class,services.loader().loadClass("net.minecraft.item.ItemStack")).invoke(null,services.enchantments().id(key),services.items().materialize(stack)));
    }
    private ItemStack wand(boolean charged)throws Exception{
        var services=content.services();var loader=services.loader();var type=loader.loadClass("net.minecraft.item.ItemStack");var aspect=loader.loadClass("thaumcraft.api.aspects.Aspect");
        Object old=services.items().materialize(new LegacyClientData.Stack("Thaumcraft:WandCasting",0,1,"","{rod:\"wood\",cap:\"iron\"}")),item=type.getMethod("func_77973_b").invoke(old);
        if(charged)for(String name:List.of("aer","ignis","aqua","terra","ordo","perditio"))item.getClass().getMethod("storeVis",type,aspect,int.class).invoke(item,old,aspect.getMethod("getAspect",String.class).invoke(null,name),2500);
        return content.gameplayStack(services.items().snapshot(old));
    }
    private int vis(ItemStack stack)throws Exception{
        var services=content.services();var loader=services.loader();var type=loader.loadClass("net.minecraft.item.ItemStack");var aspect=loader.loadClass("thaumcraft.api.aspects.Aspect");Object old=services.items().materialize(content.gameplayStack(stack)),item=type.getMethod("func_77973_b").invoke(old);int total=0;
        for(String name:List.of("aer","ignis","aqua","terra","ordo","perditio"))total+=(int)item.getClass().getMethod("getVis",type,aspect).invoke(item,old,aspect.getMethod("getAspect",String.class).invoke(null,name));return total;
    }
    private void repairTick(ServerPlayer player){int before=player.tickCount;try{player.tickCount=40;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));}finally{player.tickCount=before;}}
    private void movement(Minecraft mc){
        var player=mc.player;var previous=player.getItemBySlot(EquipmentSlot.FEET);float forward=player.zza;boolean ground=player.onGround();float yaw=player.getYRot();
        try{
            player.zza=1;player.setYRot(0);player.setOnGround(true);player.getAbilities().flying=false;var boots=new ItemStack(Items.DIAMOND_BOOTS);boots.enchant(haste,3);player.setItemSlot(EquipmentSlot.FEET,boots);player.setDeltaMovement(0,0,0);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));double grounded=player.getDeltaMovement().z;
            check(Math.abs(grounded-.045)<.00001,"Haste III ground acceleration missing: "+grounded);
            player.setOnGround(false);player.setDeltaMovement(0,0,0);MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));double airborne=player.getDeltaMovement().z;
            check(Math.abs(airborne-.0225)<.00001,"Haste airborne reduction missing: "+airborne);
            player.zza=0;player.setDeltaMovement(0,0,0);MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));check(player.getDeltaMovement().lengthSqr()==0,"Haste moved idle player");
            proof.put("originalHasteGroundAirAndIdleMotion",Map.of("ground",grounded,"air",airborne));
        }finally{player.setItemSlot(EquipmentSlot.FEET,previous);player.zza=forward;player.setOnGround(ground);player.setYRot(yaw);}
    }
    private void finish(Minecraft mc)throws Exception{
        var output=mc.gameDirectory.toPath().resolve("logs/mfcl/enchantment-smoke.json");java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        com.mojang.logging.LogUtils.getLogger().info("MFCL_ENCHANTMENT_SMOKE {}",proof);stage=99;if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
