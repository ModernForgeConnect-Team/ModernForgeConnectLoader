package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Frame conversion only: original item-relative transforms are captured by LegacyItemRendering. */
final class LegacyItemTransformBridge {
    private LegacyItemTransformBridge() { }
    static void handToArm(PoseStack pose, boolean left) {
        // Inverse of 1.20.1 ItemInHandLayer.renderArmWithItem, after translateToHand().
        pose.translate(left ? .0625 : -.0625,-.125,.625);
        pose.mulPose(Axis.YP.rotationDegrees(-180)); pose.mulPose(Axis.XP.rotationDegrees(90));
        if (left) pose.scale(-1,1,1);
    }
}
