package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.legacy.LegacyModCatalog;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.ForgeI18n;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.internal.BrandingControl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Keeps Forge's cached title branding consistent with the runtime catalog, including resource reloads. */
public final class LegacyModBranding {
    private final LegacyModCatalog catalog;
    private final String mfclLine, legacyForgeLine;
    private final Field brandings, noMinecraft;
    private List<String> lastBrandings;
    private String lastLabel;

    private LegacyModBranding(LegacyModCatalog catalog, java.util.Map<String, String> versions) throws ReflectiveOperationException {
        this.catalog = catalog;
        mfclLine = "MFCL " + ModList.get().getMods().stream().filter(mod -> mod.getModId().equals("mfcl")).findFirst().orElseThrow().getVersion();
        legacyForgeLine = "Forge " + catalog.sourceMinecraft() + " " + versions.getOrDefault("Forge", github.com.gengyoubo.mfcl.legacy.LegacyRuntimeCheck.FORGE);
        brandings = BrandingControl.class.getDeclaredField("brandings");
        noMinecraft = BrandingControl.class.getDeclaredField("brandingsNoMC");
        brandings.setAccessible(true); noMinecraft.setAccessible(true);
    }

    public static void install(LegacyModCatalog catalog, java.util.Map<String, String> versions) {
        try {
            var branding = new LegacyModBranding(catalog, versions);
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event) -> {
                if (event.getScreen() instanceof TitleScreen) branding.refresh();
            });
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot integrate runtime counts with Forge 47 title branding", failure);
        }
    }

    private void refresh() {
        int modern = ModList.get().size();
        String label = catalog.loadedCount() == 0 ? ForgeI18n.parseMessage("fml.menu.loadingmods", modern)
                : Component.translatable("mfcl.mods.total_loaded", catalog.totalLoaded(modern),
                catalog.targetMinecraft(), modern, catalog.sourceMinecraft(), catalog.loadedCount()).getString();
        try {
            // Calling the public reader initializes branding after a language/resource reload.
            BrandingControl.forEachLine(true, false, (index, line) -> { });
            @SuppressWarnings("unchecked") var current = (List<String>) brandings.get(null);
            if (current == lastBrandings && label.equals(lastLabel)) return;
            @SuppressWarnings("unchecked") var currentShort = (List<String>) noMinecraft.get(null);
            String nativeLabel = ForgeI18n.parseMessage("fml.menu.loadingmods", modern);
            var snapshot = update(current, nativeLabel, label);
            var shortSnapshot = update(currentShort, nativeLabel, label);
            brandings.set(null, snapshot);
            noMinecraft.set(null, shortSnapshot);
            lastBrandings = snapshot; lastLabel = label;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot update Forge 47 title branding", failure);
        }
    }

    private List<String> update(List<String> current, String nativeLabel, String label) {
        var lines = new ArrayList<>(current);
        lines.remove(mfclLine); lines.remove(legacyForgeLine);
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).equals(nativeLabel) || lines.get(i).equals(lastLabel)) {
            lines.set(i, label); found = true; break;
        }
        if (!found) throw new IllegalStateException("Forge's mod count branding was not found");
        // TitleScreen renders this list in reverse from the bottom, so the first entries appear above Forge.
        lines.add(0, mfclLine); lines.add(0, legacyForgeLine);
        return List.copyOf(lines);
    }
}
