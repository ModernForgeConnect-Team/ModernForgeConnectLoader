package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * Caller matrices Forge 1.7.10 establishes before it invokes {@code IItemRenderer.renderItem} for a given
 * {@code ItemRenderType}. The captured geometry already contains the renderer's own transform, so replaying it
 * with the matching caller matrix (and the modern unit conversion) reproduces the original placement instead of
 * guessing display angles. Each value is copied from the 1.7.10 bytecode/source call site, not tuned by eye.
 */
final class LegacyItemTransformBridge {
    private LegacyItemTransformBridge() { }
    /**
     * {@code ForgeHooksClient.renderInventoryItem} (INVENTORY_BLOCK): the old GUI origin sat at the slot top-left
     * offset by (-2,3); after the old relative base the model origin lands on the slot centre. The modern pose
     * origin already is the slot centre, so only the relative part is kept and converted to modern model units
     * (1 unit = 16px): S(10) -> S(0.625), then S(1,1,-1) Rx(210) Ry(45).
     */
    static void inventory(PoseStack pose) {
        pose.scale(0.625f, 0.625f, -0.625f);
        pose.mulPose(Axis.XP.rotationDegrees(210));
        pose.mulPose(Axis.YP.rotationDegrees(45));
    }
    /**
     * {@code RenderBiped.func_77029_c}: the held-item base after {@code bipedRightArm.postRender(0.0625)}. The arm
     * bone itself is supplied by the modern player rig, so only the grip offset and the item-relative branch are
     * restored.
     */
    static void equipped(PoseStack pose, boolean blockItem) {
        pose.translate(-0.0625f, 0.4375f, 0.0625f);
        if (blockItem) {
            pose.translate(0f, 0.1875f, -0.3125f);
            pose.mulPose(Axis.XP.rotationDegrees(20));
            pose.mulPose(Axis.YP.rotationDegrees(45));
            pose.scale(0.375f, -0.375f, -0.375f);
        } else {
            pose.translate(0.25f, 0.1875f, -0.1875f);
            pose.scale(0.375f, 0.375f, 0.375f);
            pose.mulPose(Axis.ZP.rotationDegrees(60));
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.mulPose(Axis.ZP.rotationDegrees(20));
        }
    }
}
