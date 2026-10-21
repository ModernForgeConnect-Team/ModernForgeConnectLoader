package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs original IC2 texture registration in a fresh JVM, including its TextureMap casts and custom sprites. */
class LegacyIconMetadataIntegrationTest {
    @TempDir Path temporary;

    @Test void bootsOriginalIc2AndPreservesCustomSpriteMetadata() throws Exception {
        String support = System.getProperty("mfcl.test.legacySupportDir", "");
        String ic2 = System.getProperty("mfcl.test.ic2Jar", "");
        assumeTrue(!support.isBlank() && !ic2.isBlank(), "Supply -PlegacySupportDir and -Pic2Jar");
        Path mods = Files.createDirectories(temporary.resolve("mods/1.7.10"));
        Files.copy(Path.of(ic2), mods.resolve(Path.of(ic2).getFileName()));
        Path runtime = Path.of(support);
        if (Files.isDirectory(runtime.resolve("runtime"))) runtime = runtime.resolve("runtime");
        Path log = temporary.resolve("icons.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("mfcl.test.runtimeClasspath"),
                getClass().getName(), temporary.toString(), runtime.toAbsolutePath().toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(180, TimeUnit.SECONDS), ()->"IC2 icon registration timed out: "+readLog(log));
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("MFCL_IC2_ICON_METADATA_PASS"), output);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        }
    }

    private static String readLog(Path log) { try{return Files.readString(log);}catch(Exception failure){return failure.toString();} }

    public static void main(String[] args) throws Exception {
        var service = new LegacyRuntimeService();
        var report = service.start(Path.of(args[0]), Path.of(args[1]));
        try (var runtime = service.runtime()) {
            assertTrue(report.legacyFmlStarted(), report.toString());
            assertTrue(service.loadedModIds().contains("IC2"), report.toString());
            var data = runtime.services().items();
            ClassLoader loader = data.loader();
            Class<?> block = loader.loadClass("net.minecraft.block.Block");
            Class<?> customSprite = loader.loadClass("ic2.core.block.BlockTextureStitched");
            Class<?> sprite = loader.loadClass("net.minecraft.client.renderer.texture.TextureAtlasSprite");
            Class<?> atlas = loader.loadClass("net.minecraft.client.renderer.texture.TextureMap");
            Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft");
            Object client = minecraft.getMethod("func_71410_x").invoke(null);
            Object manager = minecraft.getField("field_71446_o").get(client);
            Class<?> location = loader.loadClass("net.minecraft.util.ResourceLocation");
            for (String field : new String[]{"field_110575_b", "field_110576_c"}) {
                Object key = atlas.getField(field).get(null);
                Object map = manager.getClass().getMethod("func_110581_b", location).invoke(manager, key);
                assertTrue(atlas.isInstance(map), "Atlas missing from texture manager");
                Object fallback = loader.loadClass("ic2.core.block.RenderBlock").getMethod("getMissingIcon", location).invoke(null, key);
                assertNotNull(fallback);
                assertEquals(16, sprite.getMethod("func_94211_a").invoke(fallback));
                if (field.equals("field_110575_b")) {
                    assertSame(map, minecraft.getMethod("func_147117_R").invoke(client));
                    Object registered = atlas.getMethod("getTextureExtry", String.class).invoke(map, "stone");
                    assertNotNull(registered);
                    assertSame(registered, atlas.getMethod("func_110572_b", String.class).invoke(map, "stone"));
                }
            }
            int custom = 0;
            int comparedPixels=0;
            for (Object value : (Iterable<?>) block.getField("field_149771_c").get(null)) {
                Object icon = block.getMethod("func_149691_a", int.class, int.class).invoke(value, 0, 0);
                if (!customSprite.isInstance(icon)) continue;
                String name = (String) sprite.getMethod("func_94215_i").invoke(icon);
                float[] cell = data.iconLayout().get(name);
                assertNotNull(cell, "Custom sprite was omitted: " + name);
                assertEquals(16, sprite.getMethod("func_94211_a").invoke(icon));
                assertEquals(16, sprite.getMethod("func_94216_b").invoke(icon));
                assertEquals(cell[0], (float) sprite.getMethod("func_94209_e").invoke(icon));
                assertEquals(cell[3], (float) sprite.getMethod("func_94210_h").invoke(icon));
                assertTrue(cell[2] > cell[0] && cell[3] > cell[1]);
                assertEquals(name, data.iconAt((cell[0] + cell[2]) / 2, (cell[1] + cell[3]) / 2));
                byte[] png=data.iconTexture(name);
                assertNotNull(png,"Custom loader pixels were omitted: "+name);
                var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
                assertEquals(16,image.getWidth());
                int split=name.lastIndexOf(':');String path=name.substring(name.indexOf(':')+1,split);
                try(var input=loader.getResourceAsStream("assets/ic2/textures/blocks/"+path+".png")) {
                    if(input!=null) {
                        var source=javax.imageio.ImageIO.read(input);
                        if(source.getHeight()==16) {
                            int columns=source.getWidth()/16,index=Integer.parseInt(name.substring(split+1));
                            int column=columns==2?index/6:index%columns;
                            assertArrayEquals(source.getRGB(column*16,0,16,16,null,0,16),image.getRGB(0,0,16,16,null,0,16),name);
                            comparedPixels++;
                        }
                    }
                }
                custom++;
            }
            assertTrue(custom > 0, "No original IC2 custom block sprite was checked");
            assertTrue(comparedPixels>0,"No custom sprite was compared with the original texture sheet");
            var original = data.iconLayout();
            data.refreshIconMetadata();
            assertEquals(original.keySet(), data.iconLayout().keySet());
            original.forEach((name, cell) -> assertArrayEquals(cell, data.iconLayout().get(name)));
            System.out.println("MFCL_IC2_ICON_METADATA_PASS customBlocks=" + custom);
        }
    }
}
