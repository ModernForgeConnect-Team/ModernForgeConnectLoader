package github.com.gengyoubo.mfcl.legacy.runtime;

import github.com.gengyoubo.mfcl.legacy.LegacyArchive;
import github.com.gengyoubo.mfcl.legacy.LegacyDiscovery;
import github.com.gengyoubo.mfcl.legacy.LegacyPaths;
import github.com.gengyoubo.mfcl.legacy.LegacyRuntimeService;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executes the original plugin handshake in an isolated diagnostic JVM, not a complete FML boot. */
public final class LegacyCoremodProbe {
    public record Result(String stage, String modId, String pluginClass, String containerClass,
                         List<String> transformerClasses, String setupClass, String codeSource,
                         boolean legacyFmlStarted, boolean legacyClientReady, boolean systemClassLoaderUnchanged) {}

    private Result probe(Path gameDirectory, Path supportDirectory, String modId) throws Exception {
        var report = new LegacyRuntimeService().inspect(gameDirectory);
        if (report.diagnostics().stream().anyMatch(d -> d.error())) throw new IllegalStateException("Resolve discovery errors first: " + report.diagnostics());
        LegacyArchive selected = report.archives().stream().filter(a -> a.mods().stream().anyMatch(m -> m.id().equals(modId)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Legacy mod not found: " + modId));
        if (selected.corePlugin().isEmpty()) throw new IllegalArgumentException(modId + " does not declare FMLCorePlugin");
        List<URL> urls = new ArrayList<>();
        try (var files = Files.list(supportDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                urls.add(file.toUri().toURL());
            }
        }
        for (LegacyArchive archive : report.archives()) {
            Path path = Path.of(archive.file());
            if (!LegacyDiscovery.sha256(path).equals(archive.sha256())) throw new IllegalStateException("Archive changed since inspection: " + path);
            urls.add(path.toUri().toURL());
        }
        ClassLoader systemBefore = ClassLoader.getSystemClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader contextBefore = thread.getContextClassLoader();
        URL[] inputs = urls.toArray(URL[]::new);
        try (var tools = new IsolatedLegacyClassLoader(inputs, getClass().getClassLoader())) {
            Class<?> launchLoaderType = tools.loadClass("net.minecraft.launchwrapper.LaunchClassLoader");
            try (URLClassLoader legacy = (URLClassLoader) launchLoaderType.getConstructor(URL[].class).newInstance((Object) inputs)) {
                launchLoaderType.getMethod("addClassLoaderExclusion", String.class).invoke(legacy, "cpw.mods.fml.relauncher.");
                Class<?> launch = tools.loadClass("net.minecraft.launchwrapper.Launch");
                launch.getField("classLoader").set(null, legacy);
                launch.getField("blackboard").set(null, new HashMap<String, Object>());
                launch.getField("minecraftHome").set(null, new LegacyPaths(gameDirectory).gameDirectory().toFile());
                thread.setContextClassLoader(legacy);
                Class<?> injectionData = legacy.loadClass("cpw.mods.fml.relauncher.FMLInjectionData");
                Method build = injectionData.getDeclaredMethod("build", File.class, launchLoaderType);
                build.setAccessible(true);
                build.invoke(null, gameDirectory.toAbsolutePath().toFile(), legacy);
                Object[] values = (Object[]) injectionData.getMethod("data").invoke(null);
                if (!"1.7.10".equals(values[4])) throw new IllegalStateException("Wrong legacy Forge runtime: " + values[4]);
                Class<?> side = legacy.loadClass("cpw.mods.fml.relauncher.Side");
                Class<?> handler = legacy.loadClass("cpw.mods.fml.relauncher.FMLLaunchHandler");
                Field sideField = handler.getDeclaredField("side");
                sideField.setAccessible(true);
                sideField.set(null, side.getField("CLIENT").get(null));

                Class<?> pluginType = legacy.loadClass(selected.corePlugin());
                Object plugin = pluginType.getDeclaredConstructor().newInstance();
                Map<String, Object> data = new HashMap<>();
                data.put("coremodLocation", Path.of(selected.file()).toFile());
                data.put("mcLocation", gameDirectory.toAbsolutePath().toFile());
                data.put("runtimeDeobfuscationEnabled", Boolean.TRUE);
                data.put("classLoader", legacy);
                pluginType.getMethod("injectData", Map.class).invoke(plugin, data);
                String[] transformers = (String[]) pluginType.getMethod("getASMTransformerClass").invoke(plugin);
                String container = (String) pluginType.getMethod("getModContainerClass").invoke(plugin);
                String setup = (String) pluginType.getMethod("getSetupClass").invoke(plugin);
                if (setup != null && !setup.isBlank()) {
                    Class<?> setupType = legacy.loadClass(setup);
                    Object setupInstance = setupType.equals(pluginType) ? plugin : setupType.getDeclaredConstructor().newInstance();
                    setupType.getMethod("injectData", Map.class).invoke(setupInstance, data);
                    setupType.getMethod("call").invoke(setupInstance);
                }
                if (!LegacyDiscovery.sha256(Path.of(selected.file())).equals(selected.sha256())) throw new IllegalStateException("Original mod archive was modified");
                return new Result("CORE_PLUGIN_HANDSHAKE", modId, selected.corePlugin(), container,
                        transformers == null ? List.of() : List.of(transformers), setup,
                        pluginType.getProtectionDomain().getCodeSource().getLocation().toString(),
                        false, false, systemBefore == ClassLoader.getSystemClassLoader());
            }
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw wrapper;
        } finally { thread.setContextClassLoader(contextBefore); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: LegacyCoremodProbe <game-dir> <runtime-support-dir> <legacy-mod-id>");
        var result = new LegacyCoremodProbe().probe(Path.of(args[0]), Path.of(args[1]), args[2]);
        System.out.println("MFCL_COREMOD_RESULT=" + new com.google.gson.Gson().toJson(result));
    }
}
