import github.com.gengyoubo.mfcl.bridge.LegacyOpenGl;
import github.com.gengyoubo.mfcl.legacy.runtime.IsolatedLegacyClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises original LWJGL2 bytecode through the actual two-layer legacy loader, without natives. */
class LegacyDisplayAdapterTest {
    @TempDir Path temporary;

    @Test void displayBeforeGlPreservesSealedPackage() throws Exception { verify(false); }
    @Test void glBeforeDisplayPreservesSealedPackage() throws Exception { verify(true); }

    private void verify(boolean glFirst) throws Exception {
        String support = System.getProperty("mfcl.test.legacySupportDir");
        assumeTrue(support != null, "Supply -PlegacySupportDir=<runtime directory or its parent> for original LWJGL2 inputs");
        Path runtime = Path.of(support);
        if (!Files.isRegularFile(runtime.resolve("lwjgl-2.9.1.jar"))) runtime = runtime.resolve("runtime");
        assertTrue(Files.isRegularFile(runtime.resolve("lwjgl-2.9.1.jar")), "Configured support directory must contain the prepared LWJGL2 runtime");
        Path helper = temporary.resolve("helper.jar");
        try (var input = getClass().getResourceAsStream("/mfcl/mfcl-legacy-runtime.jar")) {
            assertNotNull(input);
            Files.copy(input, helper);
        }
        var urls = new ArrayList<URL>();
        urls.add(helper.toUri().toURL());
        try (var jars = Files.list(runtime)) {
            for (Path jar : jars.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) urls.add(jar.toUri().toURL());
        }
        URL[] inputs = urls.toArray(URL[]::new);
        try (var root = new IsolatedLegacyClassLoader(inputs, getClass().getClassLoader());
             var legacy = (URLClassLoader) root.loadClass("github.com.gengyoubo.mfcl.legacy1710.boot.LegacyLaunchLoader")
                     .getConstructor(URL[].class, ClassLoader.class).newInstance(inputs, root)) {
            if (glFirst) Class.forName("org.lwjgl.opengl.GL11", true, legacy);
            Class<?> display = Class.forName("org.lwjgl.opengl.Display", true, legacy);
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL11", true, legacy);
            assertSame(legacy, display.getClassLoader());
            assertSame(legacy, gl.getClassLoader());
            assertSame(display.getPackage(), gl.getPackage());
            assertEquals(display.getProtectionDomain().getCodeSource().getLocation(), gl.getProtectionDomain().getCodeSource().getLocation());
            assertTrue(display.getPackage().isSealed(display.getProtectionDomain().getCodeSource().getLocation()));
            // Load an unadapted class in the same sealed package after the adapted classes.
            Class.forName("org.lwjgl.opengl.DisplayMode", true, legacy);
            assertEquals(false, display.getMethod("isCreated").invoke(null));
            assertEquals(0, display.getMethod("getWidth").invoke(null));

            var state = new LegacyOpenGl.DisplayState(1920, 1080, 40, 50, true, true, true, false, false, true);
            LegacyOpenGl.attach(gl, new LegacyOpenGl.Backend() {
                @Override public Object call(String operation, Object[] arguments) { throw new AssertionError("Display query entered GL dispatch"); }
                @Override public LegacyOpenGl.DisplayState displayState() { return state; }
            });
            try {
                String[] queries = {"getWidth", "getHeight", "getX", "getY", "isCreated", "isActive", "isVisible", "isFullscreen", "isCloseRequested", "isResizable"};
                Object[] expected = {1920, 1080, 40, 50, true, true, true, false, false, true};
                for (int i = 0; i < queries.length; i++) assertEquals(expected[i], display.getMethod(queries[i]).invoke(null), queries[i]);
                for (String operation : new String[]{"create", "destroy", "update", "processMessages", "swapBuffers", "makeCurrent", "releaseContext"}) {
                    var failure = assertThrows(InvocationTargetException.class, () -> display.getMethod(operation).invoke(null), operation);
                    assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
                    assertTrue(failure.getCause().getMessage().contains("host owns the window"));
                }
                var failure = assertThrows(InvocationTargetException.class, () -> display.getMethod("setTitle", String.class).invoke(null, "legacy"));
                assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
            } finally { LegacyOpenGl.detach(gl); }
            assertEquals(false, display.getMethod("isCreated").invoke(null));
        }
    }
}
