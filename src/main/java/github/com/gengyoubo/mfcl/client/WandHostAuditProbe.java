package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Native server menu audit, deliberately using the same callbacks as an ordinary player's output-slot click. */
final class WandHostAuditProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private int stage;
    private int frames;
    private String focusItemKey;
    private String focusFixture="wand";
    private CompletableFuture<?> pending;
    private ArcaneWorkbenchHostAcceptance workbench;
    private String worldName;
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);
    private WandHostAuditProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new WandHostAuditProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Wand host audit timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);stage=1;
                worldName="mfcl-wand-audit-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(worldName,new LevelSettings("Wand audit",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null){
                pending=server(mc,()->{audit(mc);verifyNode(mc);prepareFocus(mc);return true;});stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&++frames>12&&mc.screen==null){
                pending.join();selectFocus(mc,false);stage=3;frames=0;
            }else if(stage==3&&++frames>8){pending=server(mc,()->{verifyFocus(mc,true);return true;});stage=4;frames=0;
            }else if(stage==4&&pending.isDone()&&++frames>8&&mc.screen==null){pending.join();selectFocus(mc,true);stage=5;frames=0;
            }else if(stage==5&&++frames>8){pending=server(mc,()->{verifyFocus(mc,false);return true;});stage=6;
            }else if(stage==6&&pending.isDone()){
                pending.join();pending=server(mc,()->{verifyPreviewScene();prepareFocus(mc,"sceptre");return true;});stage=7;frames=0;
            }else if(stage==7&&pending.isDone()&&++frames>12&&mc.screen==null){
                pending.join();pressFocusKey(mc);check(mc.screen==null,"Sceptre opened the focus selector");stage=8;frames=0;
            }else if(stage==8&&++frames>8){
                pending=server(mc,()->{verifyFocus(mc,false);report.put("sceptreSelectorBlockedWithoutInventoryChanges",true);prepareFocus(mc,"staff");return true;});stage=9;frames=0;
            }else if(stage==9&&pending.isDone()&&++frames>12&&mc.screen==null){
                pending.join();selectFocus(mc,false);stage=10;frames=0;
            }else if(stage==10&&++frames>8){pending=server(mc,()->{verifyFocus(mc,true);return true;});stage=11;frames=0;
            }else if(stage==11&&pending.isDone()&&++frames>8&&mc.screen==null){pending.join();selectFocus(mc,true);stage=12;frames=0;
            }else if(stage==12&&++frames>8){pending=server(mc,()->{verifyFocus(mc,false);return true;});stage=13;
            }else if(stage==13&&pending.isDone()){
                pending.join();
                if(workbench==null)workbench=new ArcaneWorkbenchHostAcceptance(content,report,worldName);
                if(workbench.tick(mc)){report.put("status","PASS");finish(mc);}
            }
        }catch(Throwable failure){report.put("status","FAIL");report.put("failure",failure.toString());try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}}
    }
    private <T> CompletableFuture<T> server(Minecraft mc,java.util.concurrent.Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private void pressFocusKey(Minecraft mc){
        var key=com.mojang.blaze3d.platform.InputConstants.getKey(org.lwjgl.glfw.GLFW.GLFW_KEY_F,0);
        net.minecraft.client.KeyMapping.click(key);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.InputEvent.Key(org.lwjgl.glfw.GLFW.GLFW_KEY_F,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0));
        check(!mc.options.keySwapOffhand.consumeClick(),"F also queued an offhand swap while holding a wand");
    }
    private void selectFocus(Minecraft mc,boolean remove){
        pressFocusKey(mc);
        check(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("Selector"),"F did not open the focus selector");
        var buttons=mc.screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.Button).map(child->(net.minecraft.client.gui.components.Button)child).toList();
        var button=remove?buttons.stream().filter(value->value.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("mfcl.wand.focus.remove").getString())).findFirst().orElseThrow():buttons.get(0);
        button.onPress();report.put("nativeFocusSelectorAndOffhandConflict",true);
    }
    private void prepareFocus(Minecraft mc)throws Exception{
        prepareFocus(mc,"wand");
    }
    private void prepareFocus(Minecraft mc,String kind)throws Exception{
        focusFixture=kind;
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.closeContainer();player.getInventory().clearContent();
        var services=content.services();var loader=services.loader();
        var focus=services.items().inLegacy(()->{for(var candidate:services.items().catalog()){
            Object old=services.items().materialize(candidate),item=loader.loadClass("net.minecraft.item.ItemStack").getMethod("func_77973_b").invoke(old);
            if(loader.loadClass("thaumcraft.api.wands.ItemFocusBasic").isInstance(item))return candidate;
        }throw new IllegalStateException("No focus fixture");});
        String tag=switch(kind){case "sceptre"->"{rod:\"silverwood\",cap:\"thaumium\",sceptre:1b}";case "staff"->"{rod:\"silverwood_staff\",cap:\"thaumium\"}";default->"{rod:\"wood\",cap:\"iron\"}";};
        focusItemKey=focus.key();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:WandCasting",0,1,tag));
        services.items().inLegacy(()->{
            Object old=services.items().materialize(content.gameplayStack(player.getMainHandItem()));var stackType=loader.loadClass("net.minecraft.item.ItemStack");Object wand=stackType.getMethod("func_77973_b").invoke(old);
            check((boolean)wand.getClass().getMethod("isSceptre",stackType).invoke(wand,old)==kind.equals("sceptre"),"Invalid sceptre fixture");
            check((boolean)wand.getClass().getMethod("isStaff",stackType).invoke(wand,old)==kind.equals("staff"),"Invalid staff fixture");return null;
        });
        player.getInventory().setItem(1,content.stack(focus.key(),focus.metadata(),1,focus.tag()));player.inventoryMenu.broadcastChanges();
    }
    private void verifyFocus(Minecraft mc,boolean equipped)throws Exception{
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var services=content.services();
        Object old=services.items().materialize(content.gameplayStack(player.getMainHandItem()));var stackType=services.loader().loadClass("net.minecraft.item.ItemStack");Object wand=stackType.getMethod("func_77973_b").invoke(old);
        Object focus=wand.getClass().getMethod("getFocusItem",stackType).invoke(wand,old);
        check((focus!=null)==equipped,"Focus request did not reach the authoritative held wand");
        if(equipped)check(services.items().snapshot(focus).key().equals(focusItemKey),"Wrong focus equipped");
        check(player.getInventory().countItem(content.items().get(focusItemKey))==(equipped?0:1),"Focus was duplicated or lost");
        report.put(equipped?"nativeFocusEquippedViaPacket":"nativeFocusReturnedExactlyOnce",true);
        report.put(focusFixture+(equipped?"FocusEquipped":"FocusInInventory"),true);
    }
    /** XU's inventory read calls markDirty after TileEntity restores the saved absolute coordinates. */
    private void verifyPreviewScene()throws Exception{
        if(!content.items().containsKey("ExtraUtilities:extractor_base")){report.put("previewTileCoordinates","SKIP: Extra Utilities not installed");return;}
        var services=content.services();var loader=services.loader();
        services.items().inLegacy(()->{
            var cell=new HostWorldAccess.Block("ExtraUtilities:extractor_base",12);
            try(var source=new LegacyBlockScene(services,(x,y,z)->cell)){
                Object original=services.tiles().create(cell.legacyKey(),12,source.world(),-41,-60,-64);
                byte[] saved=services.tiles().save(original),unchanged=saved.clone();
                var view=new LegacyBlockRendering.View(){
                    public HostWorldAccess.Block block(int x,int y,int z){check(Math.abs(x)<=1&&Math.abs(y)<=1&&Math.abs(z)<=1,"Preview used absolute snapshot coordinates: "+List.of(x,y,z));return x==0&&y==0&&z==0?cell:new HostWorldAccess.Block("minecraft:air",0);}
                    public boolean legacyTile(int x,int y,int z){return x==0&&y==0&&z==0;}
                    public byte[] tileData(int x,int y,int z){return legacyTile(x,y,z)?saved:null;}
                };
                try(var scene=new LegacyBlockScene(services,view)){
                    Object tile=loader.loadClass("net.minecraft.world.World").getMethod("func_147438_o",int.class,int.class,int.class).invoke(scene.world(),0,0,0);
                    var type=loader.loadClass("net.minecraft.tileentity.TileEntity");
                    check(tile!=null&&type.getField("field_145851_c").getInt(tile)==0&&type.getField("field_145848_d").getInt(tile)==0&&type.getField("field_145849_e").getInt(tile)==0,"Preview tile was not rebased");
                    check(Arrays.equals(saved,unchanged),"Preview modified the authoritative snapshot");
                }
                // The same bounded view must also survive the original contextual block renderer.
                content.blockRendering().capture(cell.legacyKey(),12,view);
                report.put("previewTileCoordinates","PASS");
            }
            return null;
        });
    }
    private void verifyNode(Minecraft mc)throws Exception{
        var services=content.services();var loader=services.loader();var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        player.closeContainer();player.getInventory().clearContent();var pos=player.blockPosition().south(3);
        level.setBlockAndUpdate(pos,content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockAiry",0)));Object node=((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();
        var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");Object air=aspectType.getMethod("getAspect",String.class).invoke(null,"aer");
        Object aspects=loader.loadClass("thaumcraft.api.aspects.AspectList").getConstructor().newInstance();aspects.getClass().getMethod("add",aspectType,int.class).invoke(aspects,air,20);node.getClass().getMethod("setAspects",aspects.getClass()).invoke(node,aspects);
        player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()-2);player.setYRot(0);player.setXRot(24);player.getInventory().selected=0;
        player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:WandCasting",0,1,"{rod:\"wood\",cap:\"iron\"}"));
        player.gameMode.useItem(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND);
        check(content.gameplayStack(player.getMainHandItem()).tag().contains("IIUX"),"Native wand ray trace did not select the node");
        ItemStack held=player.getMainHandItem();for(int remaining=100;remaining>50;remaining--)held.getItem().onUseTick(level,player,held,remaining);
        check(player.getMainHandItem()==held,"Charging replaced the live held-use stack");
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");Object old=services.items().materialize(content.gameplayStack(held)),wand=stackType.getMethod("func_77973_b").invoke(old);
        int vis=(int)wand.getClass().getMethod("getVis",stackType,aspectType).invoke(wand,old,air),left=(int)aspects.getClass().getMethod("getAmount",aspectType).invoke(aspects,air);
        check(vis>0&&left*100+vis==2000,"Native node charging did not conserve vis: node="+left+", wand="+vis);
        report.put("nativeNodeChargeConserved",Map.of("nodeAirRemaining",left,"heldRawAirVis",vis));
        player.releaseUsingItem();check(!content.gameplayStack(held).tag().contains("IIUX"),"Releasing the wand retained its old node target");
        report.put("nativeWandReleaseAndStackIdentity",true);
    }
    private void finish(Minecraft mc)throws Exception{
        Path output=mc.gameDirectory.toPath().resolve("logs/mfcl/wand-host-audit.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        com.mojang.logging.LogUtils.getLogger().info("MFCL_WAND_HOST_AUDIT: {}",report);stage=99;
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
    private void audit(Minecraft mc)throws Exception{
        var services=content.services();var loader=services.loader();var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
        try {
            var ordinary=player.blockPosition().east(2);level.setBlockAndUpdate(ordinary,net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
            player.openMenu(level.getBlockState(ordinary).getMenuProvider(level,ordinary));
            var crafting=(net.minecraft.world.inventory.CraftingMenu)player.containerMenu;
            Object cap=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandCap").getField("caps").get(null)).get("iron");
            Object rod=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandRod").getField("rods").get(null)).get("wood");
            ItemStack modernCap=content.gameplayStack(services.items().snapshot(cap.getClass().getMethod("getItem").invoke(cap)));
            ItemStack modernRod=content.gameplayStack(services.items().snapshot(rod.getClass().getMethod("getItem").invoke(rod)));
            crafting.getSlot(1).set(modernCap.copy());crafting.getSlot(5).set(modernRod.copy());crafting.getSlot(9).set(modernCap.copy());crafting.broadcastChanges();
            report.put("ordinaryWandPreview",crafting.getSlot(0).getItem().toString());
            crafting.clicked(0,0,ClickType.PICKUP,player);
            boolean consumed=true;for(int i=1;i<=9;i++)if(!crafting.getSlot(i).getItem().isEmpty())consumed=false;
            report.put("nativeBasicWandCraft",!crafting.getCarried().isEmpty()&&content.gameplayStack(crafting.getCarried()).key().equals("Thaumcraft:WandCasting")&&consumed?"PASS":"FAIL");
            if(!crafting.getCarried().isEmpty())report.put("ordinaryWandResult",content.gameplayStack(crafting.getCarried()));
        } catch(Exception failure){report.put("nativeBasicWandCraft","FAIL: "+failure);}
        player.closeContainer();
        Object recipe=services.items().inLegacy(()->{
            for(Object candidate:(List<?>)loader.loadClass("thaumcraft.api.ThaumcraftApi").getMethod("getCraftingRecipes").invoke(null))if(candidate.getClass().getSimpleName().equals("ShapedArcaneRecipe")){
                Object output=candidate.getClass().getMethod("getRecipeOutput").invoke(candidate);
                Object cost=candidate.getClass().getMethod("getAspects").invoke(candidate);Object[] aspects=(Object[])cost.getClass().getMethod("getAspects").invoke(cost);boolean small=aspects.length>0;
                for(Object aspect:aspects)if((int)cost.getClass().getMethod("getAmount",aspectType).invoke(cost,aspect)>15)small=false;
                if(output!=null&&small)return candidate;
            }throw new IllegalStateException("No small original arcane recipe found");
        });
        String outputKey=services.items().snapshot(recipe.getClass().getMethod("getRecipeOutput").invoke(recipe)).key();
        String research=(String)recipe.getClass().getMethod("getResearch").invoke(recipe);report.put("recipeOutput",outputKey);report.put("recipeResearch",research);
        Object world=services.worlds().bound(level);
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){
            Object actor=content.itemUse().clientActor(world,new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0),null);
            Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null),knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
            @SuppressWarnings("unchecked") var completed=(Map<String,ArrayList<String>>)knowledge.getClass().getField("researchCompleted").get(knowledge);
            var learned=completed.computeIfAbsent(player.getName().getString(),ignored->new ArrayList<>());if(!learned.contains(research))learned.add(research);
            player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));return null;
        }});
        var pos=player.blockPosition().east(2);level.setBlockAndUpdate(pos,content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockTable",15)));
        var tile=(LegacyBlockEntity)level.getBlockEntity(pos);Object original=tile.legacy();
        var held=new LegacyClientData.Stack("Thaumcraft:WandCasting",0,1,"","{rod:\"wood\",cap:\"iron\"}");
        Object charged=services.items().materialize(held),wand=stackType.getMethod("func_77973_b").invoke(charged);
        for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))wand.getClass().getMethod("storeVis",stackType,aspectType,int.class).invoke(wand,charged,aspectType.getMethod("getAspect",String.class).invoke(null,tag),2500);
        Object[] inputs=(Object[])recipe.getClass().getMethod("getInput").invoke(recipe);int width=recipe.getClass().getField("width").getInt(recipe);
        for(int i=0;i<inputs.length;i++){
            Object input=inputs[i];if(input instanceof List<?> choices)input=choices.isEmpty()?null:choices.get(0);
            if(input!=null){var snapshot=services.items().snapshot(input);int meta=snapshot.metadata()==32767?0:snapshot.metadata();services.inventories().set(original,(i/width)*3+i%width,new LegacyClientData.Stack(snapshot.key(),meta,1,"",snapshot.tag()));}
        }
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);player.getInventory().clearContent();
        if(!level.getBlockState(pos).use(level,player,InteractionHand.MAIN_HAND,hit).consumesAction()||!(player.containerMenu instanceof LegacyDeviceMenu))throw new IllegalStateException("Native workbench menu did not open");
        var menu=(LegacyDeviceMenu)player.containerMenu;
        report.put("withoutWandOutputEmpty",menu.getSlot(0).getItem().isEmpty());
        menu.setCarried(content.gameplayStack(held));menu.clicked(1,0,ClickType.PICKUP,player);report.put("emptyWandOutputEmpty",menu.getSlot(0).getItem().isEmpty());
        menu.clicked(1,0,ClickType.PICKUP,player);menu.setCarried(content.gameplayStack(services.items().snapshot(charged)));menu.clicked(1,0,ClickType.PICKUP,player);
        report.put("chargedWandOutput",menu.getSlot(0).getItem().toString());
        if(menu.getSlot(0).getItem().isEmpty()){
            report.put("nativeArcaneCraft","FAIL: charged wand produced no output");
            services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){
                services.inventories().set(original,10,services.items().snapshot(charged));menu.broadcastChanges();return null;
            }});
            report.put("outputWithExplicitServerKnowledge",menu.getSlot(0).getItem().toString());
            if(!menu.getSlot(0).getItem().isEmpty()){
                var before=services.inventories().get(original,10);menu.clicked(0,0,ClickType.PICKUP,player);var after=services.inventories().get(original,10);
                report.put("controlledOutputPickup",menu.getCarried().toString());report.put("controlledPickupVisChanged",!before.tag().equals(after.tag()));
            }
            throw new IllegalStateException("Native arcane menu required an explicit research scope");
        }
        var expectedVis=expectedCraftingVis(original,menu.originalPlayer(),level);
        var before=services.inventories().get(original,10);menu.clicked(0,0,ClickType.PICKUP,player);
        var after=services.inventories().get(original,10);var remaining=new ArrayList<LegacyClientData.Stack>();for(int i=0;i<9;i++)remaining.add(services.inventories().get(original,i));
        report.put("takenOutput",menu.getCarried().toString());report.put("beforeWand",before);report.put("afterWand",after);report.put("remainingIngredients",remaining);
        boolean passed=!menu.getCarried().isEmpty()&&content.gameplayStack(menu.getCarried()).key().equals(outputKey)&&!before.tag().equals(after.tag())&&remaining.stream().allMatch(Objects::isNull)&&vis(after).equals(expectedVis);
        report.put("nativeArcaneCraft",passed?"PASS":"FAIL");
        check(passed,"Native arcane output, ingredient consumption or exact vis charge failed");report.put("nativeArcaneVisChargedExactlyOnce",true);
        byte[] saved=services.tiles().save(original);Object restored=services.tiles().create("Thaumcraft:blockTable",15,services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ());services.tiles().load(restored,saved);
        report.put("savedWandNbtPreserved",after.tag().equals(services.inventories().get(restored,10).tag()));
        player.closeContainer();
        verifyAdvanced(mc);
    }
    private Map<String,Integer> vis(LegacyClientData.Stack snapshot)throws Exception{
        var services=content.services();var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
        Object stack=services.items().materialize(snapshot),item=stackType.getMethod("func_77973_b").invoke(stack);var values=new LinkedHashMap<String,Integer>();
        for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))values.put(tag,(int)item.getClass().getMethod("getVis",stackType,aspectType).invoke(item,stack,aspectType.getMethod("getAspect",String.class).invoke(null,tag)));
        return values;
    }
    private Map<String,Integer> expectedCraftingVis(Object tile,Object actor,net.minecraft.server.level.ServerLevel level)throws Exception{
        var services=content.services();var loader=services.loader();return services.items().inLegacy(()->{try(var state=services.playerState().enter(services.worlds().bound(level))){
            var stackType=loader.loadClass("net.minecraft.item.ItemStack");Object stack=services.items().materialize(services.inventories().get(tile,10)),item=stackType.getMethod("func_77973_b").invoke(stack);
            Object cost=loader.loadClass("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager").getMethod("findMatchingArcaneRecipeAspects",loader.loadClass("net.minecraft.inventory.IInventory"),loader.loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(null,tile,actor);
            item.getClass().getMethod("consumeAllVisCrafting",stackType,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),cost.getClass(),boolean.class).invoke(item,stack,actor,cost,true);
            return vis(services.items().snapshot(stack));
        }});
    }
    private void verifyAdvanced(Minecraft mc)throws Exception{
        var services=content.services();var loader=services.loader();var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        Object cap=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandCap").getField("caps").get(null)).get("gold"),rod=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandRod").getField("rods").get(null)).get("greatwood");
        content.withApiPlayer(player,actor->{try{
            Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null),knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
            @SuppressWarnings("unchecked") var completed=(Map<String,ArrayList<String>>)knowledge.getClass().getField("researchCompleted").get(knowledge);
            var learned=completed.computeIfAbsent(player.getName().getString(),ignored->new ArrayList<>());
            for(Object part:List.of(cap,rod)){String key=(String)part.getClass().getMethod("getResearch").invoke(part);if(!learned.contains(key))learned.add(key);}return null;
        }catch(Exception failure){throw new IllegalStateException(failure);}});
        player.getInventory().clearContent();var pos=player.blockPosition().east(3);level.setBlockAndUpdate(pos,content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockTable",15)));
        Object tile=((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();
        level.getBlockState(pos).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));var menu=(LegacyDeviceMenu)player.containerMenu;
        var capStack=services.items().snapshot(cap.getClass().getMethod("getItem").invoke(cap));var rodStack=services.items().snapshot(rod.getClass().getMethod("getItem").invoke(rod));
        for(int slot:List.of(4,8)){menu.setCarried(content.stack(capStack.key(),capStack.metadata(),1,capStack.tag()));menu.clicked(slot,0,ClickType.PICKUP,player);}
        menu.setCarried(content.stack(rodStack.key(),rodStack.metadata(),1,rodStack.tag()));menu.clicked(6,0,ClickType.PICKUP,player);
        Object tool=services.items().materialize(new LegacyClientData.Stack("Thaumcraft:WandCasting",0,1,"","{rod:\"silverwood\",cap:\"thaumium\"}")),item=stackType.getMethod("func_77973_b").invoke(tool);var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
        for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))item.getClass().getMethod("storeVis",stackType,aspectType,int.class).invoke(item,tool,aspectType.getMethod("getAspect",String.class).invoke(null,tag),10000);
        menu.setCarried(content.gameplayStack(services.items().snapshot(tool)));menu.clicked(1,0,ClickType.PICKUP,player);
        check(!menu.getSlot(0).getItem().isEmpty(),"Native advanced wand assembly produced no result");var expected=expectedCraftingVis(tile,menu.originalPlayer(),level);
        menu.clicked(0,0,ClickType.PICKUP,player);var output=content.gameplayStack(menu.getCarried());
        check(output.key().equals("Thaumcraft:WandCasting")&&output.tag().contains("greatwood")&&output.tag().contains("gold"),"Native advanced wand lost its rod/cap NBT");
        check(vis(services.inventories().get(tile,10)).equals(expected),"Advanced wand assembly charged the wrong vis");
        for(int i=0;i<9;i++)check(services.inventories().get(tile,i)==null,"Advanced wand assembly left a consumed ingredient");
        report.put("nativeAdvancedWandCraft","PASS");report.put("nativeAdvancedWandResult",output);report.put("nativeAdvancedToolVis",expected);player.closeContainer();
    }
}
