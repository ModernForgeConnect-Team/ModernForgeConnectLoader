package github.com.gengyoubo.mfcl.bridge;

import java.nio.file.*;
import java.util.*;

/** Coarse startup timings, reported once; values are elapsed wall time, not CPU time. */
public final class LegacyStartupMetrics {
    private static final long START=System.nanoTime();
    private static final Map<String,Double> PHASES=new LinkedHashMap<>();
    private static final Map<String,Object> DETAILS=new LinkedHashMap<>();
    private LegacyStartupMetrics() {}
    public static long now(){return System.nanoTime();}
    public static synchronized void elapsed(String name,long start){PHASES.put(name,(System.nanoTime()-start)/1_000_000.0);}
    public static synchronized void detail(String name,Object value){DETAILS.put(name,value);}
    public static synchronized void write(Path game,String end) {
        var report=new LinkedHashMap<String,Object>();report.put("endpoint",end);
        report.put("mfclEntryToReportMs",(System.nanoTime()-START)/1_000_000.0);
        report.put("jvmUptimeMs",java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        report.put("phaseWallMs",new LinkedHashMap<>(PHASES));report.put("details",new LinkedHashMap<>(DETAILS));
        try {Path file=game.resolve("logs/mfcl/startup-timings.json");Files.createDirectories(file.getParent());
            Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            com.mojang.logging.LogUtils.getLogger().info("MFCL startup timings: {}",report);
        } catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("MFCL could not write startup timings",failure);}
    }
}
