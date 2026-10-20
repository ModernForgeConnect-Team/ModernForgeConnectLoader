package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import java.util.*;

/** Replays the hosted original's own Render, captured at the same camera-relative position as placed tile models. */
public final class LegacyHostedEntityRenderer extends EntityRenderer<LegacyHostedEntity> {
    private final ModernContentBridge bridge;
    private final LegacyBlockEntityRenderer replay;
    private final Map<Class<?>, Long> retryAfter = new HashMap<>();
    public LegacyHostedEntityRenderer(EntityRendererProvider.Context context, ModernContentBridge bridge) {
        super(context); this.bridge = bridge; this.replay = new LegacyBlockEntityRenderer(bridge); shadowRadius = 0;
    }
    @Override public void render(LegacyHostedEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Object original = entity.legacyOrNull();
        if (original == null || System.nanoTime() < retryAfter.getOrDefault(original.getClass(), 0L)) return;
        try {
            var mc = Minecraft.getInstance(); var camera = mc.gameRenderer.getMainCamera(); var eye = camera.getPosition();
            var world = bridge.services().worlds().findBound(entity.level());
            if (world == null) return;
            bridge.blockRendering().tiles().view(world, eye.x, eye.y, eye.z, camera.getYRot(), camera.getXRot(), mc.player == null ? 0 : mc.player.tickCount);
            double x = Mth.lerp(partial, entity.xo, entity.getX()) - eye.x, y = Mth.lerp(partial, entity.yo, entity.getY()) - eye.y, z = Mth.lerp(partial, entity.zo, entity.getZ()) - eye.z;
            var primitives = bridge.services().entityHosting().render(original, x, y, z, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial);
            replay.render(primitives, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } catch (Exception | LinkageError failure) {
            // One incompatible original renderer must not stop the frame; retry later like placed tile models.
            retryAfter.put(original.getClass(), System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
            com.mojang.logging.LogUtils.getLogger().error("Cannot render hosted original entity {}; retrying in 5 seconds", original.getClass().getName(), failure);
        }
    }
    @Override public ResourceLocation getTextureLocation(LegacyHostedEntity entity) { return InventoryMenu.BLOCK_ATLAS; }
}
