import github.com.gengyoubo.mfcl.legacy.*;
import github.com.gengyoubo.mfcl.legacy.runtime.IsolatedLegacyClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import java.nio.file.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyMavenDiscoveryTest {
    @TempDir Path game;

    @Test void resolverOutputIsDiscoveredAndAccessibleInLegacyLoader() throws Exception {
        var paths = new LegacyPaths(game);
        Files.createDirectories(paths.gradleMavenDirectory());
        Path temporary = paths.gradleMavenDirectory().resolve("input.jar");
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Library", null, "java/lang/Object", null);
        writer.visitEnd();
        try (var jar = new JarOutputStream(Files.newOutputStream(temporary))) {
            jar.putNextEntry(new JarEntry("fixture/Library.class")); jar.write(writer.toByteArray()); jar.closeEntry();
        }
        String hash = LegacyDiscovery.sha256(temporary);
        Path artifact = Files.move(temporary, paths.gradleMavenDirectory().resolve(hash + ".jar"));
        Files.writeString(paths.gradleMavenDirectory().resolve("legacy-dependencies.json"),
                "{\"format\":1,\"artifacts\":[{\"sha256\":\"" + hash + "\",\"file\":\"" + hash + ".jar\"}]}");
        var result = new LegacyDiscovery().scan(paths);
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.archives().size());
        assertTrue(result.archives().get(0).classes().contains("fixture.Library"));
        try (var loader = new IsolatedLegacyClassLoader(new java.net.URL[]{Path.of(result.archives().get(0).file()).toUri().toURL()}, getClass().getClassLoader())) {
            assertSame(loader, loader.loadClass("fixture.Library").getClassLoader());
        }
        Files.createDirectories(paths.modDirectory());
        Path installed = Files.copy(artifact, paths.modDirectory().resolve("library.jar"));
        var duplicate = new LegacyRuntimeService().inspect(game);
        assertEquals(LegacyRuntimeService.State.RUNTIME_NOT_READY, duplicate.state());
        assertEquals(1, duplicate.archives().size());
        assertEquals(installed.toAbsolutePath().toString(), duplicate.archives().get(0).file());
        Files.writeString(artifact, "corrupt");
        var damaged = new LegacyRuntimeService().inspect(game);
        assertEquals(LegacyRuntimeService.State.DEPENDENCY_ERRORS, damaged.state());
        assertTrue(damaged.diagnostics().stream().anyMatch(d -> d.code().equals("INVALID_LEGACY_MAVEN")));
    }

    @Test void rejectsPathsOutsideManifestDirectory() throws Exception {
        var paths = new LegacyPaths(game);
        Files.createDirectories(paths.mavenDirectory());
        Files.writeString(paths.mavenDirectory().resolve("legacy-dependencies.json"),
                "{\"format\":1,\"artifacts\":[{\"sha256\":\"" + "0".repeat(64) + "\",\"file\":\"../outside.jar\"}]}");
        assertTrue(new LegacyDiscovery().scan(paths).diagnostics().stream().anyMatch(d -> d.code().equals("INVALID_LEGACY_MAVEN")));
    }
}
