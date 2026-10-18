package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.platform.NativeImage;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Replays common old GUI operations through the host renderer and private legacy texture resources. */
final class LegacyGuiRenderer {
    private final LegacyServices services;
    private final Map<String,ResourceLocation> textures=new HashMap<>();
    LegacyGuiRenderer(LegacyServices services){this.services=services;}
    void draw(GuiGraphics graphics,List<LegacyGuiGraphics.Command> commands) throws Exception {
        var mc=Minecraft.getInstance();
        for(var command:commands) {
            if(command instanceof LegacyGuiGraphics.Rect r) {
                int x1=Math.min(r.x1(),r.x2()),x2=Math.max(r.x1(),r.x2()),y1=Math.min(r.y1(),r.y2()),y2=Math.max(r.y1(),r.y2());
                graphics.fillGradient(x1,y1,x2,y2,r.top(),r.bottom());
            } else if(command instanceof LegacyGuiGraphics.Text t) {
                graphics.drawString(mc.font,t.text(),t.x(),t.y(),t.color(),t.shadow());
            } else if(command instanceof LegacyGuiGraphics.Texture t) {
                graphics.blit(texture(t.resource()),t.x(),t.y(),t.u(),t.v(),t.width(),t.height(),256,256);
            } else if(command instanceof LegacyGuiGraphics.Item i) {
                var stack=ModernItemMapping.renderStack(services.items().snapshot(i.stack()));
                if(stack.isEmpty()) continue;
                if(i.overlay())graphics.renderItemDecorations(mc.font,stack,i.x(),i.y(),i.label());
                else graphics.renderItem(stack,i.x(),i.y());
            }
        }
    }
    private ResourceLocation texture(String key) throws Exception {
        ResourceLocation cached=textures.get(key);if(cached!=null)return cached;
        ResourceLocation old=new ResourceLocation(key);
        try(var input=services.loader().getResourceAsStream("assets/"+old.getNamespace()+"/"+old.getPath())) {
            if(input==null)throw new IllegalArgumentException("Missing original GUI texture: "+key);
            var image=NativeImage.read(input);var location=Minecraft.getInstance().getTextureManager().register("mfcl/legacy_gui",new DynamicTexture(image));
            textures.put(key,location);return location;
        }
    }
}
