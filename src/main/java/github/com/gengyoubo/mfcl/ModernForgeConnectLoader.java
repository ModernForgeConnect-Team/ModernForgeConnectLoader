package github.com.gengyoubo.mfcl;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.legacy.LegacyRuntimeService;
import github.com.gengyoubo.mfcl.client.HostSmokeProbe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(ModernForgeConnectLoader.MOD_ID)
public final class ModernForgeConnectLoader {
    public static final String MOD_ID = "mfcl";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LegacyRuntimeService runtimeService = new LegacyRuntimeService();

    public ModernForgeConnectLoader() {
        var gameDirectory = FMLPaths.GAMEDIR.get();
        var supportDirectory = java.nio.file.Path.of(System.getProperty("mfcl.legacyRuntimeDir", gameDirectory.resolve("mfcl/runtime/1.7.10").toString()));
        var result = runtimeService.start(gameDirectory, supportDirectory);
        LOGGER.info("MFCL: reading Forge 1.7.10 mods from {}", result.modDirectory());
        LOGGER.info("MFCL: {} legacy archives; state {}; report {}",
                result.archives().size(), result.state(), result.reportFile());
        for (var diagnostic : result.diagnostics()) {
            if (diagnostic.error()) LOGGER.error("MFCL [{}] {}", diagnostic.code(), diagnostic.message());
            else LOGGER.warn("MFCL [{}] {}", diagnostic.code(), diagnostic.message());
        }
        if (runtimeService.runtime() != null) {
            runtimeService.runtime().nei().ifPresent(bridge -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> github.com.gengyoubo.mfcl.client.NeiClientUi.install(bridge)));
        }
        if (Boolean.getBoolean("mfcl.test.exitAtTitleScreen")) {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> HostSmokeProbe::install);
        }
    }
}
