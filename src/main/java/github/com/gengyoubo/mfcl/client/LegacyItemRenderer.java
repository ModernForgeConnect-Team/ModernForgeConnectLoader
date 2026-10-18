package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replays the captured original inventory-block geometry for one legacy block item. */
public final class LegacyItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final java.util.Set<String> missingSprites = new java.util.HashSet<>();
    private static final java.util.Set<String> debugged = new java.util.HashSet<>();
    private final ModernContentBridge bridge;
    private final String itemKey;
    public LegacyItemRenderer(ModernContentBridge bridge, String itemKey) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.bridge = bridge; this.itemKey = itemKey;
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        List<LegacyVertexStream.Primitive> primitives = bridge.pack().geometry().get(itemKey + "@" + ModernContentBridge.metadata(stack));
        if (primitives == null || primitives.isEmpty()) return;
        pose.pushPose();
        // Captured old geometry is centered on the block origin ([-0.5,0.5]); block models are [0,1], so
        // shift first, then apply the standard block item display transform.
        pose.translate(0.5, 0.5, 0.5);
        switch (context) {
            case GUI -> { pose.mulPose(Axis.XP.rotationDegrees(30)); pose.mulPose(Axis.YP.rotationDegrees(225)); pose.scale(0.625f, 0.625f, 0.625f); }
            case GROUND -> { pose.translate(0, 0.1875, 0); pose.scale(0.25f, 0.25f, 0.25f); }
            case FIXED -> pose.scale(0.5f, 0.5f, 0.5f);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> { pose.mulPose(Axis.YP.rotationDegrees(45)); pose.translate(0, 0.125, 0); pose.scale(0.375f, 0.375f, 0.375f); }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> { pose.mulPose(Axis.YP.rotationDegrees(45)); pose.translate(0, 0.125, 0); pose.scale(0.375f, 0.375f, 0.375f); }
            default -> pose.scale(0.5f, 0.5f, 0.5f);
        }
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        Map<String, List<LegacyVertexStream.Primitive>> byTexture = new LinkedHashMap<>();
        for (LegacyVertexStream.Primitive primitive : primitives) byTexture.computeIfAbsent(primitive.texture(), ignored -> new ArrayList<>()).add(primitive);
        for (var entry : byTexture.entrySet()) {
            ResourceLocation spriteId = ResourceLocation.tryParse(entry.getKey());
            if (spriteId == null) continue;
            TextureAtlasSprite sprite = atlas.apply(spriteId);
            if (sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
                if (missingSprites.add(spriteId.toString())) com.mojang.logging.LogUtils.getLogger().warn("MFCL: captured sprite is not stitched: {}", spriteId);
                continue;
            }
            if (debugged.add(itemKey + "@" + ModernContentBridge.metadata(stack) + ":" + entry.getKey())) {
                var first = entry.getValue().get(0);
                float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
                for (var vertex : first.vertices()) { minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u()); minV = Math.min(minV, vertex.v()); maxV = Math.max(maxV, vertex.v()); }
                com.mojang.logging.LogUtils.getLogger().info("MFCL item render {} sprite={} localU=[{},{}] localV=[{},{}] mode={} primitives={} argb={}",
                        itemKey, sprite.contents().name(), minU, maxU, minV, maxV, first.mode(), entry.getValue().size(), Integer.toHexString(first.vertices().get(0).argb()));
            }
            LegacyWorldRenderer.replay(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), sprite, entry.getValue(), light, overlay);
        }
        pose.popPose();
    }
}
