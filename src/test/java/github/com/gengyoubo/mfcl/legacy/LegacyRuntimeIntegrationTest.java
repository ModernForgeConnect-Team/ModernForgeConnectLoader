package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LegacyRuntimeIntegrationTest {
    @TempDir Path temporary;

    @Test void realFmlRunsNeiTransformersAndOriginalHandlersConsumeProjectedRecipes() throws Exception {
        String nei = System.getProperty("mfcl.test.neiJar", ""), support = System.getProperty("mfcl.test.legacySupportDir", "");
        assumeTrue(!nei.isBlank() && !support.isBlank(), "Supply original NEI and prepared runtime inputs");
        Path runtime = Path.of(support).resolve("runtime");
        assertTrue(Files.isRegularFile(runtime.resolve("minecraft-forge-1.7.10-srg.jar")), "Run tools/prepare-legacy-runtime.ps1 first");
        Path mods = new LegacyPaths(temporary).modDirectory(); Files.createDirectories(mods);
        Files.copy(Path.of(nei), mods.resolve(Path.of(nei).getFileName()));
        for (String file : List.of("CodeChickenCore-1.7.10-1.0.7.47-universal.jar", "CodeChickenLib-1.7.10-1.1.3.138-universal.jar"))
            Files.copy(Path.of(support).resolve(file), mods.resolve(file));
        Path output = temporary.resolve("boot.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("mfcl.test.runtimeClasspath"),
                "github.com.gengyoubo.mfcl.legacy.NeiSemanticsProbe", temporary.toString(), runtime.toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Legacy FML boot timed out");
            assertEquals(0, process.exitValue(), new String(Files.readAllBytes(output), java.nio.charset.StandardCharsets.UTF_8));
            var report = JsonParser.parseString(Files.readString(temporary.resolve("logs/mfcl/runtime-1.7.10.json"))).getAsJsonObject();
            assertEquals("AVAILABLE", report.get("stage").getAsString());
            assertTrue(report.get("legacyFmlStarted").getAsBoolean());
            assertEquals("READY", report.getAsJsonObject("adapters").getAsJsonObject("nei").get("status").getAsString());
            assertTrue(report.get("genericItemWorldGuiSemanticsVerified").getAsBoolean());
            assertTrue(report.get("originalGuiCaptureVerified").getAsBoolean());
            assertTrue(report.get("registrationSealed").getAsBoolean());
            assertTrue(report.get("lateRegistrationRejected").getAsBoolean());
            assertTrue(report.get("modernClassIsolationVerified").getAsBoolean());
            assertTrue(report.get("recipeProjectionVerified").getAsBoolean());
            assertTrue(report.get("hostItemIdentityVerified").getAsBoolean());
            assertTrue(report.getAsJsonObject("adapters").getAsJsonObject("nei").getAsJsonObject("data").get("catalogSize").getAsInt() > 500);
            assertTrue(report.getAsJsonArray("loadedMods").asList().stream().anyMatch(id -> id.getAsString().equals("NotEnoughItems")));
            var transformed = report.getAsJsonArray("transformations").asList().stream().map(v -> v.getAsString()).toList();
            assertTrue(transformed.contains("codechicken.nei.asm.NEITransformer -> net.minecraft.client.gui.inventory.GuiContainer"));
            assertTrue(transformed.contains("cpw.mods.fml.common.asm.transformers.ModAccessTransformer -> net.minecraft.client.gui.inventory.GuiContainer"));
            assertEquals(LegacyDiscovery.sha256(Path.of(nei)), LegacyDiscovery.sha256(mods.resolve(Path.of(nei).getFileName())));
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); }
        }
    }
}
