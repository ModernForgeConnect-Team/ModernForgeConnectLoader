package github.com.gengyoubo.mfcl.compat.nei;

import github.com.gengyoubo.mfcl.client.ModernRecipeProjection;
import github.com.gengyoubo.mfcl.client.ModernItemMapping;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Modern presentation of data returned by the original NEI ItemList and recipe handlers. */
public final class NeiClientUi {
    private static NeiBackend activeBridge;
    static NeiBackend activeBridge() { return activeBridge; }
    private NeiClientUi() {}
    public static void install(NeiBackend bridge) {
        activeBridge = bridge;
        ModernRecipeProjection.install(bridge.data());
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
                event.addListener(Button.builder(Component.literal("NEI"), b -> Minecraft.getInstance().setScreen(new Browser(screen, bridge)))
                        .bounds(4, 4, 34, 20).build());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            if (event.getScreen() instanceof AbstractContainerScreen<?> && event.getKeyCode() == GLFW.GLFW_KEY_O && Screen.hasControlDown()) {
                Minecraft.getInstance().setScreen(new Browser(event.getScreen(), bridge)); event.setCanceled(true);
            }
        });
    }

    public static final class Browser extends Screen {
        private final Screen parent;
        private final NeiBackend bridge;
        private List<LegacyClientData.Stack> entries = List.of();
        private List<LegacyClientData.Recipe> recipes = List.of();
        private final List<Hit> hits = new ArrayList<>();
        private EditBox search;
        private String expression = "", error = "";
        private int page, recipePage;
        private boolean usages;
        private LegacyClientData.Stack selected;
        int renderedFrames;
        private record Hit(int x, int y, LegacyClientData.Stack stack) {
            boolean contains(double mx, double my) { return mx >= x && mx < x + 18 && my >= y && my < y + 18; }
        }
        public Browser(Screen parent, NeiBackend bridge) { super(Component.literal("NEI · 1.7.10")); this.parent = parent; this.bridge = bridge; }
        @Override protected void init() {
            search = addRenderableWidget(new EditBox(font, 10, 25, Math.max(100, width / 2 - 20), 20, Component.literal("Search")));
            search.setMaxLength(256); search.setValue(expression);
            search.setResponder(text -> { expression = text; page = 0; refresh(); });
            addRenderableWidget(Button.builder(Component.literal("<"), b -> recipePage = Math.max(0, recipePage - 1)).bounds(width - 62, height - 28, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> recipePage = Math.min(Math.max(0, recipes.size() - 1), recipePage + 1)).bounds(width - 34, height - 28, 24, 20).build());
            refresh();
        }
        private void refresh() {
            try { entries = bridge.search(expression); error = ""; }
            catch (Exception failure) { error = failure.toString(); entries = List.of(); }
        }
        private int columns() { return Math.max(1, (width / 2 - 20) / 20); }
        private int rows() { return Math.max(1, (height - 85) / 20); }
        private void query(LegacyClientData.Stack stack, boolean usages) {
            try {
                if (minecraft.level != null && ModernRecipeProjection.lastResult() == null) throw new IllegalStateException("Current world's recipes are unavailable");
                recipes = bridge.recipes(stack, usages); selected = stack; this.usages = usages; recipePage = 0; error = "";
            }
            catch (Exception failure) { error = failure.toString(); recipes = List.of(); }
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderedFrames++;
            renderBackground(graphics); hits.clear();
            graphics.drawString(font, title, 10, 9, 0xffffff);
            graphics.drawString(font, "R: recipes   U: uses   Esc: back", width / 2 + 8, 9, 0xcccccc);
            int perPage = columns() * rows(), maxPage = Math.max(0, (entries.size() - 1) / perPage); page = Math.min(page, maxPage);
            for (int i = page * perPage; i < Math.min(entries.size(), (page + 1) * perPage); i++) {
                int cell = i - page * perPage;
                drawStack(graphics, entries.get(i), 10 + cell % columns() * 20, 53 + cell / columns() * 20);
            }
            graphics.drawString(font, entries.size() + " items   " + (page + 1) + "/" + (maxPage + 1), 10, height - 22, 0xaaaaaa);
            if (selected != null) {
                int x = width / 2 + 8, y = 38;
                graphics.drawString(font, font.plainSubstrByWidth(selected.name(), width / 2 - 20), x, y, 0xffffff);
                graphics.drawString(font, (usages ? "Uses: " : "Recipes: ") + recipes.size(), x, y + 15, 0xaaaaaa);
                if (!recipes.isEmpty()) {
                    var recipe = recipes.get(recipePage); y += 39;
                    graphics.drawString(font, recipe.name(), x, y, 0xffffff); y += 18;
                    for (var ingredient : recipe.ingredients()) if (!ingredient.alternatives().isEmpty())
                        drawStack(graphics, ingredient.alternatives().get((int) (System.currentTimeMillis() / 1000 % ingredient.alternatives().size())), x + ingredient.x(), y + ingredient.y());
                    if (recipe.result() != null) drawStack(graphics, recipe.result(), x + 126, y + 22);
                    for (var other : recipe.other()) if (!other.alternatives().isEmpty()) drawStack(graphics, other.alternatives().get(0), x + other.x(), y + other.y());
                    graphics.drawString(font, (recipePage + 1) + "/" + recipes.size(), x, height - 22, 0xaaaaaa);
                }
            }
            if (!error.isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(error, width - 20), 10, height - 39, 0xff7777);
            super.render(graphics, mouseX, mouseY, partialTick);
            hits.stream().filter(h -> h.contains(mouseX, mouseY)).findFirst().ifPresent(hit -> graphics.renderComponentTooltip(font,
                    List.of(Component.literal(hit.stack().name()), Component.literal("R: recipes   U: uses")), mouseX, mouseY));
        }
        private void drawStack(GuiGraphics graphics, LegacyClientData.Stack stack, int x, int y) {
            graphics.fill(x, y, x + 18, y + 18, 0x88333333);
            ItemStack modern = ModernItemMapping.renderStack(stack);
            if (modern.isEmpty()) graphics.drawString(font, "?", x + 5, y + 4, 0xffbb77);
            else { graphics.renderItem(modern, x + 1, y + 1); graphics.renderItemDecorations(font, modern, x + 1, y + 1); }
            hits.add(new Hit(x, y, stack));
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (super.mouseClicked(x, y, button)) return true;
            for (Hit hit : hits) if (hit.contains(x, y)) { query(hit.stack(), button == 1); return true; }
            return false;
        }
        @Override public boolean mouseScrolled(double x, double y, double delta) {
            page = Math.max(0, Math.min(Math.max(0, (entries.size() - 1) / (columns() * rows())), page + (delta > 0 ? -1 : 1))); return true;
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!search.isFocused() && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U)) {
                double x = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
                double y = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
                for (Hit hit : hits) if (hit.contains(x, y)) { query(hit.stack(), key == GLFW.GLFW_KEY_U); return true; }
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public boolean isPauseScreen() { return false; }
        void smokeSearch() {
            search.setValue("crafting"); search.setFocused(false);
            if (entries.size() != 1 || !entries.get(0).key().equals("minecraft:crafting_table")) throw new IllegalStateException("NEI search smoke failed");
        }
        int smokeRecipeClick(boolean usages) {
            if (!mouseClicked(11, 54, usages ? 1 : 0) || recipes.isEmpty()) throw new IllegalStateException("NEI recipe UI smoke failed usages=" + usages + " hits=" + hits.size() + " error=" + error);
            return recipes.size();
        }
    }
}
