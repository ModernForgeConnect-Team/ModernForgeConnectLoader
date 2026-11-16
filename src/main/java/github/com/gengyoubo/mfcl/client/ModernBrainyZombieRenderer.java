package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import github.com.gengyoubo.mfcl.content.ModernBrainyZombie;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** Draw TC4's original zombie model on the native zombie's position. */
public final class ModernBrainyZombieRenderer extends EntityRenderer<ModernBrainyZombie> {
    private final ModernContentBridge bridge;
    private final LegacyBlockEntityRenderer replay;
    private long retryAfter;
    public ModernBrainyZombieRenderer(EntityRendererProvider.Context context,ModernContentBridge bridge){super(context);this.bridge=bridge;replay=new LegacyBlockEntityRenderer(bridge);shadowRadius=.5f;}
    @Override public void render(ModernBrainyZombie entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        Object original=entity.legacyOrNull();if(original==null||System.nanoTime()<retryAfter)return;
        try {
            var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera();var eye=camera.getPosition();
            var world=bridge.services().worlds().findBound(entity.level());if(world==null)return;
            bridge.blockRendering().tiles().view(world,eye.x,eye.y,eye.z,camera.getYRot(),camera.getXRot(),mc.player==null?0:mc.player.tickCount);
            double x=Mth.lerp(partial,entity.xo,entity.getX())-eye.x,y=Mth.lerp(partial,entity.yo,entity.getY())-eye.y,z=Mth.lerp(partial,entity.zo,entity.getZ())-eye.z;
            var primitives=bridge.services().entityHosting().render(original,x,y,z,Mth.rotLerp(partial,entity.yRotO,entity.getYRot()),partial);
            replay.render(primitives,pose,buffers,light,OverlayTexture.pack(OverlayTexture.u(0),OverlayTexture.v(entity.hurtTime>0||entity.deathTime>0)));
        }catch(Exception | LinkageError failure){retryAfter=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);com.mojang.logging.LogUtils.getLogger().error("MFCL cannot render brainy zombie",failure);}
    }
    @Override public ResourceLocation getTextureLocation(ModernBrainyZombie entity){return InventoryMenu.BLOCK_ATLAS;}
}
