package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NeiReferenceTest {
    @TempDir Path temporary;

    private Path referenceJar() {
        String configured = System.getProperty("mfcl.test.neiJar", "");
        assumeTrue(!configured.isBlank(), "Supply -PneiJar=<original NEI JAR> to run the real reference checks");
        Path jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar), "Configured reference JAR is missing");
        return jar;
    }

    @Test void discoversTheExactUnmodifiedNeiCoremodAndItsNonstandardMetadata() throws Exception {
        Path original = referenceJar();
        String hash = LegacyDiscovery.sha256(original);
        assertEquals("3ebbc2f82b61812aa158375005a47da4d450bec870860fcbf015a64de74cde1c", hash);
        var archive = new LegacyDiscovery().inspect(original);
        assertEquals("codechicken.nei.asm.NEICorePlugin", archive.corePlugin());
        assertEquals("codechicken.nei.NEIModContainer", archive.coreContainer());
        assertEquals(List.of("META-INF/nei_at.cfg"), archive.accessTransformers());
        assertEquals("NotEnoughItems", archive.mods().get(0).id());
        assertEquals("1.0.5.120", archive.mods().get(0).version());
        assertEquals("neimod.info", archive.mods().get(0).metadataResource());
        assertTrue(archive.requiredMods().contains("CodeChickenCore@[1.0.7,)"));
        assertTrue(archive.mods().get(0).entryClass().isEmpty());
        assertEquals(hash, LegacyDiscovery.sha256(original));
    }

    @Test void reportsMissingDependenciesFromTheProductionVersionFolder() throws Exception {
        Path original = referenceJar();
        var paths = new LegacyPaths(temporary);
        Files.createDirectories(paths.modDirectory());
        Files.copy(original, paths.modDirectory().resolve(original.getFileName()));
        var report = new LegacyRuntimeService().inspect(temporary);
        assertEquals(LegacyRuntimeService.State.DEPENDENCY_ERRORS, report.state());
        assertTrue(report.diagnostics().stream().anyMatch(d -> d.code().equals("MISSING_MOD_DEPENDENCY") && d.message().contains("CodeChickenCore")));
        assertFalse(report.legacyFmlStarted());
        assertFalse(report.legacyClientReady());
    }

    @Test void executesTheOriginalNeiPluginHandshakeInAnIsolatedJava17Runtime() throws Exception {
        Path original = referenceJar();
        String support = System.getProperty("mfcl.test.legacySupportDir", "");
        assumeTrue(!support.isBlank(), "Supply -PlegacySupportDir=<probe runtime inputs> to run the isolated handshake");
        Path sourceDirectory = Path.of(support);
        var paths = new LegacyPaths(temporary);
        Files.createDirectories(paths.modDirectory());
        Files.copy(original, paths.modDirectory().resolve(original.getFileName()));
        for (String dependency : List.of("CodeChickenCore-1.7.10-1.0.7.47-universal.jar", "CodeChickenLib-1.7.10-1.1.3.138-universal.jar")) {
            Files.copy(sourceDirectory.resolve(dependency), paths.modDirectory().resolve(dependency));
        }
        Path output = temporary.resolve("probe-output.txt");
        String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(executable, "-cp", System.getProperty("mfcl.test.runtimeClasspath"),
                "github.com.gengyoubo.mfcl.legacy.runtime.LegacyCoremodProbe", temporary.toString(),
                sourceDirectory.resolve("runtime").toString(), "NotEnoughItems")
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Core-plugin probe timed out");
            String log = Files.readString(output);
            assertEquals(0, process.exitValue(), log);
            String encoded = log.lines().filter(line -> line.startsWith("MFCL_COREMOD_RESULT=")).findFirst().orElseThrow(() -> new AssertionError(log));
            var result = JsonParser.parseString(encoded.substring("MFCL_COREMOD_RESULT=".length())).getAsJsonObject();
            assertEquals("CORE_PLUGIN_HANDSHAKE", result.get("stage").getAsString());
            assertEquals("codechicken.nei.NEIModContainer", result.get("containerClass").getAsString());
            assertEquals("codechicken.nei.asm.NEITransformer", result.getAsJsonArray("transformerClasses").get(0).getAsString());
            assertTrue(result.get("systemClassLoaderUnchanged").getAsBoolean());
            assertFalse(result.get("legacyClientReady").getAsBoolean());
            assertFalse(result.get("legacyFmlStarted").getAsBoolean());
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); }
        }
        assertEquals(LegacyDiscovery.sha256(original), LegacyDiscovery.sha256(paths.modDirectory().resolve(original.getFileName())));
    }
}
