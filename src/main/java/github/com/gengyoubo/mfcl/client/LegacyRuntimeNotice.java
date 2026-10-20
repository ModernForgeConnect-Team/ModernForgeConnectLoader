package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.legacy.LegacyModCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/** Keeps a failed legacy runtime visible on the title screen instead of only in the log. */
final class LegacyRuntimeNotice {
    private static final int MAX_DETAIL_LINES = 12;
    private final List<String> skipped;
    private final String failure;
    private boolean toastShown;

    private LegacyRuntimeNotice(List<String> skipped, String failure) { this.skipped = skipped; this.failure = failure; }

    static void install(LegacyModCatalog catalog) {
        List<String> skipped = catalog.entries().stream().filter(entry -> !entry.loaded())
                .map(entry -> entry.mod().name().isBlank() ? entry.mod().id() : entry.mod().name()).toList();
        if (skipped.isEmpty() || catalog.failure().isBlank()) return;
        var notice = new LegacyRuntimeNotice(skipped, catalog.failure());
        MinecraftForge.EVENT_BUS.addListener(notice::opened);
        MinecraftForge.EVENT_BUS.addListener(notice::render);
    }

    private Component title() {
        return Component.translatable("mfcl.notice.skipped", skipped.size());
    }

    private void opened(ScreenEvent.Init.Post event) {
        if (toastShown || !(event.getScreen() instanceof TitleScreen)) return;
        toastShown = true;
        var mc = Minecraft.getInstance();
        mc.getToasts().addToast(SystemToast.multiline(mc, SystemToast.SystemToastIds.PACK_LOAD_FAILURE, title(),
                Component.translatable("mfcl.notice.toast")));
    }

    private void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;
        var font = Minecraft.getInstance().font;
        int width = Math.max(120, Math.min(screen.width - 8, 420));
        var lines = new ArrayList<FormattedCharSequence>();
        lines.addAll(font.split(title().copy().withStyle(style -> style.withColor(0xFF5555)), width));
        lines.addAll(font.split(Component.translatable("mfcl.notice.mods", String.join(", ", skipped)), width));
        var detail = font.split(Component.literal(failure), width);
        lines.addAll(detail.subList(0, Math.min(detail.size(), MAX_DETAIL_LINES)));
        if (detail.size() > MAX_DETAIL_LINES) lines.addAll(font.split(Component.translatable("mfcl.notice.more"), width));
        var graphics = event.getGuiGraphics();
        int height = lines.size() * font.lineHeight + 4;
        graphics.fill(2, 2, 2 + width + 4, 2 + height, 0xC0000000);
        int y = 4;
        for (var line : lines) { graphics.drawString(font, line, 4, y, 0xFFFFFF, false); y += font.lineHeight; }
    }
}
