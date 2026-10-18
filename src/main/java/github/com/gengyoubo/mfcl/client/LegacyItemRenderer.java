package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.bridge.LegacyItemLayout;
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
    private final ModernContentBridge bridge;
    private final String itemKey;
    private final String blockKey;
    private final Map<List<LegacyVertexStream.Primitive>, LegacyItemLayout.GuiFit> guiFits = new java.util.IdentityHashMap<>();
    public LegacyItemRenderer(ModernContentBridge bridge, String itemKey) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.bridge = bridge; this.itemKey = itemKey;
        this.blockKey = bridge.declarations().items().stream().filter(item -> item.key().equals(itemKey)).findFirst().map(github.com.gengyoubo.mfcl.bridge.LegacyContent.Item::blockKey).orElse(null);
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        var legacyTag = stack.getTagElement(ModernContentBridge.STACK_TAG);
        String tag = legacyTag == null ? "" : legacyTag.getString("snbt");
        int metadata = ModernContentBridge.metadata(stack);
        String baseKey = github.com.gengyoubo.mfcl.bridge.LegacyContent.geometryKey(itemKey, metadata, "");
        List<LegacyVertexStream.Primitive> primitives = null;
        String foundType = null;
        // The original IItemRenderer owners (wands, thaumometer, bows) capture distinct EQUIPPED, first-person and
        // entity transforms; replay the one matching the modern display context instead of the inventory snapshot.
        for (String type : contextTypes(context)) {
            primitives = bridge.pack().contextGeometry().get(type + "|" + github.com.gengyoubo.mfcl.bridge.LegacyContent.geometryKey(itemKey, metadata, tag));
            if (primitives != null && !primitives.isEmpty()) { foundType = type; break; }
            primitives = null;
        }
        if (primitives == null) primitives = bridge.pack().geometry().get(github.com.gengyoubo.mfcl.bridge.LegacyContent.geometryKey(itemKey, metadata, tag));
        if (primitives == null) primitives = bridge.pack().geometry().get(baseKey);
        if (primitives == null || primitives.isEmpty()) return;
        var worldLayers = bridge.blockRendering().isolated(blockKey, metadata);
        boolean translucent = !worldLayers.isEmpty() && worldLayers.stream().allMatch(layer -> layer.pass() == 1);
        pose.pushPose();
        // Host item space centers on the slot; the original IItemRenderer geometry is replayed with the base matrix
        // the old pipeline supplied for its ItemRenderType, rather than the ISBRH block display transform.
        pose.translate(0.5, 0.5, 0.5);
        if ("EQUIPPED".equals(foundType)) applyLegacyEquippedTransform(pose);
        else applyDefaultItemTransform(context, pose, primitives);
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        Map<String, List<LegacyVertexStream.Primitive>> byTexture = new LinkedHashMap<>();
        for (LegacyVertexStream.Primitive primitive : primitives) byTexture.computeIfAbsent(primitive.texture(), ignored -> new ArrayList<>()).add(primitive);
        for (var entry : byTexture.entrySet()) {
            ResourceLocation spriteId = ResourceLocation.tryParse(entry.getKey());
            if (spriteId == null) continue;
            if (entry.getKey().contains("textures/")) { // old model renderer texture, UVs already 0..1
                LegacyWorldRenderer.replayStandalone(pose, buffer.getBuffer(translucent ? RenderType.entityTranslucent(spriteId) : RenderType.entityCutoutNoCull(spriteId)), entry.getValue(), light, overlay);
                continue;
            }
            TextureAtlasSprite sprite = atlas.apply(spriteId);
            if (sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
                if (missingSprites.add(spriteId.toString())) com.mojang.logging.LogUtils.getLogger().warn("MFCL: captured sprite is not stitched: {}", spriteId);
                continue;
            }
            LegacyWorldRenderer.replay(pose, buffer.getBuffer(translucent ? RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS) : RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), sprite, entry.getValue(), light, overlay);
        }
        pose.popPose();
    }
    /**
     * The base matrix RenderBiped.func_77029_c establishes before invoking a plain (non-block, non-bow, non-full-3D)
     * ItemRenderType.EQUIPPED callback. The captured geometry holds only the callback's own transform, so this must
     * be re-applied to land in the same hand coordinates the old client used.
     */
    private static void applyLegacyEquippedTransform(PoseStack pose) {
        // bipedRightArm.postRender(0.0625) lands on the arm end; this is RenderBiped's grip offset from there.
        pose.translate(-0.0625f, 0.4375f, 0.0625f);
        pose.translate(0.25f, 0.1875f, -0.1875f);
        pose.scale(0.375f, 0.375f, 0.375f);
        pose.mulPose(Axis.ZP.rotationDegrees(60));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.ZP.rotationDegrees(20));
    }
    /** Host display transforms for centered ISBRH block captures and the inventory IItemRenderer snapshot. */
    private void applyDefaultItemTransform(ItemDisplayContext context, PoseStack pose, List<LegacyVertexStream.Primitive> primitives) {
        switch (context) {
            case GUI -> {
                var fit = guiFits.computeIfAbsent(primitives, LegacyItemLayout::fitGui);
                pose.translate(fit.x(), fit.y(), 0); pose.scale((float)fit.scale(), (float)fit.scale(), (float)fit.scale());
                pose.mulPose(Axis.XP.rotationDegrees(30)); pose.mulPose(Axis.YP.rotationDegrees(225)); pose.scale(0.625f, 0.625f, 0.625f);
            }
            case GROUND -> { pose.translate(0, 0.1875, 0); pose.scale(0.25f, 0.25f, 0.25f); }
            case FIXED -> pose.scale(0.5f, 0.5f, 0.5f);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> { pose.mulPose(Axis.YP.rotationDegrees(45)); pose.translate(0, 0.125, 0); pose.scale(0.375f, 0.375f, 0.375f); }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> { pose.mulPose(Axis.YP.rotationDegrees(45)); pose.translate(0, 0.125, 0); pose.scale(0.375f, 0.375f, 0.375f); }
            default -> pose.scale(0.5f, 0.5f, 0.5f);
        }
    }
    private static List<String> contextTypes(ItemDisplayContext context) {
        return switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> List.of("EQUIPPED_FIRST_PERSON", "EQUIPPED", "INVENTORY");
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> List.of("EQUIPPED", "INVENTORY");
            case GROUND, HEAD -> List.of("ENTITY", "INVENTORY");
            default -> List.of("INVENTORY");
        };
    }
}
