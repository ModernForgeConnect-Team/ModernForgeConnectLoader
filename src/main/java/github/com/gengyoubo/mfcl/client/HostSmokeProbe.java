package github.com.gengyoubo.mfcl.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Opt-in developer smoke check; never enabled during a normal game launch. */
public final class HostSmokeProbe {
    private static boolean completed;
    private HostSmokeProbe() {}

    public static void install() { MinecraftForge.EVENT_BUS.addListener(HostSmokeProbe::tick); }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (!completed && event.phase == TickEvent.Phase.END && Minecraft.getInstance().screen instanceof TitleScreen) {
            completed = true;
            LogUtils.getLogger().info("MFCL_HOST_SMOKE_PASS: Forge 1.20.1 reached the title screen; legacy gameplay is not validated");
            Minecraft.getInstance().stop();
        }
    }
}
