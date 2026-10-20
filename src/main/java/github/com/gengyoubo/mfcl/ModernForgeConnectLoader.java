package github.com.gengyoubo.mfcl;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.legacy.LegacyRuntimeService;

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
        var backend = new java.util.concurrent.atomic.AtomicReference<github.com.gengyoubo.mfcl.bridge.LegacyOpenGl.Backend>();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> backend.set(new github.com.gengyoubo.mfcl.client.ClientOpenGlBackend()));
        var result = runtimeService.start(gameDirectory, supportDirectory, backend.get());
        LOGGER.info("MFCL: reading Forge 1.7.10 mods from {}", result.modDirectory());
        LOGGER.info("MFCL: {} legacy archives; state {}; report {}",
                result.archives().size(), result.state(), result.reportFile());
        for (var diagnostic : result.diagnostics()) {
            if (diagnostic.error()) LOGGER.error("MFCL [{}] {}", diagnostic.code(), diagnostic.message());
            else LOGGER.warn("MFCL [{}] {}", diagnostic.code(), diagnostic.message());
        }
        var catalog = github.com.gengyoubo.mfcl.legacy.LegacyModCatalog.from(result, runtimeService.loadedModIds());
        var loadedMods = catalog.loadedMods(runtimeService.loadedModVersions());
        var dependencyErrors = new java.util.ArrayList<String>();
        for (var file : net.minecraftforge.fml.ModList.get().getModFiles()) {
            var owners = file.getMods().stream().map(net.minecraftforge.forgespi.language.IModInfo::getModId)
                    .collect(java.util.stream.Collectors.toSet());
            dependencyErrors.addAll(github.com.gengyoubo.mfcl.legacy.LegacyAddonDependencies.validateFile(
                    owners, file.getConfig(), loadedMods, net.minecraftforge.fml.loading.FMLEnvironment.dist));
        }
        if (!dependencyErrors.isEmpty()) throw new IllegalStateException("MFCL legacy dependency checks failed:\n"
                + String.join("\n", dependencyErrors));
        github.com.gengyoubo.mfcl.internal.api.ApiRuntimeProvider.installMods(loadedMods);
        github.com.gengyoubo.mfcl.legacy.RuntimeModAccounting.install(catalog);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(github.com.gengyoubo.mfcl.internal.api.ApiDebugCommand::register);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> github.com.gengyoubo.mfcl.client.LegacyModBranding.install(catalog));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> github.com.gengyoubo.mfcl.client.LegacyModListScreen.install(catalog));
        if (result.legacyFmlStarted() && runtimeService.runtime() != null) {
            github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.attach(runtimeService.runtime().services().loader());
            github.com.gengyoubo.mfcl.internal.api.ApiRuntimeProvider.installResources(runtimeService.runtime().services().loader());
            @SuppressWarnings("removal")
            var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
            var content = github.com.gengyoubo.mfcl.content.ModernContentBridge.install(runtimeService.runtime().services(), bus);
            var serverSmoke = System.getProperty("mfcl.test.server");
            if (serverSmoke != null) github.com.gengyoubo.mfcl.content.ServerHostSmokeProbe.install(content, serverSmoke);
            var developerApi = runtimeService.runtime().developerApi(runtimeService.loadedModIds());
            github.com.gengyoubo.mfcl.internal.api.ApiRuntimeFactory.installHostMappings(developerApi, content);
            bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(
                    () -> github.com.gengyoubo.mfcl.internal.api.ApiRuntimeProvider.publish(developerApi)));
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> github.com.gengyoubo.mfcl.client.ClientContentBridge.install(content, bus));
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> github.com.gengyoubo.mfcl.client.ClientWorldBridge.install(runtimeService.runtime().services()));
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> github.com.gengyoubo.mfcl.client.ClientContainerBridge.install(runtimeService.runtime().services()));
        }
        if (result.state() == LegacyRuntimeService.State.CLIENT_DATA_AVAILABLE && runtimeService.runtime() != null) {
            for (var adapter : runtimeService.runtime().adapters()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> adapter::installClient);
        }

    }
}
