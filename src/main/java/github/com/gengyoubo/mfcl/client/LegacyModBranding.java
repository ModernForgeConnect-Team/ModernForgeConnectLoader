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
    private final Field brandings, noMinecraft;
    private List<String> lastBrandings;
    private String lastLabel;

    private LegacyModBranding(LegacyModCatalog catalog) throws ReflectiveOperationException {
        this.catalog = catalog;
        brandings = BrandingControl.class.getDeclaredField("brandings");
        noMinecraft = BrandingControl.class.getDeclaredField("brandingsNoMC");
        brandings.setAccessible(true); noMinecraft.setAccessible(true);
    }

    public static void install(LegacyModCatalog catalog) {
        if (catalog.loadedCount() == 0) return;
        try {
            var branding = new LegacyModBranding(catalog);
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event) -> {
                if (event.getScreen() instanceof TitleScreen) branding.refresh();
            });
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot integrate runtime counts with Forge 47 title branding", failure);
        }
    }

    private void refresh() {
        int modern = ModList.get().size();
        String label = Component.translatable("mfcl.mods.total_loaded", catalog.totalLoaded(modern),
                catalog.targetMinecraft(), modern, catalog.sourceMinecraft(), catalog.loadedCount()).getString();
        try {
            // Calling the public reader initializes branding after a language/resource reload.
            BrandingControl.forEachLine(true, false, (index, line) -> { });
            @SuppressWarnings("unchecked") var current = (List<String>) brandings.get(null);
            if (current == lastBrandings && label.equals(lastLabel)) return;
            String nativeLabel = ForgeI18n.parseMessage("fml.menu.loadingmods", modern);
            var combined = new ArrayList<>(current);
            for (int i = 0; i < combined.size(); i++) {
                String line = combined.get(i);
                if (line.equals(nativeLabel) || line.equals(lastLabel)) {
                    combined.set(i, label);
                    var snapshot = List.copyOf(combined);
                    brandings.set(null, snapshot);
                    noMinecraft.set(null, snapshot.subList(1, snapshot.size()));
                    lastBrandings = snapshot; lastLabel = label;
                    return;
                }
            }
            throw new IllegalStateException("Forge's mod count branding was not found");
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot update Forge 47 title branding", failure);
        }
    }
}
