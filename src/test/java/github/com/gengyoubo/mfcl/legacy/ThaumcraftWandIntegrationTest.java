package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ThaumcraftWandIntegrationTest {
    @TempDir Path temporary;
    @Test void originalWandsCraftChargeAndTransferFociWithoutLosingState() throws Exception {
        String support=System.getProperty("mfcl.test.legacySupportDir","");
        String tc=System.getProperty("mfcl.test.thaumcraftJar",""),baubles=System.getProperty("mfcl.test.baublesJar","");
        assumeTrue(!support.isBlank()&&!tc.isBlank()&&!baubles.isBlank(),"Supply prepared runtime and original TC4/Baubles JARs");
        Path mods=Files.createDirectories(temporary.resolve("mods/1.7.10"));
        Files.copy(Path.of(tc),mods.resolve("Thaumcraft.jar"));Files.copy(Path.of(baubles),mods.resolve("Baubles.jar"));
        Path log=temporary.resolve("wand.log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Dfile.encoding=UTF-8","-cp",System.getProperty("mfcl.test.runtimeClasspath"),
                ThaumcraftWandAuditProbe.class.getName(),temporary.toString(),Path.of(support).resolve("runtime").toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(90,TimeUnit.SECONDS),"Original wand integration timed out");
            assertEquals(0,process.exitValue(),Files.readString(log));
            var report=JsonParser.parseString(Files.readString(temporary.resolve("logs/mfcl/wand-functions-audit.json"))).getAsJsonObject();
            for(String name:new String[]{"basicIronWoodWandAssembly","advancedGoldGreatwoodWandAssembly","arcaneRejectsMissingWand","arcaneRejectsEmptyWand","arcaneResearchGate",
                    "arcaneCraftConsumesIngredientsAndVis","nodeChargingHeldUseBridge","focusChangePacketBridge","passiveRodInventoryTick"})
                assertEquals("PASS",report.getAsJsonObject(name).get("status").getAsString(),name+": "+report);
            var node=report.getAsJsonObject("nodeChargingHeldUseBridge");
            assertEquals(2000,node.get("nodeAirAfter").getAsInt()*100+node.getAsJsonObject("heldWandRawVis").get("aer").getAsInt());
            assertTrue(report.getAsJsonObject("focusChangePacketBridge").get("equippedAndReturnedExactlyOnce").getAsBoolean());
            assertTrue(report.getAsJsonObject("focusChangePacketBridge").get("unknownFocusRejected").getAsBoolean());
        } finally {if(process.isAlive()){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}}
    }
}
