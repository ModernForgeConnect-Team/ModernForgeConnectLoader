package github.com.gengyoubo.mfcl.legacy1710;

import java.lang.reflect.Method;

/**
 * Runs the real old inventory block renderer inside the private legacy domain. The host wraps the call in a
 * {@code LegacyWorldGraphics} capture, so the resulting geometry never touches the host OpenGL context.
 * Reflection keeps this source set free of Minecraft compile-time types.
 */
public final class LegacyBlockRenderer {
    public boolean renderInventory(String blockKey, int metadata, int modelId) throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Class<?> blockType = loader.loadClass("net.minecraft.block.Block");
        Object registry = blockType.getField("field_149771_c").get(null);
        Object block = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, blockKey);
        if (block == null) return false;
        int renderType = (Integer) blockType.getMethod("func_149645_b").invoke(block);
        Class<?> renderingType = loader.loadClass("cpw.mods.fml.client.registry.RenderingRegistry");
        Object rendering = renderingType.getMethod("instance").invoke(null);
        if (rendering == null) return false;
        Class<?> renderBlocksType = loader.loadClass("net.minecraft.client.renderer.RenderBlocks");
        Object renderBlocks = renderBlocksType.getConstructor().newInstance();
        Method render = renderingType.getMethod("renderInventoryBlock", renderBlocksType, blockType, int.class, int.class);
        render.invoke(rendering, renderBlocks, block, metadata, renderType);
        return true;
    }
}
