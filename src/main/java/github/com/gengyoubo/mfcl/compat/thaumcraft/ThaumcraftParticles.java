package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyIconResolver;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.bridge.LegacyWorldGraphics;
import github.com.gengyoubo.mfcl.client.LegacyBlockEntityRenderer;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * TC4 draws its effects (crucible bubbles/froth, wand and infusion sparkles...) through its own
 * thaumcraft.client.fx.ParticleEngine, which the old FML client tick and RenderWorldLastEvent drive. Those old events
 * never fire here, so the host drives the original engine: its own updateParticles each client tick, and its own
 * onRenderWorldLast inside a capture frame each render, replayed like any other original world geometry.
 */
final class ThaumcraftParticles {
    private static boolean reported;
    private ThaumcraftParticles() {}

    static void install(LegacyServices services) {
        var content = ModernContentBridge.active();
        var replay = new LegacyBlockEntityRenderer(content);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            var mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
            guard(() -> services.items().inLegacy(() -> {
                Object engine = engine(services); if (engine == null) return null;
                var loader = services.loader();
                Class<?> tick = loader.loadClass("cpw.mods.fml.common.gameevent.TickEvent$ClientTickEvent");
                Object phase = loader.loadClass("cpw.mods.fml.common.gameevent.TickEvent$Phase").getField("END").get(null);
                Object legacyEvent = tick.getConstructor(phase.getClass()).newInstance(phase);
                engine.getClass().getMethod("updateParticles", tick).invoke(engine, legacyEvent);
                return null;
            }));
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            var camera = event.getCamera(); var eye = camera.getPosition();
            float partial = event.getPartialTick();
            guard(() -> {
                List<LegacyVertexStream.Primitive> primitives = services.items().inLegacy(() -> {
                    Object engine = engine(services); if (engine == null) return List.<LegacyVertexStream.Primitive>of();
                    // Sets the old view entity and ActiveRenderInfo: particles are drawn relative to the camera.
                    content.blockRendering().tiles().view(services.worlds().bound(mc.level), eye.x, eye.y, eye.z, camera.getYRot(), camera.getXRot(),
                            mc.player == null ? 0 : mc.player.tickCount);
                    var loader = services.loader();
                    Class<?> last = loader.loadClass("net.minecraftforge.client.event.RenderWorldLastEvent");
                    Object legacyEvent = last.getConstructor(loader.loadClass("net.minecraft.client.renderer.RenderGlobal"), float.class).newInstance(null, partial);
                    try (var frame = LegacyWorldGraphics.begin()) {
                        engine.getClass().getMethod("onRenderWorldLast", last).invoke(engine, legacyEvent);
                        return LegacyIconResolver.resolve(frame.primitives(), services.items().iconLayout());
                    }
                });
                if (primitives.isEmpty()) return null;
                replay.render(primitives, event.getPoseStack(), mc.renderBuffers().bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY);
                mc.renderBuffers().bufferSource().endBatch();
                return null;
            });
        });
    }
    private static Object engine(LegacyServices services) throws Exception {
        return services.loader().loadClass("thaumcraft.client.fx.ParticleEngine").getField("instance").get(null);
    }
    private interface Work { Object run() throws Exception; }
    private static void guard(Work work) {
        try { work.run(); }
        catch (Exception | LinkageError failure) {
            if (!reported) { reported = true; LogUtils.getLogger().error("MFCL TC4 particle engine bridge failed; later failures are not logged", failure); }
        }
    }
}
