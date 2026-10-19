package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ThaumcraftResearchIntegrationTest {
    @TempDir Path temporary;
    @Test void originalNotesCanBeInsertedEditedAndCompleted() throws Exception {
        String support=System.getProperty("mfcl.test.legacySupportDir","");
        String tc=System.getProperty("mfcl.test.thaumcraftJar","");
        String baubles=System.getProperty("mfcl.test.baublesJar","");
        assumeTrue(!support.isBlank()&&!tc.isBlank()&&!baubles.isBlank(),"Supply prepared runtime and original TC4/Baubles JARs");
        Path mods=Files.createDirectories(temporary.resolve("mods/1.7.10"));
        Files.copy(Path.of(tc),mods.resolve("Thaumcraft.jar"));Files.copy(Path.of(baubles),mods.resolve("Baubles.jar"));
        Path log=temporary.resolve("research.log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Dfile.encoding=UTF-8","-cp",System.getProperty("mfcl.test.runtimeClasspath"),
                ResearchTableSemanticsProbe.class.getName(),temporary.toString(),Path.of(support).resolve("runtime").toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60,TimeUnit.SECONDS),"TC4 research probe timed out");
            String output=new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);
            assertTrue(output.contains("MFCL_RESEARCH_TABLE_PASS"),output);
            assertTrue(output.contains("MFCL_TC_BIOMES_PASS"),output);
        } finally {if(process.isAlive()){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}}
    }
}
