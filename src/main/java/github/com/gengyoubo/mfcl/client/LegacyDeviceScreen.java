package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Original device GUI layout with native menu clicks and server-owned inventory synchronization. */
public final class LegacyDeviceScreen extends AbstractContainerScreen<LegacyDeviceMenu> {
    private final Object original;
    public Object originalGui(){return original;}
    public LegacyDeviceScreen(LegacyDeviceMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);
        var content=ModernContentBridge.active();var services=content.services();var mc=Minecraft.getInstance();
        try {
            original=services.items().inLegacy(()->{
                Object world=services.worlds().bound(mc.level);
                Object actor=content.itemUse().clientActor(world,new LegacyTiles.Placer(mc.player.getUUID(),mc.player.getName().getString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot(),mc.player.getAbilities().instabuild,mc.player.isShiftKeyDown()),null);
                var stacks=new java.util.ArrayList<LegacyClientData.Stack>();
                for(int slot=0;slot<36;slot++){
                    LegacyClientData.Stack stack=null;
                    if(!inventory.getItem(slot).isEmpty())try{stack=content.gameplayStack(inventory.getItem(slot));}catch(Exception unmapped){}
                    stacks.add(stack);
                }
                services.playerInventory().write(actor,stacks);services.playerInventory().select(actor,menu.descriptor().itemSlot()>=0?menu.descriptor().itemSlot():inventory.selected);
                Object gui=LegacyDeviceContainers.clientGui(services,menu.descriptor(),actor,world);
                if(gui==null)throw new IllegalStateException("Original GUI handler returned no client screen");
                var guiType=services.loader().loadClass("net.minecraft.client.gui.inventory.GuiContainer");
                Object container=guiType.getField("field_147002_h").get(gui);
                container.getClass().getField("field_75152_c").setInt(container,menu.containerId);
                services.loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71070_bA").set(actor,container);
                menu.bindPrediction(gui,actor);
                var width=guiType.getDeclaredField("field_146999_f");width.setAccessible(true);imageWidth=width.getInt(gui);
                var height=guiType.getDeclaredField("field_147000_g");height.setAccessible(true);imageHeight=height.getInt(gui);
                return gui;
            });
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Cannot construct original device GUI",failure);throw new IllegalStateException("Cannot construct original device GUI",failure);}
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y) {}
    @Override public boolean mouseClicked(double x,double y,int button) {
        return ClientContainerBridge.active().deviceMouse(this,(int)x,(int)y,button,false)||super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        return ClientContainerBridge.active().deviceMouse(this,(int)x,(int)y,button,true)||super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        ClientContainerBridge.active().deviceDrag(this,(int)x,(int)y,button);return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta) {
        return ClientContainerBridge.active().deviceScroll(this,(int)x,(int)y,delta)||super.mouseScrolled(x,y,delta);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y) {}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
        renderBackground(graphics);super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);
    }
}
