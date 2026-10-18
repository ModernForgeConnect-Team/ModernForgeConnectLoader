package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.Tesselator;
import github.com.gengyoubo.mfcl.legacy.LegacyMod;
import github.com.gengyoubo.mfcl.legacy.LegacyRuntimeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.client.gui.widget.ModListWidget;
import net.minecraftforge.client.gui.widget.ScrollPanel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

/** Adds display entries to Forge's screen, without registering legacy containers in modern FML. */
public final class LegacyModListScreen extends ModListScreen {
    private final List<LegacyInfo> legacyMods;
    private ModListWidget list;
    private DetailsPanel details;
    private LegacyInfo selection;
    private int renderedFrames;

    public static void install(LegacyRuntimeService.Report report, Set<String> loadedIds) {
        List<LegacyInfo> entries = new ArrayList<>();
        for (var archive : report.archives()) for (var mod : archive.mods()) {
            entries.add(new LegacyInfo(mod, Path.of(archive.file()).getFileName().toString(), report.sourceMinecraft(),
                    loadedIds.contains(mod.id()) && report.legacyFmlStarted()));
        }
        if (entries.isEmpty()) return;
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> {
            if (event.getNewScreen() != null && event.getNewScreen().getClass() == ModListScreen.class)
                event.setNewScreen(new LegacyModListScreen(event.getCurrentScreen(), entries));
        });
        if (Boolean.getBoolean("mfcl.test.modListSmoke")) ModListSmokeProbe.install();
    }

    private LegacyModListScreen(Screen parent, List<LegacyInfo> entries) {
        super(parent);
        legacyMods = List.copyOf(entries);
        // Forge 47 exposes list construction but keeps the filter/sort source private.
        // Change only this screen's two snapshots; the global ModList remains untouched.
        try {
            Field mods = field("mods"), unsorted = field("unsortedMods");
            @SuppressWarnings("unchecked") List<IModInfo> original = (List<IModInfo>) mods.get(this);
            List<IModInfo> combined = new ArrayList<>(original); combined.addAll(legacyMods);
            mods.set(this, combined); unsorted.set(this, List.copyOf(combined));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot integrate legacy entries into Forge 47's mod screen", failure);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = ModListScreen.class.getDeclaredField(name); field.setAccessible(true); return field;
    }

    @Override public void init() {
        super.init();
        list = children().stream().filter(ModListWidget.class::isInstance).map(ModListWidget.class::cast).findFirst().orElseThrow();
        details = addRenderableWidget(new DetailsPanel(list.getRight() + 6, width - list.getRight() - 12, height - 38));
        details.set(selection);
    }

    @Override public void setSelected(ModListWidget.ModEntry entry) {
        if (entry != null && entry.getInfo() instanceof LegacyInfo info) {
            super.setSelected(null);
            selection = selection == info ? null : info;
            details.set(selection);
            list.setSelected(selection == null ? null : entry);
        } else {
            selection = null;
            if (details != null) details.set(null);
            super.setSelected(entry);
        }
    }

    @Override public void tick() {
        super.tick();
        if (selection != null) {
            var selected = list.children().stream().filter(e -> e.getInfo() == selection).findFirst().orElse(null);
            list.setSelected(selected);
            if (selected == null) { selection = null; details.set(null); }
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderedFrames++;
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (details != null && details.isMouseOver(x, y)) return details.mouseScrolled(x, y, delta);
        return super.mouseScrolled(x, y, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (details != null && details.isMouseOver(x, y)) return details.mouseClicked(x, y, button);
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (details != null && details.visible && details.mouseDragged(x, y, button, dx, dy)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    private final class DetailsPanel extends ScrollPanel {
        private List<FormattedCharSequence> lines = List.of();
        private boolean visible;
        DetailsPanel(int left, int width, int height) { super(Minecraft.getInstance(), width, height, 6, left); }
        void set(LegacyInfo info) {
            visible = info != null; scrollDistance = 0;
            if (info == null) { lines = List.of(); return; }
            List<Component> text = List.of(Component.literal(info.getDisplayName()),
                    Component.translatable("mfcl.mods.version", info.mod.version()),
                    Component.translatable("mfcl.mods.game_version", info.gameVersion),
                    Component.literal("Mod ID: " + info.mod.id()),
                    Component.translatable("mfcl.mods.status", Component.translatable(info.loaded ? "mfcl.mods.loaded" : "mfcl.mods.discovered")),
                    Component.translatable("mfcl.mods.loader"),
                    Component.translatable("mfcl.mods.file", info.file));
            List<FormattedCharSequence> wrapped = new ArrayList<>();
            for (Component line : text) wrapped.addAll(font.split(line, Math.max(20, width - 18)));
            lines = List.copyOf(wrapped);
        }
        @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
            if (visible) super.render(graphics, x, y, partialTick);
        }
        @Override public boolean isMouseOver(double x, double y) { return visible && super.isMouseOver(x, y); }
        @Override public boolean mouseScrolled(double x, double y, double delta) { return visible && super.mouseScrolled(x, y, delta); }
        @Override protected int getContentHeight() { return lines.size() * font.lineHeight + 12; }
        @Override protected void drawPanel(GuiGraphics graphics, int right, int y, Tesselator tess, int mouseX, int mouseY) {
            for (var line : lines) { graphics.drawString(font, line, left + 6, y, 0xFFFFFF, false); y += font.lineHeight; }
        }
        @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
        @Override public void updateNarration(NarrationElementOutput output) { }
    }

    /** Presentation metadata only: never inserted into the host's actual mod/container registries. */
    private record LegacyInfo(LegacyMod mod, String file, String gameVersion, boolean loaded) implements IModInfo {
        @Override public String getModId() { return mod.id(); }
        @Override public String getDisplayName() { return mod.name().isBlank() ? mod.id() : mod.name(); }
        @Override public ArtifactVersion getVersion() { return new DefaultArtifactVersion(mod.version().isBlank() ? "unknown" : mod.version()); }
        @Override public String getDescription() { return ""; }
        @Override public IModFileInfo getOwningFile() { return null; }
        @Override public List<? extends ModVersion> getDependencies() { return List.of(); }
        @Override public List<? extends ForgeFeature.Bound> getForgeFeatures() { return List.of(); }
        @Override public String getNamespace() { return mod.id(); }
        @Override public Map<String, Object> getModProperties() { return Map.of(); }
        @Override public Optional<URL> getUpdateURL() { return Optional.empty(); }
        @Override public Optional<URL> getModURL() { return Optional.empty(); }
        @Override public Optional<String> getLogoFile() { return Optional.empty(); }
        @Override public boolean getLogoBlur() { return false; }
        @Override public IConfigurable getConfig() {
            return new IConfigurable() {
                @Override public <T> Optional<T> getConfigElement(String... key) { return Optional.empty(); }
                @Override public List<? extends IConfigurable> getConfigList(String... key) { return List.of(); }
            };
        }
    }

    private static final class ModListSmokeProbe {
        private static int stage, frames;
        private static volatile boolean screenshotSaved;
        static void install() {
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent event) -> {
                if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || stage == 99) return;
                var mc = Minecraft.getInstance();
                try {
                    if (stage == 0 && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.getOverlay() == null) {
                        mc.setScreen(new ModListScreen(mc.screen)); stage = 1;
                    } else if (stage == 1 && mc.screen instanceof LegacyModListScreen screen && screen.renderedFrames > 3) {
                        for (var info : screen.legacyMods) require(screen.list.children().stream().anyMatch(e -> e.getInfo() == info), "Legacy entry missing");
                        require(net.minecraftforge.fml.ModList.get().getMods().stream().noneMatch(LegacyInfo.class::isInstance), "Legacy entry leaked into modern FML");
                        screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                                .map(net.minecraft.client.gui.components.Button.class::cast)
                                .filter(b -> b.getMessage().equals(Component.translatable("fml.menu.mods.a_to_z"))).findFirst().orElseThrow().onPress();
                        stage = 2; frames = screen.renderedFrames;
                    } else if (stage == 2 && mc.screen instanceof LegacyModListScreen screen && screen.renderedFrames > frames + 3) {
                        var names = screen.list.children().stream().map(e -> e.getInfo().getDisplayName().toLowerCase(Locale.ROOT)).toList();
                        require(names.equals(names.stream().sorted().toList()), "Combined list did not sort");
                        screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().setValue("not enough");
                        stage = 3; frames = screen.renderedFrames;
                    } else if (stage == 3 && mc.screen instanceof LegacyModListScreen screen && screen.renderedFrames > frames + 3) {
                        require(screen.list.children().size() == 1, "Legacy search did not filter native list");
                        screen.setSelected(screen.list.children().get(0));
                        require(screen.selection != null && screen.selection.loaded && screen.selection.gameVersion.equals("1.7.10"), "Legacy version or load status incorrect");
                        screen.resize(mc, screen.width, screen.height);
                        require(screen.details.visible, "Legacy details lost on resize");
                        screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().setValue("");
                        stage = 4; frames = screen.renderedFrames;
                    } else if (stage == 4 && mc.screen instanceof LegacyModListScreen screen && screen.renderedFrames > frames + 3) {
                        screen.setSelected(screen.list.children().stream().filter(e -> e.getInfo().getModId().equals("forge")).findFirst().orElseThrow());
                        require(!screen.details.visible, "Legacy panel obscured modern details");
                        screen.setSelected(screen.list.children().stream().filter(e -> e.getInfo().getModId().equals("NotEnoughItems")).findFirst().orElseThrow());
                        screen.list.setScrollAmount(Double.MAX_VALUE);
                        stage = 5; frames = screen.renderedFrames;
                    } else if (stage == 5 && mc.screen instanceof LegacyModListScreen screen && screen.renderedFrames > frames + 3) {
                        net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mfcl-mod-list.png", mc.getMainRenderTarget(), message -> screenshotSaved = true);
                        stage = 6;
                    } else if (stage == 6 && screenshotSaved && mc.screen instanceof LegacyModListScreen screen) {
                        screen.onClose();
                        require(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen, "Mod list did not return to its parent");
                        com.mojang.logging.LogUtils.getLogger().info("MFCL_MOD_LIST_SMOKE_PASS: combined modern/legacy entries, sort, search, version/load details, resize, modern details and return");
                        stage = 99; mc.stop();
                    }
                } catch (Throwable failure) {
                    com.mojang.logging.LogUtils.getLogger().error("MFCL_MOD_LIST_SMOKE_FAIL", failure); stage = 99; mc.stop();
                }
            });
        }
        private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    }
}
