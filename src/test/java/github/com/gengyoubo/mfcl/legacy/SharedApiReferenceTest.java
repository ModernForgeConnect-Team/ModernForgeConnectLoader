package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SharedApiReferenceTest {
    @TempDir Path temporary;
    @Test void originalBaublesThaumcraftAndExtraUtilitiesBootWithCanonicalApiEvenWhenOwnerIsLast()throws Exception{
        String support=System.getProperty("mfcl.test.legacySupportDir","");var inputs=new LinkedHashMap<String,String>();
        for(String key:List.of("extrautilitiesJar","thaumcraftJar","baublesJar")){String file=System.getProperty("mfcl.test."+key,"");assumeTrue(!file.isBlank()&&!support.isBlank(),"Supply original XU/TC4/Baubles and prepared runtime");inputs.put(key,file);}
        var mods=Files.createDirectories(temporary.resolve("mods/1.7.10"));Files.copy(Path.of(inputs.get("extrautilitiesJar")),mods.resolve("a-extrautilities.jar"));Files.copy(Path.of(inputs.get("thaumcraftJar")),mods.resolve("m-thaumcraft.jar"));Files.copy(Path.of(inputs.get("baublesJar")),mods.resolve("z-baubles.jar"));
        var report=new LegacyRuntimeService().inspect(temporary);assertFalse(report.diagnostics().stream().anyMatch(LegacyDiagnostic::error),report.toString());
        var log=temporary.resolve("shared-api.log");var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Dfile.encoding=UTF-8","-cp",System.getProperty("mfcl.test.runtimeClasspath"),SharedApiReferenceTest.class.getName(),temporary.toString(),Path.of(support).resolve("runtime").toString()).directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{assertTrue(process.waitFor(90,TimeUnit.SECONDS),"Combined original mod boot timed out");assertEquals(0,process.exitValue(),Files.readString(log));assertTrue(Files.readString(log).contains("MFCL_SHARED_API_PASS"));}finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}}
    }
    public static void main(String[] args)throws Exception{
        var game=Path.of(args[0]);var report=new LegacyRuntimeService().inspect(game);
        try(var runtime=new EmbeddedLegacyRuntime(game,Path.of(args[1]),report)){
            var boot=runtime.boot();if(!Boolean.TRUE.equals(boot.get("legacyFmlStarted")))throw new AssertionError(boot);
            var loader=runtime.services().loader();var api=loader.loadClass("baubles.api.BaublesApi");
            var source=source(api);if(!source.equals(game.resolve("mods/1.7.10/z-baubles.jar")))throw new AssertionError("Bundled API won: "+source);
            for(String name:List.of("baubles.api.IBauble","baubles.api.BaubleType"))if(!source(loader.loadClass(name)).equals(source))throw new AssertionError("Split Baubles API ownership");
            System.out.println("MFCL_SHARED_API_PASS: original Baubles + Thaumcraft + ExtraUtilities; canonical API owner last, archives unchanged");
        }
    }
    private static Path source(Class<?> type)throws Exception{
        var url=type.getProtectionDomain().getCodeSource().getLocation();if(url.getProtocol().equals("jar"))url=((java.net.JarURLConnection)url.openConnection()).getJarFileURL();return Path.of(url.toURI());
    }
}
