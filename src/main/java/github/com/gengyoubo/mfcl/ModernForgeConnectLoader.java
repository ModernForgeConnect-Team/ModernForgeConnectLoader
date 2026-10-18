package github.com.gengyoubo.mfcl;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.legacy.LegacyRuntimeService;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(ModernForgeConnectLoader.MOD_ID)
public final class ModernForgeConnectLoader {
    public static final String MOD_ID = "mfcl";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ModernForgeConnectLoader() {
        var result = new LegacyRuntimeService().inspect(FMLPaths.GAMEDIR.get());
        LOGGER.info("MFCL: reading Forge 1.7.10 mods from {}", result.modDirectory());
        LOGGER.info("MFCL: {} legacy archives; state {}; report {}",
                result.archives().size(), result.state(), result.reportFile());
        for (var diagnostic : result.diagnostics()) {
            if (diagnostic.error()) LOGGER.error("MFCL [{}] {}", diagnostic.code(), diagnostic.message());
            else LOGGER.warn("MFCL [{}] {}", diagnostic.code(), diagnostic.message());
        }
    }
}
