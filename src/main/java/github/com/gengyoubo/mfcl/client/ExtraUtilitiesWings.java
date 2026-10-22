package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import java.util.*;

/** Original XU wing geometry and textures, attached to the native player's animated body. */
public final class ExtraUtilitiesWings extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    private final ModernContentBridge content;
    private final LegacyBlockEntityRenderer replay;
    private long retryAfter;
    private static Object handler,renderer;
    private static ClassLoader owner;
    private ExtraUtilitiesWings(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent,ModernContentBridge content){super(parent);this.content=content;replay=new LegacyBlockEntityRenderer(content);}
    static void install(ModernContentBridge content,net.minecraftforge.eventbus.api.IEventBus bus){
        if(!content.items().containsKey("ExtraUtilities:angelRing"))return;
        bus.addListener((EntityRenderersEvent.AddLayers event)->{for(String skin:event.getSkins()){net.minecraft.client.renderer.entity.player.PlayerRenderer parent=event.getSkin(skin);if(parent!=null)parent.addLayer(new ExtraUtilitiesWings(parent,content));}});
    }
    public static List<LegacyVertexStream.Primitive> capture(ModernContentBridge content,AbstractClientPlayer player,float partial) throws Exception {
        return content.services().items().inLegacy(()->{
            var services=content.services();var loader=services.loader();var type=loader.loadClass("com.rwtema.extrautils.EventHandlerClient");
            var players=(Map<?,?>)type.getField("flyingPlayers").get(null);Object style=players.get(player.getGameProfile().getName());
            if(!(style instanceof Integer value)||value<=0)return List.of();
            Object world=services.worlds().findBound(player.level());if(world==null)return List.of();
            Object actor=LegacyWorlds.entity(world,player.getUUID());if(actor==null)return List.of();
            if(owner!=loader){handler=type.getConstructor().newInstance();renderer=loader.loadClass("net.minecraft.client.renderer.entity.RenderPlayer").getConstructor().newInstance();owner=loader;}
            var time=type.getDeclaredField("time");time.setAccessible(true);time.setLong(null,player.level().getGameTime());
            var fraction=type.getDeclaredField("renderTickTime");fraction.setAccessible(true);fraction.setFloat(null,partial);
            Object caps=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71075_bZ").get(actor);
            caps.getClass().getField("field_75100_b").setBoolean(caps,player.getAbilities().flying);
            var event=loader.loadClass("net.minecraftforge.client.event.RenderPlayerEvent$Specials$Post");
            Object post=event.getConstructor(loader.loadClass("net.minecraft.entity.player.EntityPlayer"),loader.loadClass("net.minecraft.client.renderer.entity.RenderPlayer"),float.class).newInstance(actor,renderer,partial);
            try(var frame=LegacyWorldGraphics.begin()){type.getMethod("renderWings",event).invoke(handler,post);return frame.primitives();}
        });
    }
    @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(player.isInvisible()||System.nanoTime()<retryAfter)return;
        try {
            var geometry=capture(content,player,partial);if(geometry.isEmpty())return;
            pose.pushPose();try{getParentModel().body.translateAndRotate(pose);replay.render(geometry,pose,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);}finally{pose.popPose();}
        }catch(Exception|LinkageError failure){retryAfter=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);com.mojang.logging.LogUtils.getLogger().error("MFCL cannot render original angel ring wings; retrying in 5 seconds",failure);}
    }
}
