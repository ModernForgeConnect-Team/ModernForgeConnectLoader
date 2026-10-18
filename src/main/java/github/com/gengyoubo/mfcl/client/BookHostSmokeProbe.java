package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** Native right-click and registered research sync acceptance in a fresh world. */
final class BookHostSmokeProbe {
    private final ModernContentBridge bridge;
    private int stage,frames;
    private java.util.concurrent.CompletableFuture<?> pending;
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private BookHostSmokeProbe(ModernContentBridge bridge){this.bridge=bridge;}
    static void install(ModernContentBridge bridge){MinecraftForge.EVENT_BUS.addListener(new BookHostSmokeProbe(bridge)::tick);}
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;
        var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new IllegalStateException("Book acceptance timed out at stage "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);stage=1;
                mc.createWorldOpenFlows().createFreshLevel("mfcl-book-smoke-"+System.currentTimeMillis(),new LevelSettings("MFCL original book",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            } else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null) {
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    player.getInventory().setItem(0,bridge.stack("Thaumcraft:ItemThaumonomicon",0,1,""));player.inventoryMenu.broadcastChanges();return true;
                });stage=2;
            } else if(stage==2&&pending.isDone()&&mc.player!=null&&bridge.snapshot(mc.player.getMainHandItem())!=null) {
                pending.join();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=3;frames=0;
            } else if(stage==3&&mc.screen!=null&&++frames>25) {
                Object old=ClientContainerBridge.active().legacyScreen(mc.screen);
                if(!old.getClass().getName().equals("thaumcraft.client.gui.GuiResearchBrowser"))throw new IllegalStateException("Right click did not open original research browser");
                var commands=ClientContainerBridge.active().commands(mc.screen);
                if(commands.stream().noneMatch(LegacyGuiGraphics.Texture.class::isInstance))throw new IllegalStateException("Research background was not drawn");
                var completed=(Map<?,?>)old.getClass().getField("completedResearch").get(null);
                if(!completed.containsKey(mc.player.getName().getString()))return;
                proof.put("nativeRightClickOpenedOriginalBook",true);proof.put("registeredResearchPacketDelivered",true);proof.put("capturedCommands",commands.size());
                String name=(String)bridge.services().loader().loadClass("net.minecraft.util.StatCollector").getMethod("func_74838_a",String.class).invoke(null,"tc.research_name.ORE");
                if(name.equals("tc.research_name.ORE"))throw new IllegalStateException("Original research language was not loaded");
                proof.put("localizedResearchName",name);
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-thaumonomicon.png",mc.getMainRenderTarget(),message->{});
                openEntry(mc,old,(List<?>)completed.get(mc.player.getName().getString()));stage=10;frames=0;
            } else if(stage==10&&++frames>10) {
                Object old=ClientContainerBridge.active().legacyScreen(mc.screen);
                if(!old.getClass().getName().equals("thaumcraft.client.gui.GuiResearchRecipe"))throw new IllegalStateException("Original research entry did not open");
                ClientContainerBridge.active().commands(mc.screen);proof.put("originalResearchEntryOpened",true);
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-thaumonomicon-entry.png",mc.getMainRenderTarget(),message->{});
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);stage=11;frames=0;
            } else if(stage==11&&++frames>10) {
                if(!ClientContainerBridge.active().legacyScreen(mc.screen).getClass().getName().equals("thaumcraft.client.gui.GuiResearchBrowser"))throw new IllegalStateException("Original page return did not restore browser");
                int x=(mc.screen.width-256)/2-12,y=(mc.screen.height-230)/2+36;
                mc.screen.mouseClicked(x,y,0);mc.screen.mouseReleased(x,y,0);stage=4;frames=0;
            } else if(stage==4&&++frames>10) {
                ClientContainerBridge.active().commands(mc.screen);proof.put("originalCategoryNavigation",true);
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);stage=5;
            } else if(stage==5&&mc.screen==null) {
                proof.put("originalCloseKey",true);proof.put("status","PASS");finish(mc,null);
            }
        } catch(Throwable failure){finish(mc,failure);}
    }
    private void openEntry(Minecraft mc,Object old,List<?> completed) throws Exception {
        var researchField=old.getClass().getDeclaredField("research");researchField.setAccessible(true);
        int mapX=number(old,"field_74117_m"),mapY=number(old,"field_74115_n");
        mapX=Math.max(number(old,"guiMapTop"),Math.min(mapX,number(old,"guiMapBottom")-1));
        mapY=Math.max(number(old,"guiMapLeft"),Math.min(mapY,number(old,"guiMapRight")-1));
        for(Object research:(Iterable<?>)researchField.get(old)) {
            String key=(String)research.getClass().getField("key").get(research);
            if(!completed.contains(key)||(boolean)research.getClass().getMethod("isVirtual").invoke(research))continue;
            int x=research.getClass().getField("displayColumn").getInt(research)*24-mapX;
            int y=research.getClass().getField("displayRow").getInt(research)*24-mapY;
            if(x<0||x>210||y<0||y>180)continue;
            x+=(mc.screen.width-256)/2+27;y+=(mc.screen.height-230)/2+28;
            var graphics=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
            net.minecraftforge.client.ForgeHooksClient.drawScreen(mc.screen,graphics,x,y,0);graphics.flush();
            mc.screen.mouseClicked(x,y,0);mc.screen.mouseReleased(x,y,0);proof.put("openedResearchKey",key);return;
        }
        throw new IllegalStateException("No visible original initial research entry was unlocked");
    }
    private int number(Object old,String name) throws Exception {
        var field=old.getClass().getDeclaredField(name);field.setAccessible(true);return (int)Math.floor(((Number)field.get(old)).doubleValue());
    }
    private void finish(Minecraft mc,Throwable failure) {
        stage=99;
        if(failure!=null){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_BOOK_SMOKE_FAIL",failure);}
        try {
            var path=mc.gameDirectory.toPath().resolve("logs/mfcl/book-smoke.json");java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        }catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_BOOK_SMOKE_RESULT: {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
