package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ScanSyncChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The villager choice of a remote trading device; each row shows what the villager sells. */
public final class RemoteMerchantScreen extends Screen {
    private static final int ROW = 22, ICONS = 8;
    private final ScanSyncChannel.MerchantList list;
    private int page, start, end;
    private RemoteMerchantScreen(ScanSyncChannel.MerchantList list) { super(Component.translatable("mfcl.trading_post.title")); this.list = list; }
    public static void open(ScanSyncChannel.MerchantList list) { Minecraft.getInstance().setScreen(new RemoteMerchantScreen(list)); }
    private int left() { return width / 2 - 160; }
    @Override protected void init() {
        int rows = Math.max(1, (height - 76) / ROW); start = page * rows; end = Math.min(list.merchants().size(), start + rows);
        for (int i = start; i < end; i++) {
            var merchant = list.merchants().get(i);
            addRenderableWidget(Button.builder(merchant.name(), button -> pick(merchant)).bounds(left(), 32 + (i - start) * ROW, 140, 20).build());
        }
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("←"), button -> { page--; rebuildWidgets(); }).bounds(width / 2 - 110, height - 28, 60, 20).build());
        if (end < list.merchants().size()) addRenderableWidget(Button.builder(Component.literal("→"), button -> { page++; rebuildWidgets(); }).bounds(width / 2 + 50, height - 28, 60, 20).build());
    }
    private void pick(ScanSyncChannel.MerchantEntry merchant) {
        ScanSyncChannel.pickMerchant(new ScanSyncChannel.MerchantPick(list.origin(), merchant.entityId()));
        onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderBackground(graphics); super.render(graphics, x, y, partial);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xffffff);
        for (int i = start; i < end; i++) {
            var results = list.merchants().get(i).results(); int top = 32 + (i - start) * ROW + 2;
            for (int icon = 0; icon < results.size() && icon < ICONS; icon++) {
                int left = left() + 146 + icon * 18; var stack = results.get(icon);
                graphics.renderItem(stack, left, top); graphics.renderItemDecorations(font, stack, left, top);
                if (x >= left && x < left + 16 && y >= top && y < top + 16) graphics.renderTooltip(font, stack, x, y);
            }
        }
    }
}
