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
    private record Texture(ResourceLocation id,int width,int height) {}
    private final Map<String,Texture> textures=new HashMap<>();
    private final Set<String> missingIcons=new HashSet<>();
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
                graphics.blit(texture(t.resource()).id(),t.x(),t.y(),t.u(),t.v(),t.width(),t.height(),256,256);
            } else if(command instanceof LegacyGuiGraphics.Item i) {
                var stack=ModernItemMapping.renderStack(services.items().snapshot(i.stack()));
                if(stack.isEmpty()) { if(!i.overlay()) legacyIcon(graphics,i);continue; }
                if(i.overlay())graphics.renderItemDecorations(mc.font,stack,i.x(),i.y(),i.label());
                else graphics.renderItem(stack,i.x(),i.y());
            }
        }
    }
    private void legacyIcon(GuiGraphics graphics,LegacyGuiGraphics.Item item) throws Exception {
        Object icon=item.stack().getClass().getMethod("func_77954_c").invoke(item.stack());
        if(icon!=null) {
            String name=(String)services.loader().loadClass("net.minecraft.util.IIcon").getMethod("func_94215_i").invoke(icon);
            ResourceLocation id=ResourceLocation.tryParse(name);
            if(id!=null)for(String directory:List.of("blocks","items")) {
                String key=id.getNamespace()+":textures/"+directory+"/"+id.getPath()+".png";
                if(services.loader().getResource("assets/"+id.getNamespace()+"/textures/"+directory+"/"+id.getPath()+".png")==null)continue;
                Texture texture=texture(key);int size=Math.min(texture.width(),texture.height());
                graphics.blit(texture.id(),item.x(),item.y(),16,16,0,0,size,size,texture.width(),texture.height());return;
            }
        }
        var snapshot=services.items().snapshot(item.stack());String key=snapshot.key()+"@"+snapshot.metadata();
        if(missingIcons.add(key))com.mojang.logging.LogUtils.getLogger().warn("MFCL: no modern model or original 2D icon for {}",key);
    }
    private Texture texture(String key) throws Exception {
        Texture cached=textures.get(key);if(cached!=null)return cached;
        ResourceLocation old=Objects.requireNonNull(ResourceLocation.tryParse(key));
        try(var input=services.loader().getResourceAsStream("assets/"+old.getNamespace()+"/"+old.getPath())) {
            if(input==null)throw new IllegalArgumentException("Missing original GUI texture: "+key);
            var image=NativeImage.read(input);var location=Minecraft.getInstance().getTextureManager().register("mfcl/legacy_gui",new DynamicTexture(image));
            Texture texture=new Texture(location,image.getWidth(),image.getHeight());textures.put(key,texture);return texture;
        }
    }
}
