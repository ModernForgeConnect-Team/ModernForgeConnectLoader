package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.*;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import java.util.*;

/** Per-chunk immutable geometry; original side tests and connected-texture selection run before baking. */
public final class LegacyBlockModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Map<RenderType, List<BakedQuad>>> GEOMETRY = new ModelProperty<>();
    private final ModernContentBridge bridge;
    private final String key;
    private final int metadata;
    private volatile Map<RenderType, List<BakedQuad>> fallback;
    public LegacyBlockModel(BakedModel original, ModernContentBridge bridge, String key, int metadata) {
        super(original); this.bridge = bridge; this.key = key; this.metadata = metadata;
    }
    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        try {
            var layers = bridge.blockRendering().capture(key, metadata, bridge.renderView(level, pos));
            return data.derive().with(GEOMETRY, bake(layers)).build();
        } catch (Exception failure) { throw new IllegalStateException("Cannot render legacy block " + key + "@" + metadata + " at " + pos, failure); }
    }
    private Map<RenderType, List<BakedQuad>> geometry(ModelData data) {
        var value = data.get(GEOMETRY);
        if (value != null) return value; // Empty geometry is valid for a completely enclosed block.
        value = fallback;
        if (value == null) fallback = value = bake(bridge.blockRendering().isolated(key, metadata));
        return value;
    }
    private Map<RenderType, List<BakedQuad>> bake(List<LegacyBlockRendering.Layer> layers) {
        var result = new LinkedHashMap<RenderType, List<BakedQuad>>();
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        for (var layer : layers) {
            var quads = new ArrayList<BakedQuad>();
            var consumer = new QuadBakingVertexConsumer(quads::add);
            consumer.setTintIndex(-1); consumer.setShade(false); consumer.setHasAmbientOcclusion(false);
            for (var primitive : layer.geometry()) {
                var sprite = atlas.apply(ResourceLocation.parse(bridge.pack().worldTexture(primitive.texture())));
                consumer.setSprite(sprite);
                var vertices = primitive.vertices();
                for (int[] indices : LegacyWorldRenderer.quads(primitive.mode(), vertices.size())) {
                    var a = vertices.get(indices[0]); var b = vertices.get(indices[1]); var c = vertices.get(indices[2]);
                    double bx = b.x()-a.x(), by = b.y()-a.y(), bz = b.z()-a.z();
                    double cx = c.x()-a.x(), cy = c.y()-a.y(), cz = c.z()-a.z();
                    Direction face = Direction.getNearest((float)(by*cz-bz*cy), (float)(bz*cx-bx*cz), (float)(bx*cy-by*cx));
                    consumer.setDirection(face);
                    for (int index : indices) {
                        var v = vertices.get(index); int color = v.argb();
                        consumer.vertex(v.x(), v.y(), v.z()).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
                                .uv(sprite.getU(v.u()*16), sprite.getV(v.v()*16)).uv2(0, 0)
                                .normal(face.getStepX(), face.getStepY(), face.getStepZ()).endVertex();
                    }
                }
            }
            result.put(layer.pass() == 1 ? RenderType.translucent() : RenderType.cutout(), List.copyOf(quads));
        }
        return Collections.unmodifiableMap(result);
    }
    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean useAmbientOcclusion(BlockState state, RenderType type) { return false; }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.of(geometry(data).keySet());
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        if (side != null) return List.of(); // ISBRH already performed contextual face culling.
        var geometry = geometry(data);
        return type == null ? geometry.values().stream().flatMap(Collection::stream).toList() : geometry.getOrDefault(type, List.of());
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }
}
