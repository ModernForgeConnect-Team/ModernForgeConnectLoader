package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.client.LegacyBlockEntityRenderer;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Drive the original Pickaxe of the Core scan overlay from the modern world-render event. */
public final class ThaumcraftProspecting {
    private static final AtomicBoolean REPORTED=new AtomicBoolean();
    private ThaumcraftProspecting(){}
    static void install(LegacyServices services){
        var replay=new LegacyBlockEntityRenderer(ModernContentBridge.active());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event)->{
            if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
            try{
                var primitives=capture(services,event.getPartialTick(),System.currentTimeMillis());if(primitives.isEmpty())return;
                var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
                replay.render(primitives,event.getPoseStack(),buffers,0xF000F0,OverlayTexture.NO_OVERLAY);buffers.endBatch();
            }catch(Exception|LinkageError failure){ThaumcraftReflection.reportOnce(REPORTED,"MFCL TC4 prospecting overlay failed",failure);}
        });
    }
    /** Shared capture path for the live renderer and acceptance: original scan results, fade, colors and geometry. */
    public static List<LegacyVertexStream.Primitive> capture(LegacyServices services,float partial,long now)throws Exception {
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return List.of();
        return services.items().inLegacy(()->{
            var content=ModernContentBridge.active();var loader=services.loader();
            Object tc=loader.loadClass("thaumcraft.common.Thaumcraft").getField("instance").get(null);
            Object renderer=tc.getClass().getField("renderEventHandler").get(tc);
            if(renderer==null||renderer.getClass().getField("scanCount").getLong(renderer)<=now)return List.of();
            var camera=mc.gameRenderer.getMainCamera();var eye=camera.getPosition();Object world=services.worlds().bound(mc.level);
            content.blockRendering().tiles().view(world,eye.x,eye.y,eye.z,camera.getYRot(),camera.getXRot(),mc.player.tickCount);
            var clientType=loader.loadClass("net.minecraft.client.Minecraft");Object client=clientType.getMethod("func_71410_x").invoke(null);
            Object player=clientType.getField("field_71439_g").get(client);
            try(var frame=LegacyWorldGraphics.begin()){
                renderer.getClass().getMethod("showScannedBlocks",float.class,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),long.class).invoke(renderer,partial,player,now);
                return services.items().resolveIcons(frame.primitives());
            }
        });
    }
}
