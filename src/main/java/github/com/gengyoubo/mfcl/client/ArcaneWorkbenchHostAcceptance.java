package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Delayed native slot packets, close/reopen and real save/logout/login for the original arcane table. */
final class ArcaneWorkbenchHostAcceptance {
    private final ModernContentBridge content;
    private final Map<String,Object> proof;
    private final String world;
    private final List<Integer> inputs=new ArrayList<>();
    private CompletableFuture<?> pending;
    private BlockPos pos;
    private LegacyClientData.Stack expectedWand,expectedOutput;
    private int comparisonPhase;
    private int warpPhase;
    private int stage,frames,input;
    ArcaneWorkbenchHostAcceptance(ModernContentBridge content,Map<String,Object> proof,String world){this.content=content;this.proof=proof;this.world=world;}
    boolean tick(Minecraft mc)throws Exception {
        if(pending!=null){if(!pending.isDone())return false;pending.join();pending=null;}
        if(++frames<12)return false;
        switch(stage){
            case 0 -> server(mc,this::setup);
            case 1 -> {if(mc.screen!=null)return false;open(mc);}
            case 2 -> {
                if(!ready(mc))return false;
                if(input<inputs.size()){
                    int grid=inputs.get(input++);click(mc,38+grid);click(mc,2+grid);frames=0;return false;
                }
                captureGui(mc,"withoutWand",20);
                click(mc,11);click(mc,1);
            }
            case 3 -> {
                if(comparisonPhase==0){
                    require(ready(mc)&&mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Deconstruction table accepted an undercharged wand");
                    require(insufficientVis(mc),"Undercharged deconstruction GUI omitted the vis warning");
                    captureGui(mc,"iron20",22);
                    proof.put("deconstructionRejects20VisWithIronCaps",true);
                    server(mc,player->captureServer(player,"iron20",2000,2200,false));
                    comparisonPhase=1;frames=0;return false;
                }
                if(comparisonPhase==1){
                    click(mc,1);click(mc,11);click(mc,13);click(mc,1);
                    comparisonPhase=2;frames=0;return false;
                }
                if(comparisonPhase==2){
                    captureGui(mc,"gold20",20);
                    require(!insufficientVis(mc)&&!mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Gold-capped wand with 20 vis did not allow the recipe");
                    server(mc,player->captureServer(player,"gold20",2000,2000,true));
                    comparisonPhase=3;frames=0;return false;
                }
                if(comparisonPhase==3){
                    if(!warpChecks(mc)){frames=0;return false;}
                    click(mc,1);click(mc,13);click(mc,12);click(mc,1);
                    comparisonPhase=4;frames=0;return false;
                }
                captureGui(mc,"iron25",22);
                require(!insufficientVis(mc),"Fully charged deconstruction GUI still reports insufficient vis");
                proof.put("delayedArcaneClientSlots",mc.player.containerMenu.slots.stream().limit(11).map(slot->slot.getItem().toString()).toList());
                server(mc,player->{
                    captureServer(player,"iron25",2500,2200,true);
                    var menu=(LegacyDeviceMenu)player.containerMenu;
                    proof.put("delayedArcaneServerSlots",menu.slots.stream().limit(11).map(slot->slot.getItem().toString()).toList());
                    require(!menu.getSlot(0).getItem().isEmpty(),"Server lost arcane output between ticks");
                    expectedOutput=content.containerStack(menu.getSlot(0).getItem());
                });
            }
            case 4 -> {require(ready(mc)&&!mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Delayed arcane slot packets produced no client output");click(mc,0);}
            case 5 -> {
                require(same(expectedOutput,content.containerStack(mc.player.containerMenu.getCarried())),"Native arcane output pickup failed");
                server(mc,player->{
                    var tile=(LegacyBlockEntity)player.serverLevel().getBlockEntity(pos);
                    for(int i=0;i<9;i++)require(content.services().inventories().get(tile.legacy(),i)==null,"Native arcane ingredient not consumed");
                    expectedWand=content.services().inventories().get(tile.legacy(),10);
                    require(expectedWand!=null&&expectedWand.tag().contains("perditio:300"),"Delayed deconstruction craft did not charge 22 entropy vis exactly once");
                    proof.put("arcaneCraftThroughDelayedNativePackets",true);
                    proof.put("deconstructionWandAfterCraft",expectedWand);
                });
            }
            case 6 -> mc.screen.onClose();
            case 7 -> {if(mc.screen!=null)return false;open(mc);}
            case 8 -> {
                if(!ready(mc))return false;
                require(same(expectedWand,content.containerStack(mc.player.containerMenu.getSlot(1).getItem())),"Reopening workbench lost wand");
                proof.put("arcaneWandSurvivesMenuReopen",true);
                // Disconnect with the menu still open to exercise its close callback during logout.
                mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());
            }
            case 9 -> {if(mc.getSingleplayerServer()!=null)return false;mc.createWorldOpenFlows().loadLevel(mc.screen,world);}
            case 10 -> {
                if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm&&confirm.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                    for(var child:confirm.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.active&&button.getMessage().equals(net.minecraft.network.chat.CommonComponents.GUI_PROCEED)){button.onPress();return false;}
                }
                if(mc.player==null||mc.screen!=null)return false;
                server(mc,player->{
                    var tile=(LegacyBlockEntity)player.serverLevel().getBlockEntity(pos);
                    require(tile!=null&&same(expectedWand,content.services().inventories().get(tile.legacy(),10)),"Saved workbench lost wand or vis after world reload");
                    proof.put("arcaneWandSurvivesWorldReloadAndLogin",true);
                });
            }
            case 11 -> open(mc);
            case 12 -> {
                if(!ready(mc))return false;
                require(same(expectedWand,content.containerStack(mc.player.containerMenu.getSlot(1).getItem())),"Reloaded client wand slot differs from server");
                click(mc,1);
            }
            case 13 -> {
                require(same(expectedWand,content.containerStack(mc.player.containerMenu.getCarried())),"Saved wand cannot be retrieved");
                proof.put("arcaneSavedWandRetrievableWithVisIntact",true);mc.screen.onClose();
            }
            case 14 -> {return true;}
        }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_ARCANE_STAGE {}",stage);stage++;frames=0;return false;
    }
    private boolean ready(Minecraft mc){return mc.screen instanceof LegacyDeviceScreen screen&&screen.getMenu().receivedContents();}
    private boolean insufficientVis(Minecraft mc){return ClientContainerBridge.active().commands(mc.screen).stream().anyMatch(command->command instanceof LegacyGuiGraphics.Text text&&text.text().equals("Insufficient vis"));}
    private void captureGui(Minecraft mc,String label,int expectedCost)throws Exception {
        captureGui(mc,label,expectedCost,true);
    }
    private void captureGui(Minecraft mc,String label,int expectedCost,boolean verify)throws Exception {
        var texts=ClientContainerBridge.active().commands(mc.screen).stream().filter(command->command instanceof LegacyGuiGraphics.Text).map(command->((LegacyGuiGraphics.Text)command).text()).distinct().toList();
        var result=new LinkedHashMap<String,Object>();result.put("renderedText",texts);result.put("expectedDisplayedCost",expectedCost);result.put("insufficientVis",insufficientVis(mc));
        result.put("nativeWand",content.containerStack(mc.player.containerMenu.getSlot(1).getItem()));
        var gui=((LegacyDeviceScreen)mc.screen).originalGui();
        var tileField=gui.getClass().getDeclaredField("tileEntity");tileField.setAccessible(true);Object tile=tileField.get(gui);
        var inventoryField=gui.getClass().getDeclaredField("ip");inventoryField.setAccessible(true);Object inventory=inventoryField.get(gui);
        Object actor=inventory.getClass().getField("field_70458_d").get(inventory);
        result.put("originalGuiWand",content.services().inventories().get(tile,10));
        if(content.services().inventories().get(tile,10)!=null)result.put("originalClientCost",cost(tile,actor));
        var path=mc.gameDirectory.toPath().resolve("screenshots/arcane-deconstructor-"+label+".png");java.nio.file.Files.createDirectories(path.getParent());
        try(var screenshot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(path);}
        result.put("screenshot",path.toAbsolutePath().toString());proof.put("deconstructionGui_"+label,result);
        if(verify)require(texts.contains(Integer.toString(expectedCost)),"Deconstruction GUI did not render cost "+expectedCost+" for "+label+": "+texts);
    }
    /** Change native effects while this same original menu stays open, then also check reopening it. */
    private boolean warpChecks(Minecraft mc)throws Exception {
        switch(warpPhase){
            case 0 -> server(mc,player->content.withApiPlayer(player,actor->{try{
                var loader=content.services().loader();Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null),knowledge=proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy);String name=LegacyObjects.playerName(actor);
                for(var entry:Map.of("WarpPerm",30,"WarpSticky",30,"WarpTemp",10,"WarpCounter",10000).entrySet())knowledge.getClass().getMethod("set"+entry.getKey(),String.class,int.class).invoke(knowledge,name,entry.getValue());
                proof.put("deconstructionWarpValues",List.of(30,30,10));return null;
            }catch(Exception failure){throw new IllegalStateException(failure);}}));
            case 1 -> observeWarpCost(mc,"gold20_highWarp",20,2000,true);
            case 2 -> server(mc,player->player.addEffect(new net.minecraft.world.effect.MobEffectInstance(Objects.requireNonNull(content.potions().effect("thaumcraft:visexhaust")),600,0)));
            case 3 -> observeWarpCost(mc,"gold20_visExhaustI",22,2200,false);
            case 4 -> {click(mc,0);}
            case 5 -> {
                require(mc.player.containerMenu.getCarried().isEmpty(),"Vis exhaustion allowed undercharged output pickup");
                server(mc,player->{var tile=((LegacyBlockEntity)player.serverLevel().getBlockEntity(pos)).legacy();for(int slot:inputs)require(content.services().inventories().get(tile,slot)!=null,"Rejected exhausted craft consumed an ingredient");});
            }
            case 6 -> mc.screen.onClose();
            case 7 -> {if(mc.screen!=null)return false;open(mc);}
            case 8 -> {if(!ready(mc))return false;observeWarpCost(mc,"gold20_visExhaustI_reopened",22,2200,false);}
            case 9 -> server(mc,player->{player.removeEffect(content.potions().effect("thaumcraft:visexhaust"));player.addEffect(new net.minecraft.world.effect.MobEffectInstance(Objects.requireNonNull(content.potions().effect("thaumcraft:infvisexhaust")),600,3));});
            case 10 -> observeWarpCost(mc,"gold20_infectiousIV",28,2800,false);
            case 11 -> server(mc,player->player.addEffect(new net.minecraft.world.effect.MobEffectInstance(Objects.requireNonNull(content.potions().effect("thaumcraft:visexhaust")),600,1)));
            case 12 -> observeWarpCost(mc,"gold20_bothExhaustions",28,2800,false);
            case 13 -> server(mc,net.minecraft.server.level.ServerPlayer::removeAllEffects);
            case 14 -> observeWarpCost(mc,"gold20_effectsRemoved",20,2000,true);
            case 15 -> server(mc,player->player.addEffect(new net.minecraft.world.effect.MobEffectInstance(Objects.requireNonNull(content.potions().effect("thaumcraft:visexhaust")),35,0)));
            case 16 -> observeWarpCost(mc,"gold20_shortExhaustI",22,2200,false);
            case 17 -> {if(mc.player.hasEffect(content.potions().effect("thaumcraft:visexhaust")))return false;observeWarpCost(mc,"gold20_exhaustionExpired",20,2000,true);}
            case 18 -> {proof.put("arcaneWarpEffectsDisplayAndServerCostStaySynchronized",true);return true;}
        }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_ARCANE_WARP_STAGE {}",warpPhase);warpPhase++;return false;
    }
    private void observeWarpCost(Minecraft mc,String label,int displayed,int required,boolean output)throws Exception {
        captureGui(mc,label,displayed,false);
        var texts=ClientContainerBridge.active().commands(mc.screen).stream().filter(command->command instanceof LegacyGuiGraphics.Text).map(command->((LegacyGuiGraphics.Text)command).text()).toList();
        server(mc,player->{captureServer(player,label,2000,required,output);require(texts.contains(Integer.toString(displayed)),"Warp GUI cost differs for "+label+": "+texts);});
    }
    private void captureServer(ServerPlayer player,String label,int stored,int required,boolean canCraft)throws Exception {
        Object tile=((LegacyBlockEntity)player.serverLevel().getBlockEntity(pos)).legacy();var menu=(LegacyDeviceMenu)player.containerMenu;
        var result=content.services().items().inLegacy(()->{try(var state=content.services().playerState().enter(content.services().worlds().bound(player.serverLevel()))){return cost(tile,menu.originalPlayer());}});
        result.put("outputAvailable",!menu.getSlot(0).getItem().isEmpty());proof.put("deconstructionServer_"+label,result);
        require(result.get("storedRawVis").equals(stored)&&result.get("requiredRawVis").equals(required),"Server vis cost differs for "+label+": "+result);
        require(!menu.getSlot(0).getItem().isEmpty()==canCraft,"Server output gate differs for "+label);
    }
    private Map<String,Object> cost(Object tile,Object actor)throws Exception {
        var services=content.services();return services.items().inLegacy(()->{
            var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
            Object entropy=aspectType.getMethod("getAspect",String.class).invoke(null,"perditio");
            Object stack=tile.getClass().getMethod("func_70301_a",int.class).invoke(tile,10),wand=stackType.getMethod("func_77973_b").invoke(stack);
            Object aspects=loader.loadClass("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager").getMethod("findMatchingArcaneRecipeAspects",loader.loadClass("net.minecraft.inventory.IInventory"),loader.loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(null,tile,actor);
            int base=(int)aspects.getClass().getMethod("getAmount",aspectType).invoke(aspects,entropy);
            float modifier=(float)wand.getClass().getMethod("getConsumptionModifier",stackType,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),aspectType,boolean.class).invoke(wand,stack,actor,entropy,true);
            var result=new LinkedHashMap<String,Object>();result.put("baseCost",base);result.put("modifier",modifier);result.put("displayedCost",base*modifier);
            result.put("storedRawVis",wand.getClass().getMethod("getVis",stackType,aspectType).invoke(wand,stack,entropy));result.put("requiredRawVis",(int)(base*100*modifier));return result;
        });
    }
    private void click(Minecraft mc,int slot){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.PICKUP,mc.player);}
    private void open(Minecraft mc){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}
    private void server(Minecraft mc,Checked action){pending=mc.getSingleplayerServer().submit(()->{try{action.run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Exception failure){throw new IllegalStateException(failure);}});}
    @FunctionalInterface private interface Checked{void run(ServerPlayer player)throws Exception;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static boolean same(LegacyClientData.Stack a,LegacyClientData.Stack b){return a==null||b==null?a==b:a.key().equals(b.key())&&a.metadata()==b.metadata()&&a.count()==b.count()&&a.tag().equals(b.tag());}
    private void setup(ServerPlayer player)throws Exception {
        player.closeContainer();player.getInventory().clearContent();player.getInventory().selected=8;
        var services=content.services();var loader=services.loader();
        Object recipe=services.items().inLegacy(()->{
            for(Object candidate:(List<?>)loader.loadClass("thaumcraft.api.ThaumcraftApi").getMethod("getCraftingRecipes").invoke(null))
                if(candidate.getClass().getSimpleName().equals("ShapedArcaneRecipe")&&candidate.getClass().getMethod("getResearch").invoke(candidate).equals("DECONSTRUCTOR"))return candidate;
            throw new IllegalStateException("DECONSTRUCTOR arcane fixture missing");
        });
        content.withApiPlayer(player,actor->{try{
            Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null),knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
            @SuppressWarnings("unchecked") var completed=(Map<String,ArrayList<String>>)knowledge.getClass().getField("researchCompleted").get(knowledge);
            var learned=completed.computeIfAbsent(player.getName().getString(),ignored->new ArrayList<>());if(!learned.contains("DECONSTRUCTOR"))learned.add("DECONSTRUCTOR");services.playerState().synchronize(actor);return null;
        }catch(Exception failure){throw new IllegalStateException(failure);}});
        pos=player.blockPosition().east(2);player.serverLevel().setBlockAndUpdate(pos,content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockTable",15)));
        Object[] values=(Object[])recipe.getClass().getMethod("getInput").invoke(recipe);int width=recipe.getClass().getField("width").getInt(recipe);
        for(int i=0;i<values.length;i++){
            Object value=values[i];if(value instanceof List<?> choices)value=choices.get(0);if(value==null)continue;
            var stack=services.items().snapshot(value);int grid=i/width*3+i%width;inputs.add(grid);
            player.getInventory().setItem(grid,content.gameplayStack(new LegacyClientData.Stack(stack.key(),stack.metadata()==32767?0:stack.metadata(),1,"",stack.tag())));
        }
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
        Object wand=services.items().materialize(new LegacyClientData.Stack("Thaumcraft:WandCasting",0,1,"","{rod:\"wood\",cap:\"iron\"}"));Object item=stackType.getMethod("func_77973_b").invoke(wand);
        for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))item.getClass().getMethod("storeVis",stackType,aspectType,int.class).invoke(item,wand,aspectType.getMethod("getAspect",String.class).invoke(null,tag),2500);
        player.getInventory().setItem(10,content.gameplayStack(services.items().snapshot(wand)));
        item.getClass().getMethod("storeVis",stackType,aspectType,int.class).invoke(item,wand,aspectType.getMethod("getAspect",String.class).invoke(null,"perditio"),2000);
        player.getInventory().setItem(9,content.gameplayStack(services.items().snapshot(wand)));
        Object gold=services.items().materialize(new LegacyClientData.Stack("Thaumcraft:WandCasting",0,1,"","{rod:\"wood\",cap:\"gold\",perditio:2000}"));
        player.getInventory().setItem(11,content.gameplayStack(services.items().snapshot(gold)));player.inventoryMenu.broadcastChanges();
    }
}
