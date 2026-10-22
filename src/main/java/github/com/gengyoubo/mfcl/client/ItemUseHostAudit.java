package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Native server callback survey in its own disposable world. PASS means callback ran, not every scenario was covered. */
final class ItemUseHostAudit {
    private record Case(String key,int meta,boolean block,boolean air,boolean use,boolean first,boolean tick,boolean entity,boolean weapon,boolean finish){}
    private final ModernContentBridge content;
    private final List<Case> cases=new ArrayList<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final Map<String,Object> report=new LinkedHashMap<>();
    private long deadline;
    private CompletableFuture<?> pending;
    private int stage,index,frames;
    private String lastScreen;
    private BlockPos anchor;
    private UUID combatTarget;
    private long wingFramesBefore;
    private final java.util.concurrent.atomic.AtomicBoolean screenshotSaved=new java.util.concurrent.atomic.AtomicBoolean();
    private ItemUseHostAudit(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(new ItemUseHostAudit(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(deadline==0)deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);
            String screenName=mc.screen==null?"none":mc.screen.getClass().getName();
            if(!screenName.equals(lastScreen)){lastScreen=screenName;com.mojang.logging.LogUtils.getLogger().info("MFCL_ITEM_AUDIT_SCREEN stage={} {}",stage,screenName);}
            if(pending!=null&&pending.isCompletedExceptionally())pending.join();
            if(System.nanoTime()>deadline)throw new IllegalStateException("Item audit timed out at "+index);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                profile();
                mc.createWorldOpenFlows().createFreshLevel("mfcl-item-audit-"+System.currentTimeMillis(),new LevelSettings("MFCL item audit",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null){
                pending=mc.getSingleplayerServer().submit(()->{var p=player(mc);anchor=p.blockPosition().offset(0,2,0);return true;});stage=2;
            }else if(stage==2&&(pending==null||pending.isDone())){
                // The scenario stages below use ExtraUtilities fixtures; without it the per-item survey is the whole audit.
                if(index>=cases.size()){stage=cases.stream().anyMatch(c->c.key().startsWith("ExtraUtilities:"))?3:98;return;}
                Case item=cases.get(index++);
                pending=mc.getSingleplayerServer().submit(()->{audit(player(mc),item);save(mc);return true;});
            }else if(stage==3){
                pending=mc.getSingleplayerServer().submit(()->{
                    var p=player(mc);fixture(p,cases.stream().filter(c->c.key().equals("ExtraUtilities:golden_bag")).findFirst().orElseThrow());
                    var bag=p.getMainHandItem();p.getInventory().setItem(0,ItemStack.EMPTY);p.getInventory().selected=5;p.getInventory().setItem(5,bag);p.getInventory().setItem(9,new ItemStack(Items.STONE,3));p.inventoryMenu.broadcastChanges();return true;
                });stage=4;frames=0;
            }else if(stage==4&&pending.isDone()&&++frames>20){
                mc.player.getInventory().selected=5;
                require(mc.player.getMainHandItem().is(content.items().get("ExtraUtilities:golden_bag")),"Bag fixture did not reach client");
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=5;frames=0;
            }else if(stage==5&&mc.screen instanceof LegacyDeviceScreen screen&&++frames>25){
                require(screen.getMenu().slots.size()==90,"Bag client slot layout differs");report.put("bagClientScreenAndValidity",true);
                var slot=screen.getMenu().slots.stream().filter(s->s.getItem().is(Items.STONE)).findFirst().orElseThrow();
                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,slot.index,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);stage=6;frames=0;
            }else if(stage==6&&++frames>10){
                require(mc.player.containerMenu.getCarried().is(Items.STONE),"Bag inventory pickup failed");
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);stage=7;frames=0;
            }else if(stage==7&&++frames>10){
                require(mc.player.containerMenu.getSlot(0).getItem().is(Items.STONE)&&mc.player.containerMenu.getSlot(0).getItem().getCount()==3,"Bag storage click failed");
                mc.player.closeContainer();stage=8;frames=0;
            }else if(stage==8&&++frames>10){
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=9;frames=0;
            }else if(stage==9&&mc.screen instanceof LegacyDeviceScreen&&++frames>10){
                require(mc.player.containerMenu.getSlot(0).getItem().is(Items.STONE)&&mc.player.containerMenu.getSlot(0).getItem().getCount()==3,"Bag contents did not survive reopening");
                report.put("bagNativeSlotClicksAndReopen",true);mc.player.closeContainer();stage=10;frames=0;
            }else if(stage==10&&++frames>10){
                pending=mc.getSingleplayerServer().submit(()->{
                    var p=player(mc);fixture(p,cases.stream().filter(c->c.key().equals("ExtraUtilities:golden_lasso")).findFirst().orElseThrow());
                    var pig=net.minecraft.world.entity.EntityType.PIG.create(p.serverLevel());pig.moveTo(p.getX()+1,p.getY(),p.getZ());pig.setCustomName(net.minecraft.network.chat.Component.literal("Captured Pig"));pig.setAge(-1234);pig.setHealth(3);pig.getPersistentData().putString("mfcl_capture_fixture","preserve");p.serverLevel().addFreshEntity(pig);
                    var pigId=pig.getUUID();p.setGameMode(GameType.SURVIVAL);
                    var result=p.getMainHandItem().interactLivingEntity(p,pig,InteractionHand.MAIN_HAND);
                    require(result.consumesAction()&&pig.isRemoved(),"Golden lasso did not capture native pig");
                    var target=p.blockPosition().offset(1,-1,1);p.serverLevel().setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    var released=p.gameMode.useItemOn(p,p.serverLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false));
                    require(released.consumesAction(),"Golden lasso did not release captured pig");
                    require(p.serverLevel().getEntity(pigId) instanceof net.minecraft.world.entity.animal.Pig,"Released pig was not restored as a native entity");
                    var restored=(net.minecraft.world.entity.animal.Pig)p.serverLevel().getEntity(pigId);
                    require(restored.getHealth()==3&&restored.getAge()==-1234&&restored.getName().getString().equals("Captured Pig")&&restored.getPersistentData().getString("mfcl_capture_fixture").equals("preserve"),"Lasso lost native entity state");
                    restored.discard();report.put("goldenLassoCaptureReleasePreservesNativeState",true);
                    soul(p);
                    p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,content.stack("ExtraUtilities:angelRing",0,1,""));return true;
                });stage=11;frames=0;
            }else if(stage==11&&pending.isDone()&&++frames>30){
                pending=mc.getSingleplayerServer().submit(()->{var p=player(mc);require(p.getAbilities().mayfly,"Angel ring did not grant native survival flight");report.put("angelRingGrantsFlight",true);p.getInventory().clearContent();return true;});stage=12;frames=0;
            }else if(stage==12&&pending.isDone()&&++frames>35){
                pending=mc.getSingleplayerServer().submit(()->{require(!player(mc).getAbilities().mayfly,"Removed angel ring retained flight");report.put("angelRingRemovalRevokesFlight",true);return true;});stage=13;
            }else if(stage==13&&pending.isDone()){
                pending=mc.getSingleplayerServer().submit(()->{
                    var p=player(mc);shears(p);p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,content.stack("ExtraUtilities:ethericsword",0,1,""));
                    var victim=net.minecraft.world.entity.EntityType.PIG.create(p.serverLevel());victim.moveTo(p.getX()+1,p.getY(),p.getZ());victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);victim.setHealth(200);p.serverLevel().addFreshEntity(victim);combatTarget=victim.getUUID();return true;
                });stage=14;frames=0;
            }else if(stage==14&&pending.isDone()&&++frames>20){
                pending=mc.getSingleplayerServer().submit(()->{
                    var p=player(mc);var victim=(net.minecraft.world.entity.LivingEntity)p.serverLevel().getEntity(combatTarget);
                    require(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)>1,"Original sword damage attribute was missing");
                    p.attack(victim);require(victim.getHealth()<199,"Native sword attack did not apply original damage");victim.discard();report.put("swordNativeDamageAndCallback",true);
                    p.setItemInHand(InteractionHand.MAIN_HAND,content.stack("ExtraUtilities:destructionpickaxe",0,1,""));return true;
                });stage=15;frames=0;
            }else if(stage==15&&pending.isDone()&&++frames>15){
                pending=mc.getSingleplayerServer().submit(()->{
                    var p=player(mc);var stone=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();var tool=p.getMainHandItem();
                    require(tool.getDestroySpeed(stone)>1&&tool.isCorrectToolForDrops(stone),"Original pickaxe efficiency/harvest capability missing");
                    var target=p.blockPosition().offset(1,0,1);p.serverLevel().setBlockAndUpdate(target,stone);
                    require(p.gameMode.destroyBlock(target)&&p.serverLevel().getBlockState(target).isAir(),"Native pickaxe mining failed");
                    require(p.getMainHandItem().getDamageValue()==0,"Destruction pickaxe lost its original no-wear mining behavior");report.put("pickaxeHarvestEfficiencyAndOriginalDurability",true);
                    p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,content.stack("ExtraUtilities:angelRing",4,1,""));return true;
                });stage=16;frames=0;
            }else if(stage==16&&pending.isDone()&&++frames>30){
                var wings=ExtraUtilitiesWings.capture(content,mc.player,.5f);int count=wings.stream().mapToInt(primitive->primitive.vertices().size()).sum();
                require(count>=8,"Golden wing style did not generate original geometry");report.put("goldenWingVertices",count);report.put("goldenWingTextures",wings.stream().map(primitive->primitive.texture()).distinct().toList());
                require(wings.stream().allMatch(primitive->primitive.texture().equals("extrautils:textures/wing_golden.png")),"Golden ring selected the wrong original texture");
                wingFramesBefore=ExtraUtilitiesWings.renderedFrames;mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);mc.player.setXRot(10);stage=17;frames=0;
            }else if(stage==17&&++frames>20){
                require(ExtraUtilitiesWings.renderedFrames>wingFramesBefore,"Original wings were captured but native player render layer never drew them");report.put("goldenWingNativeRenderFrames",ExtraUtilitiesWings.renderedFrames-wingFramesBefore);
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-golden-wings.png",mc.getMainRenderTarget(),message->screenshotSaved.set(true));stage=18;frames=0;
            }else if(stage==18&&screenshotSaved.get()&&++frames>3){
                report.put("goldenWingScreenshot","screenshots/mfcl-golden-wings.png");
                report.put("status",rows.stream().anyMatch(row->String.valueOf(row.get("status")).equals("FAIL"))?"FAILURES_FOUND":"COMPLETED");finish(mc,null);
            }else if(stage==98){
                report.put("status",rows.stream().anyMatch(row->String.valueOf(row.get("status")).equals("FAIL"))?"FAILURES_FOUND":"COMPLETED");finish(mc,null);
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private void soul(ServerPlayer p) {
        p.getInventory().clearContent();p.getInventory().selected=0;
        double initial=p.getMaxHealth();p.setItemInHand(InteractionHand.MAIN_HAND,content.stack("ExtraUtilities:mini-soul",2,1,""));
        p.getMainHandItem().inventoryTick(p.serverLevel(),p,0,true);
        require(p.getMaxHealth()<initial,"Split soul did not reduce native maximum health");
        var saved=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).save();
        require(!saved.getList("Modifiers",10).isEmpty(),"Soul health modifier was not persistent");
        p.getMainHandItem().getItem().use(p.serverLevel(),p,InteractionHand.MAIN_HAND);
        require(Math.abs(p.getMaxHealth()-initial)<.001&&p.getMainHandItem().isEmpty(),"Soul consumption did not restore native health cap");
        report.put("soulHealthModifierAndRestoration",true);
    }
    private void shears(ServerPlayer p){
        p.getInventory().clearContent();p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,content.stack("ExtraUtilities:shears",0,1,""));
        var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(p.serverLevel());sheep.moveTo(p.getX()+1,p.getY(),p.getZ());sheep.setColor(net.minecraft.world.item.DyeColor.BLUE);p.serverLevel().addFreshEntity(sheep);
        var result=p.getMainHandItem().interactLivingEntity(p,sheep,InteractionHand.MAIN_HAND);
        require(result.consumesAction()&&sheep.isSheared(),"Precision shears did not update native sheep");
        require(p.getMainHandItem().getDamageValue()>0,"Original shearing callback did not wear precision shears");
        int count=p.getInventory().items.stream().filter(stack->stack.is(Items.BLUE_WOOL)).mapToInt(ItemStack::getCount).sum();
        require(count>0,"Precision shears did not collect the correct wool into inventory");
        p.getMainHandItem().interactLivingEntity(p,sheep,InteractionHand.MAIN_HAND);
        require(p.getInventory().items.stream().filter(stack->stack.is(Items.BLUE_WOOL)).mapToInt(ItemStack::getCount).sum()==count,"Repeated shearing duplicated wool");
        sheep.discard();report.put("precisionShearsNativeStateAndPickup",true);
    }
    private ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    private void profile() throws Exception {
        var loader=content.services().loader();Class<?> item=loader.loadClass("net.minecraft.item.Item"),stack=loader.loadClass("net.minecraft.item.ItemStack"),world=loader.loadClass("net.minecraft.world.World"),player=loader.loadClass("net.minecraft.entity.player.EntityPlayer"),entity=loader.loadClass("net.minecraft.entity.Entity"),living=loader.loadClass("net.minecraft.entity.EntityLivingBase");
        for(var entry:content.declarations().items()){
            Object original=content.services().items().materialize(new LegacyClientData.Stack(entry.key(),entry.variants().get(0).metadata(),1,"",""));
            Class<?> type=stack.getMethod("func_77973_b").invoke(original).getClass();
            boolean air=type.getMethod("func_77659_a",stack,world,player).getDeclaringClass()!=item;
            Class<?>[] use={stack,player,world,int.class,int.class,int.class,int.class,float.class,float.class,float.class};
            boolean on=type.getMethod("func_77648_a",use).getDeclaringClass()!=item,first=type.getMethod("onItemUseFirst",use).getDeclaringClass()!=item;
            boolean tick=type.getMethod("func_77663_a",stack,world,entity,int.class,boolean.class).getDeclaringClass()!=item;
            boolean interaction=type.getMethod("func_111207_a",stack,player,living).getDeclaringClass()!=item;
            boolean weapon=type.getMethod("func_77644_a",stack,living,living).getDeclaringClass()!=item;
            boolean finish=type.getMethod("func_77654_b",stack,world,player).getDeclaringClass()!=item;
            for(var variant:entry.variants())cases.add(new Case(entry.key(),variant.metadata(),entry.blockKey()!=null,air,on,first,tick,interaction,weapon,finish));
        }
        report.put("scope","Original item callback classification plus native server air use, stone-target use and inventory update; entity/tool/completion scenarios are listed separately, not counted as functional passes.");report.put("catalogVariants",cases.size());report.put("cases",rows);
    }
    private void fixture(ServerPlayer player,Case item) {
        player.closeContainer();player.stopUsingItem();player.getInventory().clearContent();player.getInventory().selected=0;
        var level=player.serverLevel();
        for(var p:BlockPos.betweenClosed(anchor.offset(-2,-1,-2),anchor.offset(2,3,2)))level.setBlock(p,p.getY()<anchor.getY()?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        player.teleportTo(level,anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,0,0);
        player.setItemInHand(InteractionHand.MAIN_HAND,content.stack(item.key(),item.meta(),1,""));player.inventoryMenu.broadcastChanges();
    }
    private void audit(ServerPlayer player,Case item) {
        var row=new LinkedHashMap<String,Object>();row.put("key",item.key());row.put("metadata",item.meta());row.put("block",item.block());
        var actions=new LinkedHashMap<String,Object>();row.put("actions",actions);
        row.put("additionalScenarios",List.of(item.entity()?"entity interaction requires target-specific scenario":"",item.weapon()?"weapon hit requires combat scenario":"").stream().filter(s->!s.isEmpty()).toList());
        com.mojang.logging.LogUtils.getLogger().info("MFCL_ITEM_AUDIT_CASE {}@{}",item.key(),item.meta());
        try {
            if(item.air()) {
                fixture(player,item);var result=player.getMainHandItem().getItem().use(player.serverLevel(),player,InteractionHand.MAIN_HAND);
                actions.put("air",result.getResult().toString());if(result.getResult()==InteractionResult.FAIL)row.put("status","FAIL");
                if(player.containerMenu instanceof LegacyDeviceMenu menu)actions.put("menu",menu.descriptor().toString());
            }
            if(item.use()||item.first()) {
                fixture(player,item);var target=anchor.offset(1,0,0);player.serverLevel().setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                var result=player.gameMode.useItemOn(player,player.serverLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false));
                actions.put("stone",result.toString());if(result==InteractionResult.FAIL)row.put("status","FAIL");
            }
            if(item.block()) {
                // Side attachment (vines, ladders, wall blocks) is decided by the clicked face, not the item metadata.
                fixture(player,item);var target=anchor.offset(1,0,0);player.serverLevel().setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                var result=player.gameMode.useItemOn(player,player.serverLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(target).add(.5,0,0),Direction.EAST,target,false));
                var placed=player.serverLevel().getBlockState(target.east());
                actions.put("side",result+(placed.isAir()?"":":"+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(placed.getBlock())+"@"+(placed.hasProperty(ModernContentBridge.METADATA)?placed.getValue(ModernContentBridge.METADATA):"-")));
                if(result==InteractionResult.FAIL)row.put("status","FAIL");
            }
            if(item.tick()) {
                fixture(player,item);var services=content.services();Object world=services.worlds().bound(player.serverLevel());
                var source=new LegacyTiles.Placer(player.getUUID(),player.getGameProfile().getName(),player.getX(),player.getY(),player.getZ(),0,0,true,false);
                Object actor=content.itemUse().clientActor(world,source,null);
                var inventory=new ArrayList<LegacyClientData.Stack>(Collections.nCopies(36,null));inventory.set(0,new LegacyClientData.Stack(item.key(),item.meta(),1,"",""));services.playerInventory().write(actor,inventory);
                content.itemUse().inventoryTick(world,source,0,player.tickCount,true);actions.put("inventory","CALLBACK_RETURNED");
            }
            if(item.finish()) {
                // Completion of a held use (drink/eat); the original onEaten decides what remains in hand.
                fixture(player,item);var before=player.getMainHandItem().copy();
                var after=player.getMainHandItem().getItem().finishUsingItem(player.getMainHandItem(),player.serverLevel(),player);
                actions.put("finish",after.isEmpty()?"CONSUMED":ItemStack.matches(before,after)?"UNCHANGED":"CHANGED:"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(after.getItem()));
            }
            row.putIfAbsent("status",actions.isEmpty()?"NO_DIRECT_USE":"CALLBACKS_RETURNED");
        }catch(Throwable failure){while(failure.getCause()!=null)failure=failure.getCause();row.put("status","FAIL");row.put("failure",failure.toString());}
        finally{player.closeContainer();player.stopUsingItem();player.getInventory().clearContent();}
        rows.add(row);
    }
    private void save(Minecraft mc){try{var out=mc.gameDirectory.toPath().resolve("logs/mfcl/item-use-audit.json");java.nio.file.Files.createDirectories(out.getParent());java.nio.file.Files.writeString(out,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private void finish(Minecraft mc,Throwable failure){
        if(failure!=null){report.put("status","FAIL");report.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_ITEM_AUDIT_FAIL",failure);}
        stage=99;save(mc);com.mojang.logging.LogUtils.getLogger().info("MFCL_ITEM_AUDIT_RESULT {} cases, {}",rows.size(),report.get("status"));
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
