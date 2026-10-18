package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

/** Replays the original caller/helper/item pipeline for the selected display context, preserving draw order. */
public final class LegacyItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final ModernContentBridge bridge;
    private final String itemKey;
    private final Map<List<LegacyVertexStream.Primitive>, LegacyItemLayout.GuiFit> guiFits = new IdentityHashMap<>();
    public LegacyItemRenderer(ModernContentBridge bridge, String itemKey) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.bridge = bridge; this.itemKey = itemKey;
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        var tag = stack.getTagElement(ModernContentBridge.STACK_TAG);
        String base = LegacyContent.geometryKey(itemKey,ModernContentBridge.metadata(stack),"");
        String key = LegacyContent.geometryKey(itemKey,ModernContentBridge.metadata(stack),tag == null ? "" : tag.getString("snbt"));
        String type = contextType(context);
        var primitives = bridge.pack().contextGeometry().get(type + "|" + key);
        if (primitives == null) primitives = bridge.pack().contextGeometry().get(type + "|" + base);
        boolean originalPipeline = primitives != null && !primitives.isEmpty();
        if (!originalPipeline) {
            primitives = bridge.pack().geometry().get(key);
            if (primitives == null) primitives = bridge.pack().geometry().get(base);
        }
        if (primitives == null || primitives.isEmpty()) return;
        pose.pushPose();
        try {
            pose.translate(.5,.5,.5); // ItemRenderer already translated by (-.5,-.5,-.5).
            if (originalPipeline) {
                boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                if (type.equals("EQUIPPED")) LegacyItemTransformBridge.handToArm(pose,left);
                else if (type.equals("EQUIPPED_FIRST_PERSON") && left) pose.scale(-1,1,1);
                else if (context == ItemDisplayContext.GUI) {
                    var fit = guiFits.computeIfAbsent(primitives,p -> LegacyItemLayout.fit(LegacyItemLayout.bounds(p,LegacyVertexStream.identity())));
                    pose.translate(fit.x(),fit.y(),0); pose.scale((float)fit.scale(),(float)fit.scale(),(float)fit.scale());
                }
            } else blockTransform(context,pose,primitives);
            replay(primitives,pose,buffer,light,overlay);
        } finally { pose.popPose(); }
    }
    private void replay(List<LegacyVertexStream.Primitive> primitives, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance(); var atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        int first = 0;
        while (first < primitives.size()) {
            var primitive = primitives.get(first); int end = first+1;
            while (end < primitives.size() && primitives.get(end).texture().equals(primitive.texture()) && primitives.get(end).state().equals(primitive.state())) end++;
            var group = primitives.subList(first,end);
            ResourceLocation texture = ResourceLocation.parse(primitive.texture());
            boolean standalone = primitive.texture().contains("textures/");
            if (primitive.texture().equals(LegacyItemRendering.PLAYER_SKIN)) {
                texture = mc.player == null ? net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin() : mc.player.getSkinTextureLocation();
                // 1.7.10 arm models use the upper half of a 64x32 skin; current skins are 64x64.
                group = group.stream().map(p -> new LegacyVertexStream.Primitive(p.mode(),p.texture(),p.vertices().stream().map(v ->
                        new LegacyVertexStream.Vertex(v.x(),v.y(),v.z(),v.u(),v.v()*.5f,v.nx(),v.ny(),v.nz(),v.argb())).toList(),p.state())).toList();
            }
            var renderType = LegacyRenderTypes.get(standalone ? texture : TextureAtlas.LOCATION_BLOCKS,primitive.state());
            int brightness = primitive.state().blend() && primitive.state().destinationBlend() == 1 ? 0xF000F0 : light;
            if (standalone) LegacyWorldRenderer.replayStandalone(pose,buffers.getBuffer(renderType),group,brightness,overlay);
            else LegacyWorldRenderer.replay(pose,buffers.getBuffer(renderType),atlas.apply(texture),group,brightness,overlay);
            if (buffers instanceof MultiBufferSource.BufferSource immediate) immediate.endBatch(renderType);
            first = end;
        }
    }
    static String contextType(ItemDisplayContext context) {
        return switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> "EQUIPPED_FIRST_PERSON";
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> "EQUIPPED";
            case GROUND, HEAD, FIXED -> "ENTITY";
            default -> "INVENTORY";
        };
    }
    private void blockTransform(ItemDisplayContext context, PoseStack pose, List<LegacyVertexStream.Primitive> geometry) {
        switch (context) {
            case GUI -> {
                var fit = guiFits.computeIfAbsent(geometry,LegacyItemLayout::fitGui);
                pose.translate(fit.x(),fit.y(),0); pose.scale((float)fit.scale(),(float)fit.scale(),(float)fit.scale());
                pose.mulPose(Axis.XP.rotationDegrees(30)); pose.mulPose(Axis.YP.rotationDegrees(225)); pose.scale(.625f,.625f,.625f);
            }
            case GROUND -> { pose.translate(0,.1875,0); pose.scale(.25f,.25f,.25f); }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                pose.mulPose(Axis.YP.rotationDegrees(45)); pose.translate(0,.125,0); pose.scale(.375f,.375f,.375f);
            }
            default -> pose.scale(.5f,.5f,.5f);
        }
    }
}
