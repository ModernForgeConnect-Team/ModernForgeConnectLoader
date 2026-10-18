package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replays the captured original inventory-block geometry for one legacy block item. */
public final class LegacyItemRenderer extends BlockEntityWithoutLevelRenderer {
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
        switch (context) {
            case GUI -> { pose.mulPose(Axis.XP.rotationDegrees(30)); pose.mulPose(Axis.YP.rotationDegrees(225)); pose.scale(0.625f, 0.625f, 0.625f); }
            case GROUND -> { pose.translate(0, 0.1875, 0); pose.scale(0.25f, 0.25f, 0.25f); }
            case FIXED -> { }
            default -> pose.scale(0.45f, 0.45f, 0.45f);
        }
        Map<String, List<LegacyVertexStream.Primitive>> byTexture = new LinkedHashMap<>();
        for (LegacyVertexStream.Primitive primitive : primitives) byTexture.computeIfAbsent(primitive.texture(), ignored -> new ArrayList<>()).add(primitive);
        for (var entry : byTexture.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) continue;
            LegacyWorldRenderer.replay(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(id)), entry.getValue(), light, overlay);
        }
        pose.popPose();
    }
}
