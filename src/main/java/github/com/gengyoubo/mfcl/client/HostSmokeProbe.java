package github.com.gengyoubo.mfcl.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Opt-in developer smoke check; never enabled during a normal game launch. */
public final class HostSmokeProbe {
    private static boolean completed;
    private static int stage, frames;
    private static volatile boolean screenshotSaved;
    private static NeiClientUi.Browser browser;
    private HostSmokeProbe() {}

    public static void install() { MinecraftForge.EVENT_BUS.addListener(HostSmokeProbe::tick); }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (completed || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (Boolean.getBoolean("mfcl.test.neiUiSmoke")) {
            try {
                if (stage == 0 && mc.screen instanceof TitleScreen) {
                    var bridge = java.util.Objects.requireNonNull(NeiClientUi.activeBridge(), "Embedded NEI data bridge did not start");
                    browser = new NeiClientUi.Browser(mc.screen, bridge); mc.setScreen(browser); stage = 1; frames = 0;
                } else if (stage == 1 && ++frames > 3) { browser.smokeSearch(); stage = 2; frames = 0; }
                else if (stage == 2 && ++frames > 3) {
                    int recipes = browser.smokeRecipeClick(false), usages = browser.smokeRecipeClick(true);
                    browser.smokeRecipeClick(false);
                    LogUtils.getLogger().info("MFCL_NEI_QUERY_SMOKE_PASS: original NEI search, recipe and usage callbacks: recipes={} usages={}", recipes, usages);
                    stage = 3; frames = 0;
                } else if (stage == 3 && ++frames > 3) {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mfcl-nei-smoke.png", mc.getMainRenderTarget(), component -> screenshotSaved = true);
                    stage = 4;
                } else if (stage == 4 && screenshotSaved) {
                    browser.onClose();
                    if (!(mc.screen instanceof TitleScreen)) throw new IllegalStateException("NEI browser did not return to its parent screen");
                    LogUtils.getLogger().info("MFCL_NEI_UI_SMOKE_PASS: embedded original NEI data, rendered browser, recipe/usage clicks and screen return; no world session was tested");
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
