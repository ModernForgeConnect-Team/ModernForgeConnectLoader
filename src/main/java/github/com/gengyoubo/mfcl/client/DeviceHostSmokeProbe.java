package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Disposable JEI/TC world: changing creative slots, device screens, authoritative clicks and real lifter motion. */
final class DeviceHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private CompletableFuture<?> pending;
    private int stage,frames;
    private int reportedStage=-1;
    private long stageStarted;
    private BlockPos chest,table,lifter,research,deconstruction;
    private UUID lifted;
    private double initialY;
    private ResearchNoteHostAcceptance noteAcceptance;
    private ResearchTableHostAcceptance tableAcceptance;
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private DeviceHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new DeviceHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(reportedStage!=stage){reportedStage=stage;stageStarted=System.nanoTime();com.mojang.logging.LogUtils.getLogger().info("MFCL_DEVICE_STAGE {}",stage);}
            if(pending!=null&&pending.isCompletedExceptionally())pending.join();
            if(stage>1&&System.nanoTime()-stageStarted>TimeUnit.SECONDS.toNanos(25))throw new IllegalStateException("Device stage "+stage+" stalled; screen="+(mc.screen==null?"none":mc.screen.getClass().getName())+" menu="+(mc.player==null?"none":mc.player.containerMenu.getClass().getName()));
            if(System.nanoTime()>deadline)throw new IllegalStateException("Device acceptance timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.languageCode="zh_cn";
                mc.createWorldOpenFlows().createFreshLevel("mfcl-device-smoke-"+System.currentTimeMillis(),new LevelSettings("MFCL device acceptance",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null) {
                pending=server(mc,()->{verifyReadableGiveIds(mc);setup(mc);return true;});
                mc.setScreen(new CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),false));stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&++frames>8) {
                pending.join();select(mc,CreativeModeTabs.INVENTORY);stage=3;frames=0;
            }else if(stage==3&&++frames>8) {
                select(mc,CreativeModeTabs.BUILDING_BLOCKS);stage=4;frames=0;
            }else if(stage==4&&++frames>8) {
                proof.put("creativeTabSlotChanges",true);mc.screen.onClose();stage=40;frames=0;
            }else if(stage==40&&++frames>8) {
                pending=server(mc,()->{activate(mc,chest);return true;});stage=5;frames=0;
            }else if(stage==5&&pending.isDone()&&mc.screen instanceof LegacyDeviceScreen&&++frames>8) {
                pending.join();verifyScreen(mc,"GuiChest");screenshot(mc,"hungry-chest");
                clickItem(mc,Items.DIAMOND);stage=6;frames=0;
            }else if(stage==6&&++frames>8) {
                if(mc.player.containerMenu.getCarried().getCount()!=3)throw new IllegalStateException("Native cursor did not synchronize original pickup");
                var oldClient=content.services().loader().loadClass("net.minecraft.client.Minecraft").getMethod("func_71410_x").invoke(null);
                var oldPlayer=oldClient.getClass().getField("field_71439_g").get(oldClient);
                var oldInventory=content.services().loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(oldPlayer);
                var carried=oldInventory.getClass().getMethod("func_70445_o").invoke(oldInventory);
                if(carried==null||content.services().items().snapshot(carried).count()!=3)throw new IllegalStateException("Original GUI cursor did not mirror native carried stack");
                proof.put("originalGuiCarriedStackSynchronized",true);
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);stage=7;frames=0;
            }else if(stage==7&&++frames>8) {
                var oldContainer=content.services().loader().loadClass("net.minecraft.client.gui.inventory.GuiContainer").getField("field_147002_h").get(((LegacyDeviceScreen)mc.screen).originalGui());
                if(oldContainer.getClass().getMethod("func_85151_d").invoke(oldContainer)!=((LegacyBlockEntity)mc.level.getBlockEntity(chest)).legacy())throw new IllegalStateException("Client tile synchronization invalidated the open original GUI");
                proof.put("openGuiRetainedClientTileIdentity",true);
                pending=server(mc,()->{
                    var level=mc.getSingleplayerServer().overworld();var tile=(LegacyBlockEntity)level.getBlockEntity(chest);
                    var stack=content.services().inventories().get(tile.legacy(),0);
                    if(stack==null||!stack.key().equals("minecraft:diamond")||stack.count()!=3)throw new IllegalStateException("Original hungry chest did not receive native click items");
                    proof.put("hungryChestOriginalContainerAndNativeClicks",true);
                    var entity=level.getEntity(lifted);var lift=(LegacyBlockEntity)level.getBlockEntity(lifter);
                    if(lift.logicSuspended()||lift.successfulTicks()<10||entity==null||entity.getY()<=initialY+.1)throw new IllegalStateException("Original lifter did not move native entity");
                    proof.put("lifterMovedNativeEntity",true);entity.discard();
                    return true;
                });stage=70;frames=0;
            }else if(stage==70&&pending.isDone()) {
                pending.join();mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,1,ClickType.PICKUP,mc.player);stage=71;frames=0;
            }else if(stage==71&&++frames>8) {
                if(mc.player.containerMenu.getCarried().getCount()!=2)throw new IllegalStateException("Original right-click pickup did not split chest stack");
                mc.screen.onClose();stage=72;frames=0;
            }else if(stage==72&&++frames>8) {
                pending=server(mc,()->{
                    var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    var stack=content.services().inventories().get(((LegacyBlockEntity)level.getBlockEntity(chest)).legacy(),0);
                    if(stack==null||stack.count()!=1||player.getInventory().countItem(Items.DIAMOND)!=2||!player.containerMenu.getCarried().isEmpty())throw new IllegalStateException("Closing device duplicated or lost carried stack");
                    proof.put("closeReturnedCarriedItemsExactlyOnce",true);player.getInventory().setItem(1,new ItemStack(Items.STICK,2));activate(mc,table);return true;
                });stage=8;frames=0;
            }else if(stage==8&&pending.isDone()&&mc.screen instanceof LegacyDeviceScreen&&++frames>8) {
                pending.join();verifyScreen(mc,"GuiArcaneWorkbench");screenshot(mc,"arcane-workbench");clickItem(mc,Items.STICK);stage=9;frames=0;
            }else if(stage==9&&++frames>8) {
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,1,0,ClickType.PICKUP,mc.player);stage=10;frames=0;
            }else if(stage==10&&++frames>8) {
                pending=server(mc,()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    if(!player.containerMenu.getSlot(1).getItem().isEmpty()||!player.containerMenu.getCarried().is(Items.STICK)||player.containerMenu.getCarried().getCount()!=2)throw new IllegalStateException("Original wand-slot restriction was lost");
                    proof.put("originalWandSlotRestriction",true);return true;
                });stage=50;frames=0;
            }else if(stage==50&&pending.isDone()) {
                pending.join();mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,2,0,ClickType.PICKUP,mc.player);stage=52;frames=0;
            }else if(stage==52&&++frames>8) {
                pending=server(mc,()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    if(!player.containerMenu.getSlot(2).getItem().is(Items.STICK)||player.containerMenu.getSlot(2).getItem().getCount()!=2)throw new IllegalStateException("Original workbench grid did not accept native click");
                    proof.put("arcaneWorkbenchOriginalGuiAndNativeClicks",true);return true;
                });stage=53;
            }else if(stage==53&&pending.isDone()) {
                pending.join();mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,2,1,ClickType.THROW,mc.player);stage=51;frames=0;
            }else if(stage==51&&++frames>8) {
                if(!((LegacyBlockEntity)mc.level.getBlockEntity(research)).legacy().getClass().getSimpleName().equals("TileTable"))throw new IllegalStateException("Research conversion fixture was not an ordinary client table");
                pending=server(mc,()->{
                    var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    if(!player.containerMenu.getSlot(2).getItem().isEmpty()||level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(4)).stream().noneMatch(entity->entity.getItem().is(Items.STICK)&&entity.getItem().getCount()==2))throw new IllegalStateException("Original container throw did not create native item drop");
                    proof.put("originalContainerThrowCreatedNativeDrop",true);player.closeContainer();
                    content.logicTransaction(level,research,()->{
                        Object world=content.services().worlds().bound(level);
                        LegacyWorlds.metadata(world,research.getX(),research.getY(),research.getZ(),2,3);
                        Object replacement=content.services().tiles().create("Thaumcraft:blockTable",2,world,research.getX(),research.getY(),research.getZ());
                        LegacyWorlds.tile(world,research.getX(),research.getY(),research.getZ(),replacement);return true;
                    },true);return true;
                });stage=54;frames=0;
            }else if(stage==54&&pending.isDone()&&++frames>8) {
                pending.join();
                if(!((LegacyBlockEntity)mc.level.getBlockEntity(research)).legacy().getClass().getSimpleName().equals("TileResearchTable"))throw new IllegalStateException("Converted research table retained ordinary client tile subtype");
                proof.put("convertedResearchTableClientSubtype",true);
                pending=server(mc,()->{seedResearch(mc);activate(mc,research);return true;});stage=11;frames=0;
            }else if(stage==11&&pending.isDone()&&mc.screen instanceof LegacyDeviceScreen&&++frames>8) {
                pending.join();verifyScreen(mc,"GuiResearchTable");screenshot(mc,"research-table");proof.put("researchTableOriginalGui",true);
                verifyItemTooltips(mc);
                selectResearchPair(mc);clickResearchCombine(mc);stage=80;frames=0;
            }else if(stage==80&&++frames>12) {
                verifyScreen(mc,"GuiResearchTable");
                if(pool(mc,"aer")!=19||pool(mc,"ignis")!=19||pool(mc,"lux")!=1)throw new IllegalStateException("Research button did not synchronize original combination: "+pool(mc,"aer")+","+pool(mc,"ignis")+","+pool(mc,"lux"));
                pending=server(mc,()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var world=content.services().worlds().bound(player.serverLevel());
                    return content.services().items().inLegacy(()->{try(var state=content.services().playerState().enter(world)) {
                        if(pool(mc,"aer")!=19||pool(mc,"ignis")!=19||pool(mc,"lux")!=1)throw new IllegalStateException("Research changed only the client knowledge");
                        byte[] data=player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
                        if(data.length==0)throw new IllegalStateException("Research combination did not persist player knowledge");
                        content.services().playerData().write(((LegacyDeviceMenu)player.containerMenu).originalPlayer(),data);
                        if(pool(mc,"lux")!=1)throw new IllegalStateException("Combined aspect was lost when loading persisted knowledge");
                        Object nbt=content.services().loader().loadClass("net.minecraft.entity.Entity").getMethod("getEntityData").invoke(((LegacyDeviceMenu)player.containerMenu).originalPlayer());
                        if(!"retained".equals(nbt.getClass().getMethod("func_74779_i",String.class).invoke(nbt,"mfcl_device_probe")))throw new IllegalStateException("Research combination erased existing legacy player tags");
                    }
                    proof.put("researchCombineOriginalHandlerAndPersistence",true);return true;});
                });stage=81;frames=0;
            }else if(stage==81&&pending.isDone()) {
                pending.join();proof.put("researchCombineClientSynchronization",true);clickResearchCombine(mc);stage=82;frames=0;
            }else if(stage==82&&++frames>12) {
                verifyScreen(mc,"GuiResearchTable");
                if(pool(mc,"aer")!=18||pool(mc,"ignis")!=18||pool(mc,"lux")!=2)throw new IllegalStateException("Second research click was dropped or applied twice: "+pool(mc,"aer")+","+pool(mc,"ignis")+","+pool(mc,"lux"));
                proof.put("researchCombineRepeatedClickExactlyOnce",true);
                screenshot(mc,"research-combined");
                tableAcceptance=new ResearchTableHostAcceptance(content,proof);stage=83;frames=0;
            }else if(stage==83&&tableAcceptance.tick(mc)) {
                pending=server(mc,()->{mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).closeContainer();activate(mc,deconstruction);return true;});stage=12;frames=0;
            }else if(stage==12&&pending.isDone()&&mc.screen instanceof LegacyDeviceScreen&&++frames>8) {
                pending.join();verifyScreen(mc,"GuiDeconstructionTable");screenshot(mc,"deconstruction-table");proof.put("deconstructionTableOriginalGui",true);
                pending=server(mc,()->{mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).closeContainer();return true;});stage=13;
            }else if(stage==13&&pending.isDone()) {
                pending.join();noteAcceptance=new ResearchNoteHostAcceptance(content,proof);stage=90;
            }else if(stage==90&&noteAcceptance.tick(mc)) {
                proof.put("status","PASS");finish(mc,null);
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private void verifyReadableGiveIds(Minecraft mc) throws Exception {
        var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayers().get(0);
        var ring=content.items().get("Baubles:Ring");
        if(ring==null||!BuiltInRegistries.ITEM.getKey(ring).toString().equals("baubles:ring")
                ||!BuiltInRegistries.ITEM.getKey(content.items().get("Thaumcraft:ItemResearchNotes")).toString().equals("thaumcraft:item_research_notes"))throw new IllegalStateException("Legacy give IDs are not readable");
        var source=player.createCommandSourceStack().withPermission(2);var commands=server.getCommands();
        var suggestions=commands.getDispatcher().getCompletionSuggestions(commands.getDispatcher().parse("give @s baubles:",source)).join();
        if(suggestions.getList().stream().noneMatch(value->value.getText().equals("baubles:ring")))throw new IllegalStateException("Give completion does not expose the readable ring ID");
        int count=player.getInventory().countItem(ring);
        if(commands.performPrefixedCommand(source,"give @s baubles:ring 1")!=1||player.getInventory().countItem(ring)!=count+1)throw new IllegalStateException("Give command did not grant the original Baubles ring");
        proof.put("readableLegacyGiveIdsAndCompletion",true);
    }
    private void setup(Minecraft mc) throws Exception {
        var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var origin=player.blockPosition();
        player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));player.getInventory().selected=8;
        chest=origin.east(2);table=origin.south(2);research=origin.west(2);deconstruction=origin.north(2);lifter=origin.offset(3,0,3);
        place(level,chest,"Thaumcraft:blockChestHungry",0);place(level,table,"Thaumcraft:blockTable",15);place(level,research,"Thaumcraft:blockTable",0);place(level,deconstruction,"Thaumcraft:blockTable",14);place(level,lifter,"Thaumcraft:blockLifter",0);
        initialY=lifter.getY()+1.2;var item=new net.minecraft.world.entity.item.ItemEntity(level,lifter.getX()+.5,initialY,lifter.getZ()+.5,new ItemStack(Items.COBBLESTONE));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(item);lifted=item.getUUID();
    }
    private void place(net.minecraft.server.level.ServerLevel level,BlockPos pos,String key,int metadata) {
        level.setBlockAndUpdate(pos,content.modernBlock(new HostWorldAccess.Block(key,metadata)));((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();
    }
    private void activate(Minecraft mc,BlockPos pos) {
        var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        if(!level.getBlockState(pos).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction()||!(player.containerMenu instanceof LegacyDeviceMenu))throw new IllegalStateException("Original device did not open native menu at "+pos);
    }
    private void select(Minecraft mc,net.minecraft.resources.ResourceKey<CreativeModeTab> tab) throws Exception {
        var method=CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",CreativeModeTab.class);method.setAccessible(true);method.invoke(mc.screen,BuiltInRegistries.CREATIVE_MODE_TAB.get(tab));
    }
    private void clickItem(Minecraft mc,Item item) {
        var screen=(LegacyDeviceScreen)mc.screen;var slot=screen.getMenu().slots.stream().filter(value->value.getItem().is(item)).findFirst().orElseThrow();
        int x=screen.getGuiLeft()+slot.x+8,y=screen.getGuiTop()+slot.y+8;
        screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
    }
    private Object aspect(String tag) throws Exception {return content.services().loader().loadClass("thaumcraft.api.aspects.Aspect").getMethod("getAspect",String.class).invoke(null,tag);}
    private void verifyItemTooltips(Minecraft mc) throws Exception {
        var services=content.services();services.items().language("zh_cn");
        var notes=content.stack("Thaumcraft:ItemResearchNotes",24,1,"");var before=notes.getTag().copy();
        String name=notes.getHoverName().getString();if(!name.contains("研究笔记"))throw new IllegalStateException("Native research note name is not localized: "+name);
        var nativeLines=notes.getTooltipLines(mc.player,TooltipFlag.Default.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();
        Object oldClient=services.loader().loadClass("net.minecraft.client.Minecraft").getMethod("func_71410_x").invoke(null);
        var original=services.items().tooltip(content.renderSnapshot(notes),oldClient.getClass().getField("field_71439_g").get(oldClient),false);
        Object renderer=Class.forName("mezz.jei.library.render.ItemStackRenderer").getConstructor().newInstance();
        @SuppressWarnings("unchecked") var jei=(List<net.minecraft.network.chat.Component>)renderer.getClass().getMethod("getTooltip",ItemStack.class,TooltipFlag.class).invoke(renderer,notes,TooltipFlag.Default.NORMAL);
        if(!nativeLines.equals(original)||!nativeLines.equals(jei.stream().map(net.minecraft.network.chat.Component::getString).toList())||nativeLines.stream().noneMatch(line->line.contains("未知知识"))||nativeLines.stream().anyMatch(line->line.contains("1.7.10 ·")))throw new IllegalStateException("Inventory, original GUI and JEI tooltips differ: "+nativeLines+" vs "+original);
        if(!before.equals(notes.getTag()))throw new IllegalStateException("Displaying tooltips mutated original item data");
        services.items().language("en_us");String english=notes.getHoverName().getString();services.items().language("zh_cn");
        if(english.contains("研究笔记")||!notes.getHoverName().getString().contains("研究笔记"))throw new IllegalStateException("Item names did not follow language changes");
        proof.put("originalNativeAndJeiChineseTooltipsMatch",true);proof.put("tooltipLanguageSwitchAndSourceNbtPreserved",true);
        var unresolved=new ArrayList<String>();int count=0;
        for(var item:content.declarations().items())for(var variant:item.variants()) {
            String localized=content.stack(item.key(),variant.metadata(),1,"").getHoverName().getString();count++;
            if(localized.matches("(?:item|tile)\\.[A-Za-z0-9_.$:-]+"))unresolved.add(item.key()+"@"+variant.metadata()+"="+localized);
        }
        proof.put("auditedItemNames",count);proof.put("unresolvedItemTranslationKeys",unresolved);
    }
    private Object knowledge() throws Exception {var loader=content.services().loader();return loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null));}
    private int pool(Minecraft mc,String tag) throws Exception {
        return content.services().items().inLegacy(()->((Number)knowledge().getClass().getMethod("getAspectPoolFor",String.class,content.services().loader().loadClass("thaumcraft.api.aspects.Aspect")).invoke(knowledge(),mc.player.getName().getString(),aspect(tag))).intValue());
    }
    private void seedResearch(Minecraft mc) throws Exception {
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var services=content.services();Object world=services.worlds().bound(player.serverLevel());
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world)) {
            var type=services.loader().loadClass("thaumcraft.api.aspects.Aspect");Object known=knowledge();
            for(String tag:new String[]{"aer","ignis","lux"}) {
                known.getClass().getMethod("addDiscoveredAspect",String.class,type).invoke(known,player.getName().getString(),aspect(tag));
                known.getClass().getMethod("setAspectPool",String.class,type,short.class).invoke(known,player.getName().getString(),aspect(tag),(short)(tag.equals("lux")?0:20));
            }
            return null;
        }});
        services.items().inLegacy(()->{var actor=content.itemUse().clientActor(world,new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0),null);try(var state=services.playerState().enter(world)){Object nbt=services.loader().loadClass("net.minecraft.entity.Entity").getMethod("getEntityData").invoke(actor);nbt.getClass().getMethod("func_74778_a",String.class,String.class).invoke(nbt,"mfcl_device_probe","retained");player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));}return null;});
        content.itemUse().sync(world,new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0)).forEach(message->ScanSyncChannel.sendToPlayer(player,message));
    }
    private void selectResearchPair(Minecraft mc) throws Exception {
        Object gui=((LegacyDeviceScreen)mc.screen).originalGui();
        for(int index=1;index<=2;index++){var field=gui.getClass().getDeclaredField("select"+index);field.setAccessible(true);field.set(gui,aspect(index==1?"aer":"ignis"));}
    }
    private void clickResearchCombine(Minecraft mc) {
        var screen=(LegacyDeviceScreen)mc.screen;int x=screen.getGuiLeft()+51,y=screen.getGuiTop()+147;
        screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
    }
    private void verifyScreen(Minecraft mc,String name) {
        var screen=(LegacyDeviceScreen)mc.screen;
        if(!screen.originalGui().getClass().getSimpleName().equals(name)||ClientContainerBridge.active().commands(screen).isEmpty())throw new IllegalStateException("Original device GUI did not render: "+screen.originalGui().getClass());
    }
    private void screenshot(Minecraft mc,String name) throws Exception {
        var path=mc.gameDirectory.toPath().resolve("screenshots/device-"+name+".png");java.nio.file.Files.createDirectories(path.getParent());try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
    }
    private <T> CompletableFuture<T> server(Minecraft mc,Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private void finish(Minecraft mc,Throwable failure) {
        if(failure!=null){proof.put("status","FAIL");proof.put("stage",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_DEVICE_SMOKE_FAIL",failure);}stage=99;
        try{var report=mc.gameDirectory.toPath().resolve("logs/mfcl/device-smoke.json");java.nio.file.Files.createDirectories(report.getParent());java.nio.file.Files.writeString(report,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_DEVICE_SMOKE_RESULT: {}",proof);if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
