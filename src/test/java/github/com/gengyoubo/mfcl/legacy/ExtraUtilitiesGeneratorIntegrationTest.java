package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExtraUtilitiesGeneratorIntegrationTest {
    @TempDir Path temporary;
    @Test void originalGeneratorTicksAndSynchronizesItsDynamicContainer() throws Exception {
        String mod=System.getProperty("mfcl.test.extrautilitiesJar",""),support=System.getProperty("mfcl.test.legacySupportDir","");
        assumeTrue(!mod.isBlank()&&!support.isBlank(),"Supply original Extra Utilities and prepared runtime inputs");
        Path mods=temporary.resolve("mods/1.7.10");Files.createDirectories(mods);
        Files.copy(Path.of(mod),mods.resolve("extrautilities-1.2.12.jar"));
        Path output=temporary.resolve("generator.log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-XX:-OmitStackTraceInFastThrow","-cp",System.getProperty("mfcl.test.runtimeClasspath"),
                ExtraUtilitiesGeneratorProbe.class.getName(),temporary.toString(),Path.of(support).resolve("runtime").toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60,TimeUnit.SECONDS),"Generator probe timed out");
            String log=new String(Files.readAllBytes(output),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),log);
            assertTrue(log.contains("MFCL_XU_GENERATOR_PASS"));
        } finally {if(process.isAlive()){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}}
    }
}
