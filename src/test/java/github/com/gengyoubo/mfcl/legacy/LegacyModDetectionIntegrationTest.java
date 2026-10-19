package github.com.gengyoubo.mfcl.legacy;

import com.electronwill.nightconfig.toml.TomlParser;
import github.com.gengyoubo.mfcl.api.MfclApi;
import github.com.gengyoubo.mfcl.internal.api.ApiRuntimeProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.moddiscovery.NightConfigWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LegacyModDetectionIntegrationTest {
    @TempDir Path temporary;

    @Test void detectsActualTc4AndValidatesPublishedTomlExample() throws Exception {
        String support = System.getProperty("mfcl.test.legacySupportDir", "");
        String tc = System.getProperty("mfcl.test.thaumcraftJar", "");
        String baubles = System.getProperty("mfcl.test.baublesJar", "");
        assumeTrue(!support.isBlank() && !tc.isBlank() && !baubles.isBlank(), "Supply original TC4/Baubles and prepared runtime");
        Path mods = Files.createDirectories(temporary.resolve("mods/1.7.10"));
        Files.copy(Path.of(tc), mods.resolve("Thaumcraft.jar"));
        Files.copy(Path.of(baubles), mods.resolve("Baubles.jar"));
        Path log = temporary.resolve("detection.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("mfcl.test.runtimeClasspath"),
                LegacyModDetectionIntegrationTest.class.getName(), temporary.toString(),
                Path.of(support).resolve("runtime").toString(),
                Path.of("examples/legacy-accessor/thaumcraft.mods.toml.example").toAbsolutePath().toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Legacy mod detection timed out");
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("MFCL_LEGACY_DEPENDENCIES_PASS"), output);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); }
        }
    }

    public static void main(String[] args) throws Exception {
        var service = new LegacyRuntimeService();
        var report = service.start(Path.of(args[0]), Path.of(args[1]));
        try (var runtime = service.runtime()) {
            assertTrue(report.legacyFmlStarted(), report.toString());
            assertEquals("4.2.3.5", service.loadedModVersions().get("Thaumcraft"));
            var loaded = LegacyModCatalog.from(report, service.loadedModIds()).loadedMods(service.loadedModVersions());
            ApiRuntimeProvider.installMods(loaded);
            assertTrue(MfclApi.runtime().isEmpty());
            assertTrue(MfclApi.isModLoaded("Thaumcraft"));
            assertFalse(MfclApi.isModLoaded("thaumcraft"));
            assertEquals("1.7.10", MfclApi.mod("Thaumcraft").orElseThrow().sourceMinecraft());
            assertTrue(MfclApi.isModLoaded("Baubles"));
            assertFalse(MfclApi.isModLoaded("Forge"));
            var config = new NightConfigWrapper(new TomlParser().parse(Files.readString(Path.of(args[2]))));
            assertTrue(LegacyAddonDependencies.validateFile(Set.of("exampleaddon"), config, loaded, Dist.CLIENT).isEmpty());
            assertFalse(LegacyAddonDependencies.validateFile(Set.of("exampleaddon"), config, java.util.Map.of(), Dist.CLIENT).isEmpty());
            System.out.println("MFCL_LEGACY_DEPENDENCIES_PASS");
        }
    }
}
