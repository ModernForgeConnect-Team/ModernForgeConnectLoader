package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GenericRuntimeIntegrationTest {
    @TempDir Path temporary;
    @Test void itemWorldAndGuiSemanticsWorkWithoutNeiOrCodeChickenJars() throws Exception {
        String support = System.getProperty("mfcl.test.legacySupportDir", ""); assumeTrue(!support.isBlank(), "Supply prepared legacy runtime inputs");
        Path output = temporary.resolve("generic.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("mfcl.test.runtimeClasspath"),
                GenericSemanticsProbe.class.getName(), temporary.toString(), Path.of(support).resolve("runtime").toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Generic legacy runtime timed out");
            assertEquals(0, process.exitValue(), new String(Files.readAllBytes(output), java.nio.charset.StandardCharsets.UTF_8));
            var result = com.google.gson.JsonParser.parseString(Files.readString(temporary.resolve("logs/mfcl/runtime-1.7.10.json"))).getAsJsonObject();
            assertTrue(result.get("genericItemWorldGuiSemanticsVerified").getAsBoolean());
            assertTrue(result.get("genericDeveloperApiVerified").getAsBoolean());
            assertTrue(result.get("genericGuiCaptureVerified").getAsBoolean());
            assertTrue(result.get("genericContainerPlayerPotionStateVerified").getAsBoolean());
            assertTrue(result.get("genericItemUseCallbackVerified").getAsBoolean());
            assertTrue(result.get("genericWorldRayTraceVerified").getAsBoolean());
            assertTrue(result.get("genericPlayerDataBridgeVerified").getAsBoolean());
            assertTrue(result.get("genericOriginalGameplayCallbacksVerified").getAsBoolean());
            assertEquals(0, result.getAsJsonObject("adapters").size());
            assertFalse(result.getAsJsonArray("loadedMods").asList().stream().anyMatch(v -> v.getAsString().equals("NotEnoughItems")));
        } finally { if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); } }
    }
    @Test void coreSourcesDoNotDependOnOptionalModAdapters() throws Exception {
        for (String directory : new String[]{"bridge", "client", "legacy/runtime"}) {
            try (var files = Files.walk(Path.of("src/main/java/github/com/gengyoubo/mfcl", directory))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("compat.nei") || source.contains("codechicken.") || source.contains("NeiDataBridge") || source.contains("NeiBackend"), file.toString());
                }
            }
        }
    }
}
