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
                graphics.pose().pushPose();graphics.pose().translate(t.x(),t.y(),0);graphics.pose().scale((float)t.sx(),(float)t.sy(),1);
                graphics.drawString(mc.font,t.text(),0,0,t.color(),t.shadow());graphics.pose().popPose();
            } else if(command instanceof LegacyGuiGraphics.Texture t) {
                graphics.pose().pushPose();graphics.pose().translate(t.x(),t.y(),0);graphics.pose().scale((float)t.sx(),(float)t.sy(),1);
                graphics.blit(texture(t.resource()).id(),0,0,t.u(),t.v(),t.width(),t.height(),256,256);graphics.pose().popPose();
            } else if(command instanceof LegacyGuiGraphics.Item i) {
                var stack=ModernItemMapping.renderStack(services.items().snapshot(i.stack()));
                graphics.pose().pushPose();graphics.pose().translate(i.x(),i.y(),0);graphics.pose().scale((float)i.sx(),(float)i.sy(),1);
                try {
                    if(stack.isEmpty()) { if(!i.overlay()) legacyIcon(graphics,new LegacyGuiGraphics.Item(i.stack(),0,0,false,i.label(),1,1)); }
                    else if(i.overlay())graphics.renderItemDecorations(mc.font,stack,0,0,i.label());
                    else graphics.renderItem(stack,0,0);
                } finally {graphics.pose().popPose();}
            } else if(command instanceof LegacyGuiGraphics.Mesh mesh) {
                mesh(graphics,mesh.primitives());
            }
        }
    }
    private void mesh(GuiGraphics graphics,List<LegacyVertexStream.Primitive> primitives) throws Exception {
        graphics.flush();
        var buffer=com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        var matrix=graphics.pose().last().pose();
        try {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();com.mojang.blaze3d.systems.RenderSystem.disableCull();
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            for(var primitive:primitives) {
                var vertices=primitive.vertices();if(vertices.isEmpty())continue;
                boolean textured=primitive.mode()>3&&!primitive.texture().isEmpty();
                com.mojang.blaze3d.systems.RenderSystem.blendFunc(primitive.state().sourceBlend(),primitive.state().destinationBlend());
                if(textured) {
                    com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
                    com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,texture(primitive.texture()).id());
                } else com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
                buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,textured?com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR:com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
                if(primitive.mode()<=3) {
                    int edges=primitive.mode()==1?vertices.size()/2:primitive.mode()==2?vertices.size():vertices.size()-1;
                    for(int edge=0;edge<edges;edge++) {
                        var a=vertices.get(primitive.mode()==1?edge*2:edge);var b=vertices.get(primitive.mode()==1?edge*2+1:(edge+1)%vertices.size());
                        double dx=b.x()-a.x(),dy=b.y()-a.y(),length=Math.hypot(dx,dy);if(length==0)continue;
                        double ox=-dy/length*1.5,oy=dx/length*1.5;
                        vertex(buffer,matrix,a,a.x()+ox,a.y()+oy,false);vertex(buffer,matrix,b,b.x()+ox,b.y()+oy,false);
                        vertex(buffer,matrix,b,b.x()-ox,b.y()-oy,false);vertex(buffer,matrix,a,a.x()-ox,a.y()-oy,false);
                    }
                } else for(var quad:LegacyWorldRenderer.quads(primitive.mode(),vertices.size()))for(int index:quad) {
                    var v=vertices.get(index);vertex(buffer,matrix,v,v.x(),v.y(),textured);
                }
                com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());
            }
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.systems.RenderSystem.enableCull();
        }
    }
    private void vertex(com.mojang.blaze3d.vertex.BufferBuilder buffer,org.joml.Matrix4f matrix,LegacyVertexStream.Vertex v,double x,double y,boolean textured) {
        buffer.vertex(matrix,(float)x,(float)y,0);if(textured)buffer.uv(v.u(),v.v());
        buffer.color(v.argb()>>16&255,v.argb()>>8&255,v.argb()&255,v.argb()>>>24).endVertex();
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
