package github.com.gengyoubo.mfcl.legacy.runtime;

import github.com.gengyoubo.mfcl.bridge.LegacyBridgeMarker;
import github.com.gengyoubo.mfcl.legacy.TestArchives;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacyIsolationTest {
    @TempDir Path temporary;

    @Test void legacyAndHostForgeNamespacesNeverShareDefinitionsOrFallback() throws Exception {
        var hostJar = TestArchives.jar(temporary.resolve("host.jar"), Map.of(), Map.of(
                "net/minecraftforge/Shared.class", TestArchives.classBytes("net.minecraftforge.Shared", null),
                "net/minecraftforge/HostOnly.class", TestArchives.classBytes("net.minecraftforge.HostOnly", null)));
        var legacyJar = TestArchives.jar(temporary.resolve("legacy.jar"), Map.of(), Map.of(
                "net/minecraftforge/Shared.class", TestArchives.classBytes("net.minecraftforge.Shared", null)));
        try (var host = new URLClassLoader(new URL[]{hostJar.toUri().toURL()}, getClass().getClassLoader());
             var legacy = new IsolatedLegacyClassLoader(new URL[]{legacyJar.toUri().toURL()}, host)) {
            assertNotSame(host.loadClass("net.minecraftforge.Shared"), legacy.loadClass("net.minecraftforge.Shared"));
            assertThrows(ClassNotFoundException.class, () -> legacy.loadClass("net.minecraftforge.HostOnly"));
            assertThrows(ClassNotFoundException.class, () -> legacy.loadClass("github.com.gengyoubo.mfcl.legacy.LegacyPaths"));
            assertSame(LegacyBridgeMarker.class, legacy.loadClass(LegacyBridgeMarker.class.getName()));
            assertSame(String.class, legacy.loadClass("java.lang.String"));
            assertNotNull(legacy.loadClass("java.sql.Driver"));
        }
    }

    @Test void resourcesDoNotLeakFromTheHost() throws Exception {
        var hostJar = TestArchives.jar(temporary.resolve("host.jar"), Map.of(), Map.of("host-only.txt", TestArchives.text("host")));
        try (var host = new URLClassLoader(new URL[]{hostJar.toUri().toURL()}, getClass().getClassLoader());
             var legacy = new IsolatedLegacyClassLoader(new URL[0], host)) {
            assertNotNull(host.getResource("host-only.txt"));
            assertNull(legacy.getResource("host-only.txt"));
        }
    }
}
