package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The old Minecraft.effectRenderer, which mods feed with their own EntityFX (TC4 lightning bolts...). The host drives
 * it like the old client loop did: updateEffects each client tick, renderParticles/renderLitParticles each frame inside
 * a capture frame, replayed relative to the camera.
 */
final class LegacyEffects {
    // The vanilla 1.7.10 particle sheet has no host counterpart; its sprites would only show as missing textures.
    private static final String VANILLA_SHEET = "textures/particle/particles.png";
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private LegacyEffects() {}
    /** A fresh renderer for each bound client world, as Minecraft.loadWorld did. */
    static void bind(LegacyServices services, Object world) throws Exception {
        var loader = services.loader(); var mc = loader.loadClass("net.minecraft.client.Minecraft"); Object client = mc.getMethod("func_71410_x").invoke(null);
        Object effects = world == null ? null : loader.loadClass("net.minecraft.client.particle.EffectRenderer")
                .getConstructor(loader.loadClass("net.minecraft.world.World"), loader.loadClass("net.minecraft.client.renderer.texture.TextureManager"))
                .newInstance(world, mc.getField("field_71446_o").get(client));
        mc.getField("field_71452_i").set(client, effects);
    }
    static void install(ModernContentBridge content) {
        var services = content.services(); var replay = new LegacyBlockEntityRenderer(content);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            var mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.START || mc.level == null || mc.isPaused()) return;
            guard(() -> services.items().inLegacy(() -> {
                Object effects = effects(services); if (effects != null) effects.getClass().getMethod("func_78868_a").invoke(effects);
                return null;
            }));
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            var camera = event.getCamera(); var eye = camera.getPosition(); float partial = event.getPartialTick();
            guard(() -> {
                List<LegacyVertexStream.Primitive> primitives = services.items().inLegacy(() -> {
                    Object effects = effects(services); if (effects == null) return List.<LegacyVertexStream.Primitive>of();
                    Object world = services.worlds().findBound(mc.level); if (world == null) return List.<LegacyVertexStream.Primitive>of();
                    // Sets the old view entity at the camera and ActiveRenderInfo, which particle billboards are built from.
                    content.blockRendering().tiles().view(world, eye.x, eye.y, eye.z, camera.getYRot(), camera.getXRot(), mc.player == null ? 0 : mc.player.tickCount);
                    var loader = services.loader(); var entity = loader.loadClass("net.minecraft.entity.Entity");
                    var mcType = loader.loadClass("net.minecraft.client.Minecraft"); Object viewer = mcType.getField("field_71451_h").get(mcType.getMethod("func_71410_x").invoke(null));
                    try (var frame = LegacyWorldGraphics.begin()) {
                        effects.getClass().getMethod("func_78874_a", entity, float.class).invoke(effects, viewer, partial);
                        effects.getClass().getMethod("func_78872_b", entity, float.class).invoke(effects, viewer, partial);
                        return LegacyIconResolver.resolve(frame.primitives(), services.items().iconLayout()).stream()
                                .filter(primitive -> !primitive.texture().endsWith(VANILLA_SHEET)).toList();
                    }
                });
                if (primitives.isEmpty()) return null;
                replay.render(primitives, event.getPoseStack(), mc.renderBuffers().bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY);
                mc.renderBuffers().bufferSource().endBatch();
                return null;
            });
        });
    }
    private static Object effects(LegacyServices services) throws Exception {
        var mc = services.loader().loadClass("net.minecraft.client.Minecraft");
        return mc.getField("field_71452_i").get(mc.getMethod("func_71410_x").invoke(null));
    }
    private interface Work { Object run() throws Exception; }
    private static void guard(Work work) {
        try { work.run(); }
        catch (Exception | LinkageError failure) {
            if (REPORTED.compareAndSet(false, true)) com.mojang.logging.LogUtils.getLogger().error("MFCL original effect renderer failed; later failures are not logged", failure);
        }
    }
}
