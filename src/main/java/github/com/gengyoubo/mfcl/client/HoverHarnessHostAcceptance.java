package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.concurrent.*;

/** Original fuel GUI plus /hat-style equipment persisted through a real disconnect/reload. */
final class HoverHarnessHostAcceptance {
    private final ModernContentBridge content;
    private final Map<String,Object> proof;
    private final String world;
    private CompletableFuture<?> pending;
    private int stage,frames;
    HoverHarnessHostAcceptance(ModernContentBridge content,Map<String,Object> proof,String world){this.content=content;this.proof=proof;this.world=world;}
    boolean tick(Minecraft mc)throws Exception {
        if(pending!=null){if(!pending.isDone())return false;pending.join();pending=null;}
        if(++frames<12)return false;
        switch(stage){
            case 0 -> server(mc,player->{player.getInventory().clearContent();player.getInventory().selected=0;player.getInventory().setItem(0,content.stack("Thaumcraft:HoverHarness",0,1,""));player.getInventory().setItem(1,chargedJar());player.getInventory().setItem(2,new ItemStack(Items.STICK));player.inventoryMenu.broadcastChanges();});
            case 1 -> {mc.player.getInventory().selected=0;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
            case 2 -> {
                if(!(mc.screen instanceof LegacyDeviceScreen screen)||!screen.getMenu().receivedContents())return false;
                require(screen.originalGui().getClass().getSimpleName().equals("GuiHoverHarness"),"Harness right click equipped instead of opening its original GUI");
                require(ClientContainerBridge.active().commands(screen).stream().anyMatch(c->c instanceof LegacyGuiGraphics.Texture t&&t.resource().endsWith("guihoverharness.png")),"Harness background did not render");
                modelGuards(mc);proof.put("harnessOriginalGuiAndHatModelGuards",true);
                pending=mc.getSingleplayerServer().submit(()->{try{Thread.sleep(600);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}});
                click(mc,29,0,ClickType.PICKUP);click(mc,0,0,ClickType.PICKUP);
                require(mc.player.containerMenu.getCarried().isEmpty()&&!mc.player.containerMenu.getSlot(0).getItem().isEmpty()&&!pending.isDone(),"Harness insertion did not predict during server delay");
                proof.put("harnessFuelPredictionDuring600msStall",true);
            }
            case 3 -> server(mc,player->{require(fuel(player.getInventory().getItem(0))==64,"Fuel was not committed before close");proof.put("harnessFuelCommittedBeforeClose",true);});
            case 4 -> {
                click(mc,30,0,ClickType.PICKUP);click(mc,0,0,ClickType.PICKUP);require(mc.player.containerMenu.getCarried().is(Items.STICK),"Harness accepted a stick");click(mc,30,0,ClickType.PICKUP);
                click(mc,0,2,ClickType.SWAP);require(!mc.player.containerMenu.getSlot(0).getItem().is(Items.STICK),"Number key bypassed fuel validation");
                click(mc,29,0,ClickType.SWAP);require(content.renderSnapshot(mc.player.containerMenu.getSlot(28).getItem()).key().equals("Thaumcraft:HoverHarness"),"Number key moved the open harness");
                click(mc,0,0,ClickType.PICKUP);click(mc,29,0,ClickType.PICKUP);require(mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Authoritative empty fuel slot was ignored");
            }
            case 5 -> server(mc,player->{require(fuel(player.getInventory().getItem(0))==0,"Removed fuel remained in harness NBT");proof.put("harnessFuelRemovalAndNumberKeyRestrictions",true);});
            case 6 -> {click(mc,29,0,ClickType.QUICK_MOVE);require(!mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Fuel shift insertion did not predict");}
            case 7 -> {mc.player.getInventory().selected=2;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);mc.screen.onClose();}
            case 8 -> server(mc,player->{
                require(fuel(player.getInventory().getItem(0))==64&&player.getInventory().getItem(2).is(Items.STICK),"Harness close wrote the wrong held slot");
                var harness=player.getInventory().removeItemNoUpdate(0);player.setItemSlot(EquipmentSlot.HEAD,harness);player.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);proof.put("harnessSelectionChangeCloseKeepsOpeningSlot",true);
            });
            case 9 -> {
                require(content.renderSnapshot(mc.player.getItemBySlot(EquipmentSlot.HEAD)).key().equals("Thaumcraft:HoverHarness"),"Hat equipment did not synchronize");
                modelGuards(mc);mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());
            }
            case 10 -> {if(mc.getSingleplayerServer()!=null)return false;mc.createWorldOpenFlows().loadLevel(mc.screen,world);}
            case 11 -> {
                // The disposable TC4 world uses experimental registries; accept only this known reload prompt.
                if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm&&confirm.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())) {
                    for(var child:confirm.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.active&&button.getMessage().equals(net.minecraft.network.chat.CommonComponents.GUI_PROCEED)){button.onPress();return false;}
                }
                if(mc.player==null||mc.screen!=null)return false;
                require(fuel(mc.player.getItemBySlot(EquipmentSlot.HEAD))==64,"Hat equipment or fuel did not survive login");modelGuards(mc);
                proof.put("harnessHatSurvivesWorldReloadAndLogin",true);
                server(mc,player->{require(fuel(player.getItemBySlot(EquipmentSlot.HEAD))==64,"Server lost saved hat fuel");player.setItemSlot(EquipmentSlot.CHEST,player.getItemBySlot(EquipmentSlot.HEAD));player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);});
            }
            case 12 -> {require(fuel(mc.player.getItemBySlot(EquipmentSlot.CHEST))==64,"Correct chest equipment lost fuel");proof.put("harnessNormalChestEquipmentStillTicks",true);return true;}
        }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_HARNESS_STAGE {}",stage);stage++;frames=0;return false;
    }
    private ItemStack chargedJar()throws Exception {
        return content.services().items().inLegacy(()->{
            var loader=content.services().loader();Object item=loader.loadClass("thaumcraft.common.config.ConfigItems").getField("itemJarFilled").get(null);
            Object stack=loader.loadClass("net.minecraft.item.ItemStack").getConstructor(loader.loadClass("net.minecraft.item.Item"),int.class,int.class).newInstance(item,1,0);
            var aspect=loader.loadClass("thaumcraft.api.aspects.Aspect");var list=loader.loadClass("thaumcraft.api.aspects.AspectList");Object aspects=list.getConstructor().newInstance();
            list.getMethod("add",aspect,int.class).invoke(aspects,aspect.getField("ENERGY").get(null),64);item.getClass().getMethod("setAspects",stack.getClass(),list).invoke(item,stack,aspects);
            return content.gameplayStack(content.services().items().snapshot(stack));
        });
    }
    private int fuel(ItemStack harness)throws Exception {
        return content.services().items().inLegacy(()->{
            Object stack=content.services().items().materialize(content.gameplayStack(harness));Object tag=stack.getClass().getField("field_77990_d").get(stack);if(tag==null)return 0;
            Object saved=tag.getClass().getMethod("func_74775_l",String.class).invoke(tag,"jar");Object jar=stack.getClass().getMethod("func_77949_a",tag.getClass()).invoke(null,saved);if(jar==null)return 0;
            Object item=jar.getClass().getMethod("func_77973_b").invoke(jar);Object aspects=item.getClass().getMethod("getAspects",jar.getClass()).invoke(item,jar);if(aspects==null)return 0;
            var aspect=content.services().loader().loadClass("thaumcraft.api.aspects.Aspect");return (int)aspects.getClass().getMethod("getAmount",aspect).invoke(aspects,aspect.getField("ENERGY").get(null));
        });
    }
    private void modelGuards(Minecraft mc)throws Exception {
        var services=content.services();services.items().inLegacy(()->{
            var loader=services.loader();Object actor=content.itemUse().clientActor(services.worlds().bound(mc.level),new LegacyTiles.Placer(mc.player.getUUID(),mc.player.getName().getString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),0,0),null);
            Object inventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(actor);Object[] armor=(Object[])inventory.getClass().getField("field_70460_b").get(inventory);Object[] saved=armor.clone();
            Object stack=services.items().materialize(content.gameplayStack(content.stack("Thaumcraft:HoverHarness",0,1,"")));Object item=stack.getClass().getMethod("func_77973_b").invoke(stack);
            try {
                armor[3]=stack;armor[2]=null;
                var model=item.getClass().getMethod("getArmorModel",loader.loadClass("net.minecraft.entity.EntityLivingBase"),stack.getClass(),int.class);
                require(model.invoke(item,actor,stack,0)==null,"Harness supplied a chest model for a hat");
                item.getClass().getMethod("onArmorTick",loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.entity.player.EntityPlayer"),stack.getClass()).invoke(item,services.worlds().bound(mc.level),actor,stack);
                Object original=model.invoke(item,actor,stack,1);require(original!=null,"Normal harness model was disabled");
                var draw=original.getClass().getMethod("func_78088_a",loader.loadClass("net.minecraft.entity.Entity"),float.class,float.class,float.class,float.class,float.class,float.class);
                draw.invoke(original,actor,0f,0f,0f,0f,0f,.0625f);
                armor[2]=services.items().materialize(content.gameplayStack(new ItemStack(Items.DIAMOND_CHESTPLATE)));draw.invoke(original,actor,0f,0f,0f,0f,0f,.0625f);
            }finally{System.arraycopy(saved,0,armor,0,armor.length);}
            return null;
        });
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void click(Minecraft mc,int slot,int button,ClickType type){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player);}
    @FunctionalInterface private interface Action{void run(ServerPlayer player)throws Exception;}
    private void server(Minecraft mc,Action action){pending=mc.getSingleplayerServer().submit(()->{try{action.run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Exception error){throw new IllegalStateException(error);}});}
}
