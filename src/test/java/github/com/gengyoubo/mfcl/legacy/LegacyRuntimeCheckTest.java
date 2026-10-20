package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRuntimeCheckTest {
    @TempDir Path game;
    private static final List<LegacyArchive> MODS = List.of(new LegacyArchive("mods/1.7.10/Thaumcraft.jar", "", List.of(),
            "", "", List.of(), Set.of(), List.of(), List.of()));

    private Path runtime() { return game.resolve("mfcl/runtime/1.7.10"); }

    private void manifest(String minecraft, String forge, String inputs) throws Exception {
        Files.createDirectories(runtime());
        Files.writeString(runtime().resolve("runtime-manifest.json"),
                "{\"minecraft\":\"" + minecraft + "\",\"forge\":\"" + forge + "\",\"inputs\":[" + inputs + "]}");
    }

    private String entry(String name, String content) throws Exception {
        Path file = Files.writeString(runtime().resolve(name), content);
        return "{\"file\":\"" + name + "\",\"sha256\":\"" + LegacyDiscovery.sha256(file) + "\"}";
    }

    private static List<String> codes(List<LegacyDiagnostic> diagnostics) { return diagnostics.stream().map(LegacyDiagnostic::code).toList(); }

    @Test void missingRuntimeIsAnErrorWithTheFixCommandWhenModsNeedIt() {
        var result = LegacyRuntimeCheck.run(game, runtime(), MODS);
        assertEquals(List.of("RUNTIME_CHECK_FAILED", "RUNTIME_DIRECTORY_MISSING"), codes(result));
        assertTrue(result.get(0).error());
        assertTrue(result.get(0).message().contains("prepare-legacy-runtime.ps1 -GameDirectory '" + game.toAbsolutePath().normalize() + "' -Boot"));
        assertTrue(result.get(0).message().contains("Thaumcraft.jar"));
    }

    @Test void sameProblemsAreOnlyNoticesWithoutLegacyMods() {
        var result = LegacyRuntimeCheck.run(game, runtime(), List.of());
        assertEquals(List.of("RUNTIME_DIRECTORY_MISSING"), codes(result));
        assertFalse(result.get(0).error());
    }

    @Test void validManifestPasses() throws Exception {
        manifest("1.7.10", "10.13.4.1614", "");
        manifest("1.7.10", "10.13.4.1614", entry(LegacyRuntimeCheck.SRG_JAR, "srg") + "," + entry("lib.jar", "lib"));
        assertEquals(List.of(), LegacyRuntimeCheck.run(game, runtime(), MODS));
    }

    @Test void reportsEachMissingFileChecksumAndVersionSeparately() throws Exception {
        manifest("1.7.10", "10.13.4.1614", "");
        String srg = entry(LegacyRuntimeCheck.SRG_JAR, "srg"), changed = entry("changed.jar", "original");
        Files.writeString(runtime().resolve("changed.jar"), "modified");
        manifest("1.6.4", "9.11.1", srg + "," + changed + ",{\"file\":\"gone.jar\",\"sha256\":\"00\"}");
        var result = LegacyRuntimeCheck.run(game, runtime(), MODS);
        assertEquals(List.of("RUNTIME_CHECK_FAILED", "RUNTIME_VERSION_MISMATCH", "RUNTIME_CHECKSUM_MISMATCH", "RUNTIME_INPUT_MISSING"), codes(result));
        assertTrue(result.stream().allMatch(LegacyDiagnostic::error));
        assertTrue(result.get(2).message().contains("changed.jar"));
        assertTrue(result.get(3).message().contains("gone.jar"));
    }

    @Test void manifestWithoutSrgJarOrManifestItselfIsReported() throws Exception {
        manifest("1.7.10", "10.13.4.1614", "");
        manifest("1.7.10", "10.13.4.1614", entry("lib.jar", "lib"));
        assertEquals(List.of("RUNTIME_CHECK_FAILED", "RUNTIME_INPUT_MISSING"), codes(LegacyRuntimeCheck.run(game, runtime(), MODS)));
        Files.delete(runtime().resolve("runtime-manifest.json"));
        Files.writeString(runtime().resolve(LegacyRuntimeCheck.SRG_JAR), "srg");
        var unverified = LegacyRuntimeCheck.run(game, runtime(), MODS);
        assertEquals(List.of("RUNTIME_MANIFEST_MISSING"), codes(unverified));
        assertFalse(unverified.get(0).error());
    }
}
