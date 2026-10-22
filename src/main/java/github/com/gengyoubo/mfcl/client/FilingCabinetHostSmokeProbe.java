package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Disposable real client/server acceptance for original Extra Utilities basic and advanced filing cabinets. */
final class FilingCabinetHostSmokeProbe {
    private interface Step{boolean run(Minecraft mc) throws Exception;}
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private final List<Step> steps=new ArrayList<>();
    private CompletableFuture<?> pending;
    private int stage,frames;
    private boolean done;
    private BlockPos basic,advanced;
    private Set<Integer> visibleBeforeScroll,visibleAfterScroll;
    private FilingCabinetHostSmokeProbe(ModernContentBridge content){this.content=content;define();}
    static void install(ModernContentBridge content){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(new FilingCabinetHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||done)return;var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new IllegalStateException("Filing cabinet acceptance timed out at step "+stage);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(++frames<8)return;
            if(steps.get(stage).run(mc)){stage++;frames=0;}
            if(stage==steps.size()){proof.put("status","PASS");finish(mc,null);}
        }catch(Throwable failure){finish(mc,failure);}
    }
    private void define() {
        steps.add(mc->{
            if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return false;
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
            mc.createWorldOpenFlows().createFreshLevel("mfcl-filing-"+System.currentTimeMillis(),new LevelSettings("MFCL filing cabinet acceptance",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            return true;
        });
        steps.add(mc->{
            if(mc.player==null||mc.level==null||mc.screen!=null)return false;
            server(mc,player->{
                var level=player.serverLevel();basic=player.blockPosition().offset(2,0,0);advanced=player.blockPosition().offset(2,0,2);
                var block=content.blocks().get("ExtraUtilities:filing").defaultBlockState();
                level.setBlock(basic,block.setValue(ModernContentBridge.METADATA,2),3);level.setBlock(advanced,block.setValue(ModernContentBridge.METADATA,8),3);
                var inventory=player.getInventory();inventory.clearContent();
                inventory.setItem(0,new ItemStack(Items.COBBLESTONE,64));inventory.setItem(1,new ItemStack(Items.COBBLESTONE,64));inventory.setItem(2,new ItemStack(Items.DIRT,64));
                inventory.setItem(3,new ItemStack(Items.IRON_SWORD));inventory.setItem(4,new ItemStack(Items.DIAMOND_PICKAXE));inventory.setItem(5,new ItemStack(Items.COBBLESTONE,64));
                inventory.setItem(7,new ItemStack(Items.FEATHER,16));
            });
            return true;
        });
        // Basic cabinet: one item kind, counts beyond a stack and beyond the one-byte vanilla protocol.
        steps.add(mc->{open(mc,basic);return true;});
        steps.add(mc->{
            var screen=screen(mc);
            if(!screen.originalGui().getClass().getName().equals("com.rwtema.extrautils.gui.GuiFilingCabinet"))throw new IllegalStateException("Unexpected original GUI "+screen.originalGui().getClass().getName());
            if(screen.getMenu().slots.size()!=1728+36)throw new IllegalStateException("Original cabinet slot layout was not mirrored: "+screen.getMenu().slots.size());
            proof.put("basicGuiOpened",true);click(mc,hotbar(mc,0),0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{carried(mc,Items.COBBLESTONE,64);click(mc,0,0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.AIR,0);click(mc,hotbar(mc,1),0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.COBBLESTONE,64);click(mc,7,0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.AIR,0);click(mc,hotbar(mc,5),0,ClickType.QUICK_MOVE);return true;});
        steps.add(mc->{
            stored(mc,basic,List.of(new ItemStack(Items.COBBLESTONE,192)));
            var screen=screen(mc);var slot=screen.getMenu().slots.get(0);
            if(!slot.getItem().is(Items.COBBLESTONE)||slot.getItem().getCount()!=192)throw new IllegalStateException("Client cabinet count is not the original count: "+slot.getItem());
            if(legacyCount(screen,0)!=192)throw new IllegalStateException("Original client GUI did not receive the stored count");
            if(!slot.isActive()||slot.x!=8||slot.y!=18)throw new IllegalStateException("Native slot did not follow the original layout: "+slot.x+","+slot.y);
            proof.put("basicInsertPickupAndShift",192);proof.put("countBeyondByteProtocol",true);
            click(mc,hotbar(mc,2),0,ClickType.PICKUP);return true;
        });
        // Extra Utilities registers "dirt" itself, and its arrayContain compares a[i] with a[j]: any two ore-named items match.
        steps.add(mc->{carried(mc,Items.DIRT,64);click(mc,1,0,ClickType.PICKUP);return true;});
        steps.add(mc->{
            carried(mc,Items.AIR,0);stored(mc,basic,List.of(new ItemStack(Items.COBBLESTONE,192),new ItemStack(Items.DIRT,64)));
            proof.put("basicOriginalOreNameMatch",true);click(mc,1,0,ClickType.QUICK_MOVE);return true;
        });
        steps.add(mc->{
            stored(mc,basic,List.of(new ItemStack(Items.COBBLESTONE,192)));
            if(playerCount(mc,Items.DIRT)!=64)throw new IllegalStateException("Dirt did not return to the player");
            click(mc,hotbar(mc,7),0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{carried(mc,Items.FEATHER,16);click(mc,1,0,ClickType.PICKUP);return true;});
        steps.add(mc->{
            carried(mc,Items.FEATHER,16);stored(mc,basic,List.of(new ItemStack(Items.COBBLESTONE,192)));proof.put("basicRejectsOtherItem",true);
            click(mc,hotbar(mc,7),0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{carried(mc,Items.AIR,0);click(mc,0,0,ClickType.PICKUP);return true;});
        steps.add(mc->{
            carried(mc,Items.COBBLESTONE,64);stored(mc,basic,List.of(new ItemStack(Items.COBBLESTONE,128)));
            click(mc,hotbar(mc,0),0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{carried(mc,Items.AIR,0);click(mc,0,0,ClickType.QUICK_MOVE);return true;});
        steps.add(mc->{
            stored(mc,basic,List.of(new ItemStack(Items.COBBLESTONE,64)));
            if(playerCount(mc,Items.COBBLESTONE)!=128)throw new IllegalStateException("Extracted cobblestone did not return to the player");
            if(screen(mc).getMenu().slots.get(0).getItem().getCount()!=64)throw new IllegalStateException("Client count did not drop back to a vanilla count");
            proof.put("basicExtractPickupAndShift",true);mc.player.closeContainer();return true;
        });
        // Advanced cabinet: any number of kinds, but only items whose stack limit is one.
        steps.add(mc->{open(mc,advanced);return true;});
        steps.add(mc->{screen(mc);click(mc,hotbar(mc,3),0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.IRON_SWORD,1);click(mc,0,0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.AIR,0);click(mc,hotbar(mc,4),0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.DIAMOND_PICKAXE,1);click(mc,0,0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.AIR,0);click(mc,hotbar(mc,0),0,ClickType.PICKUP);return true;});
        steps.add(mc->{carried(mc,Items.COBBLESTONE,64);click(mc,2,0,ClickType.PICKUP);return true;});
        steps.add(mc->{
            carried(mc,Items.COBBLESTONE,64);stored(mc,advanced,List.of(new ItemStack(Items.IRON_SWORD),new ItemStack(Items.DIAMOND_PICKAXE)));
            proof.put("advancedRejectsStackable",true);click(mc,hotbar(mc,0),0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{
            carried(mc,Items.AIR,0);var screen=screen(mc);
            var pickaxe=cabinetSlots(screen).stream().filter(slot->slot.getItem().is(Items.DIAMOND_PICKAXE)).findFirst().orElseThrow(()->new IllegalStateException("Pickaxe is not shown"));
            if(!pickaxe.isActive()||pickaxe.x<8||pickaxe.x>152||pickaxe.y<18||pickaxe.y>108)throw new IllegalStateException("Sorted original slot is outside the grid: "+pickaxe.x+","+pickaxe.y);
            mouse(screen,pickaxe);return true;
        });
        steps.add(mc->{
            carried(mc,Items.DIAMOND_PICKAXE,1);stored(mc,advanced,List.of(new ItemStack(Items.IRON_SWORD)));proof.put("advancedInsertAndMouseExtract",true);
            click(mc,hotbar(mc,4),0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{
            carried(mc,Items.AIR,0);
            server(mc,player->{
                var services=content.services();
                services.items().inLegacy(()->{
                    Object tile=((LegacyBlockEntity)player.serverLevel().getBlockEntity(advanced)).legacy();
                    var put=tile.getClass().getMethod("func_70299_a",int.class,services.loader().loadClass("net.minecraft.item.ItemStack"));
                    for(int damage=1;damage<60;damage++) {
                        var sword=new ItemStack(Items.IRON_SWORD);sword.setDamageValue(damage);
                        put.invoke(tile,(int)tile.getClass().getMethod("func_70302_i_").invoke(tile)-1,services.items().materialize(content.gameplayStack(sword)));
                    }
                    tile.getClass().getMethod("handleInput").invoke(tile);return null;
                });
                ((LegacyBlockEntity)player.serverLevel().getBlockEntity(advanced)).markLegacyDirty();
            });
            return true;
        });
        steps.add(mc->{
            var screen=screen(mc);var filled=cabinetSlots(screen).stream().filter(slot->!slot.getItem().isEmpty()).toList();
            if(filled.size()!=60)return false;
            visibleBeforeScroll=visible(screen);
            if(visibleBeforeScroll.size()!=54)throw new IllegalStateException("Original page should show 54 of 60 kinds, shows "+visibleBeforeScroll.size());
            double x=screen.getGuiLeft()+80,y=screen.getGuiTop()+60;
            for(int notch=0;notch<20;notch++)screen.mouseScrolled(x,y,-1);
            return true;
        });
        steps.add(mc->{
            var screen=screen(mc);var after=visible(screen);
            if(after.size()!=54||after.equals(visibleBeforeScroll))throw new IllegalStateException("Mouse wheel did not scroll the original page");
            visibleAfterScroll=after;var revealed=new HashSet<>(after);revealed.removeAll(visibleBeforeScroll);
            proof.put("wheelScrollRevealed",revealed.size());
            dragScrollbar(screen,144,0);return true;
        });
        steps.add(mc->{
            var screen=screen(mc);
            if(!visible(screen).equals(visibleBeforeScroll))throw new IllegalStateException("Dragging the original scrollbar did not return to the first page");
            dragScrollbar(screen,0,150);return true;
        });
        steps.add(mc->{
            var screen=screen(mc);
            if(!visible(screen).equals(visibleAfterScroll))throw new IllegalStateException("Dragging the original scrollbar did not reach the last page");
            proof.put("scrollbarDrag",true);
            var revealed=new HashSet<>(visibleAfterScroll);revealed.removeAll(visibleBeforeScroll);
            mouse(screen,screen.getMenu().slots.get(revealed.iterator().next()));return true;
        });
        steps.add(mc->{
            carried(mc,Items.IRON_SWORD,1);
            server(mc,player->{
                Object tile=((LegacyBlockEntity)player.serverLevel().getBlockEntity(advanced)).legacy();
                int kinds=content.services().items().inLegacy(()->(int)((List<?>)tile.getClass().getField("itemSlots").get(tile)).stream().filter(Objects::nonNull).count());
                if(kinds!=59)throw new IllegalStateException("Scrolled extraction did not remove one original stack: "+kinds);
            });
            proof.put("scrolledMouseExtract",true);click(mc,mc.player.containerMenu.slots.size()-36,0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{carried(mc,Items.AIR,0);mc.player.closeContainer();return true;});
    }
    private interface ServerAction{void run(ServerPlayer player) throws Exception;}
    private void server(Minecraft mc,ServerAction action) {
        pending=mc.getSingleplayerServer().submit(()->{
            try{action.run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}
            catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException(failure);}
            return true;
        });
    }
    private void open(Minecraft mc,BlockPos position) {
        server(mc,player->{
            var level=player.serverLevel();
            if(!level.getBlockState(position).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false)).consumesAction()||!(player.containerMenu instanceof LegacyDeviceMenu))
                throw new IllegalStateException("Filing cabinet did not open its original menu at "+position);
        });
    }
    private void stored(Minecraft mc,BlockPos position,List<ItemStack> expected) {
        server(mc,player->{
            Object tile=((LegacyBlockEntity)player.serverLevel().getBlockEntity(position)).legacy();var services=content.services();
            var actual=services.items().inLegacy(()->{
                var result=new ArrayList<ItemStack>();
                for(Object stack:(List<?>)tile.getClass().getField("itemSlots").get(tile))if(stack!=null)result.add(content.gameplayStack(services.items().snapshot(stack)));
                return result;
            });
            boolean same=actual.size()==expected.size();
            for(int index=0;same&&index<actual.size();index++)same=actual.get(index).is(expected.get(index).getItem())&&actual.get(index).getCount()==expected.get(index).getCount();
            if(!same)throw new IllegalStateException("Original cabinet holds "+actual+", expected "+expected);
        });
    }
    private static LegacyDeviceScreen screen(Minecraft mc){if(mc.screen instanceof LegacyDeviceScreen screen)return screen;throw new IllegalStateException("Original cabinet GUI is not open");}
    private static int hotbar(Minecraft mc,int index){return mc.player.containerMenu.slots.size()-9+index;}
    private static List<net.minecraft.world.inventory.Slot> cabinetSlots(LegacyDeviceScreen screen){var slots=screen.getMenu().slots;return slots.subList(0,slots.size()-36);}
    private static Set<Integer> visible(LegacyDeviceScreen screen) {
        var result=new HashSet<Integer>();
        for(var slot:cabinetSlots(screen))if(slot.isActive()&&!slot.getItem().isEmpty())result.add(slot.index);
        return result;
    }
    /** The open device menu's player slots: the host Inventory itself resynchronizes when the menu closes. */
    private static int playerCount(Minecraft mc,Item item) {
        var slots=mc.player.containerMenu.slots;int count=0;
        for(var slot:slots.subList(slots.size()-36,slots.size()))if(slot.getItem().is(item))count+=slot.getItem().getCount();
        return count;
    }
    private static void click(Minecraft mc,int slot,int button,ClickType type){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player);}
    /** A real host mouse press/release at the slot's current position: native hit-testing must match the original layout. */
    private static void mouse(LegacyDeviceScreen screen,net.minecraft.world.inventory.Slot slot) {
        double x=screen.getGuiLeft()+slot.x+8,y=screen.getGuiTop()+slot.y+8;
        screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
    }
    /** Press on the original scrollbar track, drag along it and release; offsets are the GUI's own scroll units. */
    private static void dragScrollbar(LegacyDeviceScreen screen,int from,int to) {
        double y=screen.getGuiTop()+131,start=screen.getGuiLeft()+17+from,end=screen.getGuiLeft()+17+to;
        screen.mouseClicked(start,y,0);screen.mouseDragged(end,y,0,end-start,0);screen.mouseReleased(end,y,0);
    }
    private static void carried(Minecraft mc,Item item,int count) {
        var stack=mc.player.containerMenu.getCarried();
        if(item==Items.AIR?!stack.isEmpty():!stack.is(item)||stack.getCount()!=count)throw new IllegalStateException("Cursor holds "+stack+", expected "+count+" "+item);
    }
    private int legacyCount(LegacyDeviceScreen screen,int index) throws Exception {
        Object container=screen.originalGui().getClass().getField("field_147002_h").get(screen.originalGui());
        Object slot=((List<?>)container.getClass().getField("field_75151_b").get(container)).get(index);
        Object stack=slot.getClass().getMethod("func_75211_c").invoke(slot);
        return stack==null?0:stack.getClass().getField("field_77994_a").getInt(stack);
    }
    private void finish(Minecraft mc,Throwable failure) {
        if(failure!=null){
            proof.put("status","FAIL");proof.put("step",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_FILING_SMOKE_FAIL",failure);
            if(mc.getSingleplayerServer()!=null&&basic!=null)try {
                proof.put("originalContents",mc.getSingleplayerServer().submit(()->{try{
                    var result=new LinkedHashMap<String,Object>();var level=mc.getSingleplayerServer().overworld();var services=content.services();
                    for(var position:List.of(basic,advanced)) {
                        Object tile=((LegacyBlockEntity)level.getBlockEntity(position)).legacy();
                        result.put(position.toShortString(),services.items().inLegacy(()->{var stacks=new ArrayList<String>();for(Object stack:(List<?>)tile.getClass().getField("itemSlots").get(tile))stacks.add(String.valueOf(stack==null?null:services.items().snapshot(stack)));return stacks;}));
                    }
                    result.put("player",mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().items.toString());
                    result.put("oreIds",services.items().inLegacy(()->{
                        var ores=services.loader().loadClass("net.minecraftforge.oredict.OreDictionary");var stackType=services.loader().loadClass("net.minecraft.item.ItemStack");
                        var ids=new LinkedHashMap<String,String>();
                        for(var item:List.of(Items.COBBLESTONE,Items.DIRT,Items.IRON_SWORD))ids.put(item.toString(),Arrays.toString((int[])ores.getMethod("getOreIDs",stackType).invoke(null,services.items().materialize(content.gameplayStack(new ItemStack(item))))));
                        return ids;
                    }));
                    return result;
                }catch(Exception error){return Map.of("error",error.toString());}}).get(10,TimeUnit.SECONDS));
            }catch(Exception error){proof.put("originalContents",error.toString());}
        }
        done=true;
        try{var output=mc.gameDirectory.toPath().resolve("logs/mfcl/filing-smoke.json");java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_FILING_SMOKE_RESULT {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
