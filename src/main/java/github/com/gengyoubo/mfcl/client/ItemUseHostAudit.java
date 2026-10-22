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
                if(index>=cases.size()){stage=3;return;}
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
                    var pig=net.minecraft.world.entity.EntityType.PIG.create(p.serverLevel());pig.moveTo(p.getX()+1,p.getY(),p.getZ());p.serverLevel().addFreshEntity(pig);
                    var result=p.getMainHandItem().interactLivingEntity(p,pig,InteractionHand.MAIN_HAND);
                    report.put("goldenLassoEntityScenario",Map.of("result",result.toString(),"targetRemoved",pig.isRemoved(),"heldTag",String.valueOf(p.getMainHandItem().getTag())));pig.discard();
                    p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,content.stack("ExtraUtilities:angelRing",0,1,""));return true;
                });stage=11;frames=0;
            }else if(stage==11&&pending.isDone()&&++frames>30){
                pending=mc.getSingleplayerServer().submit(()->{var p=player(mc);require(p.getAbilities().mayfly,"Angel ring did not grant native survival flight");report.put("angelRingGrantsFlight",true);p.getInventory().clearContent();return true;});stage=12;frames=0;
            }else if(stage==12&&pending.isDone()&&++frames>35){
                pending=mc.getSingleplayerServer().submit(()->{require(!player(mc).getAbilities().mayfly,"Removed angel ring retained flight");report.put("angelRingRemovalRevokesFlight",true);return true;});stage=13;
            }else if(stage==13&&pending.isDone()){
                report.put("status",rows.stream().anyMatch(row->String.valueOf(row.get("status")).equals("FAIL"))?"FAILURES_FOUND":"COMPLETED");finish(mc,null);
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
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
        row.put("additionalScenarios",List.of(item.entity()?"entity interaction not yet bridged":"",item.weapon()?"weapon hit not yet bridged":"",item.finish()?"completion needs held-use scenario":"").stream().filter(s->!s.isEmpty()).toList());
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
            if(item.tick()) {
                fixture(player,item);var services=content.services();Object world=services.worlds().bound(player.serverLevel());
                var source=new LegacyTiles.Placer(player.getUUID(),player.getGameProfile().getName(),player.getX(),player.getY(),player.getZ(),0,0,true,false);
                Object actor=content.itemUse().clientActor(world,source,null);
                var inventory=new ArrayList<LegacyClientData.Stack>(Collections.nCopies(36,null));inventory.set(0,new LegacyClientData.Stack(item.key(),item.meta(),1,"",""));services.playerInventory().write(actor,inventory);
                content.itemUse().inventoryTick(world,source,0,player.tickCount,true);actions.put("inventory","CALLBACK_RETURNED");
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
