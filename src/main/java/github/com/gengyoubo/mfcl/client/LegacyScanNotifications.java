package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderGuiEvent;
import java.util.*;

/** Presents the original discovery/research messages, including the reason an attempted scan was rejected. */
final class LegacyScanNotifications {
    private record Notice(String text,String image,int color,long expire) {}
    private final ModernContentBridge content;
    private boolean reported;
    private LegacyScanNotifications(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new LegacyScanNotifications(content)::render);}
    private void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.screen!=null||mc.options.hideGui)return;
        long now=System.nanoTime()/1_000_000;
        try {
            var notices=content.services().items().inLegacy(()->{
                var loader=content.services().loader();if(loader.getResource("thaumcraft/client/lib/PlayerNotifications.class")==null)return List.<Notice>of();
                var type=loader.loadClass("thaumcraft.client.lib.PlayerNotifications");var result=new ArrayList<Notice>();
                for(Object value:(List<?>)type.getMethod("getListAndUpdate",long.class).invoke(null,now)) {
                    var c=value.getClass();Object image=c.getField("image").get(value);
                    result.add(new Notice((String)c.getField("text").get(value),image==null?null:image.toString(),c.getField("color").getInt(value),c.getField("expire").getLong(value)));
                }
                type.getMethod("getAspectListAndUpdate",long.class).invoke(null,now);return result;
            });
            var graphics=event.getGuiGraphics();int index=0;
            for(var notice:notices) {
                if(index>=6)break;int x=mc.getWindow().getGuiScaledWidth()-12,y=mc.getWindow().getGuiScaledHeight()-34-index++*12;
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(x,y,0);graphics.pose().scale(.5f,.5f,1);
                    graphics.drawString(mc.font,notice.text(),-mc.font.width(notice.text())-20,0,0xFFFFFFFF,true);
                    if(notice.image()!=null) {
                        int c=notice.color();com.mojang.blaze3d.systems.RenderSystem.setShaderColor((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,1);
                        graphics.blit(net.minecraft.resources.ResourceLocation.parse(notice.image()),-16,-4,16,16,0f,0f,256,256,256,256);
                    }
                } finally {com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);graphics.pose().popPose();}
            }
        }catch(Exception failure){if(!reported){reported=true;com.mojang.logging.LogUtils.getLogger().error("MFCL scan notification display failed",failure);}}
    }
}
