package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Enlarged native item slots for opt-in screenshot acceptance, never shown during ordinary play. */
final class ItemContextSmokeScreen extends Screen {
    private final List<ItemStack> items;
    ItemContextSmokeScreen(ModernContentBridge bridge) {
        super(Component.literal("Original item rendering"));
        items = List.of(bridge.stack("Thaumcraft:ItemThaumometer",0,1,""),bridge.stack("Thaumcraft:blockTable",14,1,""),
                bridge.declarations().tabs().stream().flatMap(t -> t.contents().stream()).filter(s -> s.key().equals("Thaumcraft:WandCasting")).findFirst().map(bridge::mapStack).orElse(ItemStack.EMPTY),
                bridge.stack("Thaumcraft:blockWoodenDevice",8,1,""));
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0,0,width,height,0xFFEEEEEE);
        int size = Math.min(128,(width-48)/4), left = (width-size*4-24)/2, top = (height-size)/2;
        for (int i=0;i<items.size();i++) {
            int x = left + i*(size+8);
            graphics.fill(x,top,x+size,top+size,0xFFB6B6B6);
            graphics.pose().pushPose(); graphics.pose().translate(x+8,top+8,0);
            float scale = (size-16)/16f; graphics.pose().scale(scale,scale,scale);
            graphics.renderItem(items.get(i),0,0); graphics.pose().popPose();
            graphics.drawCenteredString(font,items.get(i).getHoverName(),x+size/2,top+size+10,0xFF222222);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
