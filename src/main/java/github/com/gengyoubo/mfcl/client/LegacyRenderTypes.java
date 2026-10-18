package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Isolated modern render states for the old alpha/additive/depth-write batches. */
public abstract class LegacyRenderTypes extends RenderType {
    private record Key(ResourceLocation texture, LegacyVertexStream.RenderState state) {}
    private static final Map<Key, RenderType> TYPES = new HashMap<>();
    private LegacyRenderTypes() { super("mfcl_unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}); }
    public static RenderType get(ResourceLocation texture, LegacyVertexStream.RenderState state) {
        return TYPES.computeIfAbsent(new Key(texture, state), key -> {
            var transparency = new TransparencyStateShard("mfcl_blend", () -> {
                if (state.blend()) { RenderSystem.enableBlend(); RenderSystem.blendFunc(state.sourceBlend(), state.destinationBlend()); }
                else RenderSystem.disableBlend();
            }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
            var setup = CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false)).setTransparencyState(transparency)
                    .setCullState(state.cull() ? CULL : NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setWriteMaskState(new WriteMaskStateShard(true, state.depthWrite())).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(true);
            return create("mfcl_legacy_tile", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 65536, false, state.blend(), setup);
        });
    }
}
