package github.com.gengyoubo.mfcl.compat.nei;

import github.com.gengyoubo.mfcl.client.ModernRecipeProjection;
import github.com.gengyoubo.mfcl.client.ModernItemMapping;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Opt-in developer smoke check; never enabled during a normal game launch. */
public final class NeiHostSmokeProbe {
    private static boolean completed;
    private static int stage, frames;
    private static volatile boolean screenshotSaved;
    private static NeiClientUi.Browser browser;
    private NeiHostSmokeProbe() {}

    public static void install() { MinecraftForge.EVENT_BUS.addListener(NeiHostSmokeProbe::tick); }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (completed || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (Boolean.getBoolean("mfcl.test.neiUiSmoke")) {
            try {
                if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    var bridge = java.util.Objects.requireNonNull(NeiClientUi.activeBridge(), "Embedded NEI data bridge did not start");
                    if (Boolean.getBoolean("mfcl.test.neiWorldSmoke")) {
                        stage = 10;
                        var settings = new net.minecraft.world.level.LevelSettings("MFCL NEI verification", net.minecraft.world.level.GameType.SURVIVAL,
                                false, net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                        mc.options.renderDistance().set(2); mc.options.simulationDistance().set(2);
                        mc.createWorldOpenFlows().createFreshLevel("mfcl-nei-smoke-" + System.currentTimeMillis(), settings,
                                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false), access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                        .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                    } else { browser = new NeiClientUi.Browser(mc.screen, bridge); mc.setScreen(browser); stage = 1; frames = 0; }
                } else if (stage == 10 && mc.player != null && mc.level != null && mc.screen == null) {
                    if (ModernRecipeProjection.lastResult() == null || ModernRecipeProjection.lastResult().projected() == 0)
                        throw new IllegalStateException("Modern recipe sync did not reach the original NEI handlers");
                    var inventory = new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player); mc.setScreen(inventory);
                    if (github.com.gengyoubo.mfcl.bridge.LegacyScreens.guiBridgeEnabled()) {
                        Object search=NeiClientUi.activeBridge().data().loader().loadClass("codechicken.nei.LayoutManager").getField("searchField").get(null);
                        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,search.getClass().getField("x").getInt(search)+4,search.getClass().getField("y").getInt(search)+4,1));
                        stage=20;frames=0;return;
                    }
                    var button = inventory.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("NEI"))
                            .map(w -> (net.minecraft.client.gui.components.Button) w).findFirst().orElseThrow(() -> new IllegalStateException("Inventory NEI button missing"));
                    button.onPress();
                    if (!(mc.screen instanceof NeiClientUi.Browser)) throw new IllegalStateException("Inventory button did not open NEI");
                    browser = (NeiClientUi.Browser) mc.screen; stage = 1; frames = 0;
                } else if(stage==20 && ++frames>8) {
                    var commands=github.com.gengyoubo.mfcl.client.ClientContainerBridge.active().commands(mc.screen);
                    if(commands.stream().noneMatch(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::isInstance)
                            ||commands.stream().noneMatch(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Text.class::isInstance))throw new IllegalStateException("Original NEI did not draw through generic container bridge");
                    LogUtils.getLogger().info("MFCL_ORIGINAL_NEI_GUI_DRAW_PASS: {} captured commands on actual host inventory",commands.size());
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-original-nei-overlay.png",mc.getMainRenderTarget(),c->screenshotSaved=true);stage=21;
                } else if(stage==21 && screenshotSaved) {
                    Object search=NeiClientUi.activeBridge().data().loader().loadClass("codechicken.nei.LayoutManager").getField("searchField").get(null);
                    int x=search.getClass().getField("x").getInt(search)+4,y=search.getClass().getField("y").getInt(search)+4;
                    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,x,y,1));
                    for(char c:"crafting".toCharArray())MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.CharacterTyped.Pre(mc.screen,c,0));
                    if(!search.getClass().getMethod("text").invoke(search).equals("crafting"))throw new IllegalStateException("Original search field did not receive host input");
                    stage=22;frames=0;
                } else if(stage==22 && ++frames>12) {
                    var commands=github.com.gengyoubo.mfcl.client.ClientContainerBridge.active().commands(mc.screen);
                    if(commands.stream().filter(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::isInstance).count()>4)throw new IllegalStateException("Original item panel did not filter");
                    LogUtils.getLogger().info("MFCL_ORIGINAL_NEI_GUI_SEARCH_PASS: original search field and item panel filtered through host input events");
                    var item=commands.stream().filter(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::isInstance).map(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::cast).filter(i->!i.overlay()).findFirst().orElseThrow();
                    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,item.x()+4,item.y()+4,0));
                    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.screen,item.x()+4,item.y()+4,0));
                    stage=24;frames=0;
                } else if(stage==24&&++frames>10) {
                    var active=github.com.gengyoubo.mfcl.client.ClientContainerBridge.active();Object old=active.legacyScreen(mc.screen);
                    if(!old.getClass().getName().equals("codechicken.nei.recipe.GuiCraftingRecipe"))throw new IllegalStateException("Original recipe page was not opened: "+old.getClass());
                    if(mc.screen.isPauseScreen())throw new IllegalStateException("Old container pause policy was not preserved");
                    var commands=active.commands(mc.screen);if(commands.isEmpty())throw new IllegalStateException("Original recipe page did not draw");
                    LogUtils.getLogger().info("MFCL_ORIGINAL_NEI_RECIPE_GUI_PASS: actual {} rendered {} commands",old.getClass().getName(),commands.size());
                    screenshotSaved=false;net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-original-nei-recipe.png",mc.getMainRenderTarget(),c->screenshotSaved=true);stage=25;
                } else if(stage==25&&screenshotSaved) {
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);stage=26;frames=0;
                } else if(stage==26&&++frames>8) {
                    if(!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen))throw new IllegalStateException("Original recipe ESC did not return to host inventory");
                    var item=github.com.gengyoubo.mfcl.client.ClientContainerBridge.active().commands(mc.screen).stream().filter(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::isInstance).map(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::cast).filter(i->!i.overlay()).findFirst().orElseThrow();
                    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,item.x()+4,item.y()+4,1));
                    MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.screen,item.x()+4,item.y()+4,1));
                    stage=27;frames=0;
                } else if(stage==27&&++frames>8) {
                    var active=github.com.gengyoubo.mfcl.client.ClientContainerBridge.active();Object old=active.legacyScreen(mc.screen);
                    if(!old.getClass().getName().equals("codechicken.nei.recipe.GuiUsageRecipe")||active.commands(mc.screen).isEmpty())throw new IllegalStateException("Original usage GUI did not open and render");
                    LogUtils.getLogger().info("MFCL_ORIGINAL_NEI_USAGE_GUI_PASS");
                    mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);stage=28;frames=0;
                } else if(stage==28&&++frames>8) {
                    if(!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen))throw new IllegalStateException("Original usage ESC did not return to inventory");
                    mc.screen.resize(mc,mc.screen.width,mc.screen.height);stage=29;frames=0;
                } else if(stage==29&&++frames>8) {
                    if(github.com.gengyoubo.mfcl.client.ClientContainerBridge.active().commands(mc.screen).isEmpty())throw new IllegalStateException("Original GUI did not survive resize");
                    mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());stage=23;
                } else if(stage==23&&mc.getSingleplayerServer()==null&&mc.screen instanceof TitleScreen) {
                    LogUtils.getLogger().info("MFCL_ORIGINAL_NEI_GUI_SMOKE_PASS");completed=true;mc.stop();
                } else if (stage == 1 && browser.renderedFrames > 3) { browser.smokeSearch(); stage = 2; frames = browser.renderedFrames; }
                else if (stage == 2 && browser.renderedFrames > frames + 3) {
                    int recipes = browser.smokeRecipeClick(false), usages = browser.smokeRecipeClick(true);
                    browser.smokeRecipeClick(false);
                    LogUtils.getLogger().info("MFCL_NEI_QUERY_SMOKE_PASS: original NEI search, recipe and usage callbacks: recipes={} usages={}", recipes, usages);
                    stage = 3; frames = browser.renderedFrames;
                } else if (stage == 3 && browser.renderedFrames > frames + 3) {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mfcl-nei-smoke.png", mc.getMainRenderTarget(), component -> screenshotSaved = true);
                    stage = 4;
                } else if (stage == 4 && screenshotSaved) {
                    browser.onClose();
                    if (Boolean.getBoolean("mfcl.test.neiWorldSmoke")) {
                        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) throw new IllegalStateException("NEI did not return to the real inventory");
                        mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); stage = 11;
                    } else {
                        if (!(mc.screen instanceof TitleScreen)) throw new IllegalStateException("NEI browser did not return to its parent screen");
                        LogUtils.getLogger().info("MFCL_NEI_UI_SMOKE_PASS: embedded original NEI data, rendered browser, recipe/usage clicks and screen return; no world session was tested");
                        completed = true; mc.stop();
                    }
                } else if (stage == 11 && mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen) {
                    LogUtils.getLogger().info("MFCL_NEI_WORLD_SMOKE_PASS: real world join, host recipe sync, inventory NEI button, search, recipe/usage clicks, return and world disconnect");
                    completed = true; mc.stop();
                }
            } catch (Throwable failure) {
                completed = true; LogUtils.getLogger().error("MFCL_NEI_UI_SMOKE_FAIL", failure); mc.stop();
            }
            return;
        }
        if (mc.screen instanceof TitleScreen) {
            completed = true;
            LogUtils.getLogger().info("MFCL_HOST_SMOKE_PASS: Forge 1.20.1 reached the title screen; legacy gameplay is not validated");
            Minecraft.getInstance().stop();
        }
    }
}
