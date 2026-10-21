package github.com.gengyoubo.mfcl.legacy.runtime;

import github.com.gengyoubo.mfcl.legacy.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Owns the two legacy classloader layers for the lifetime of a host instance. */
public final class EmbeddedLegacyRuntime implements AutoCloseable {
    private final IsolatedLegacyClassLoader root;
    private final URLClassLoader legacy;
    private final Path gameDirectory, forge;
    private final List<File> mods;
    private final List<Path> resourcePacks;
    private final Map<Path, String> originalHashes = new LinkedHashMap<>();
    private github.com.gengyoubo.mfcl.bridge.LegacyServices services;
    private github.com.gengyoubo.mfcl.bridge.LegacyClientData earlyClient;
    private github.com.gengyoubo.mfcl.bridge.LegacyClientData.ClientSession clientSession = github.com.gengyoubo.mfcl.bridge.LegacyClientData.ClientSession.offline();
    public void clientSession(github.com.gengyoubo.mfcl.bridge.LegacyClientData.ClientSession session) {
        this.clientSession = Objects.requireNonNull(session);
    }
    private github.com.gengyoubo.mfcl.internal.api.LegacyApiRuntime developerApi;
    public synchronized github.com.gengyoubo.mfcl.internal.api.LegacyApiRuntime developerApi(Set<String> loadedMods) {
        if (developerApi == null) developerApi = github.com.gengyoubo.mfcl.internal.api.ApiRuntimeFactory.create(services(), loadedMods);
        return developerApi;
    }
    private github.com.gengyoubo.mfcl.bridge.LegacyOpenGl.Backend openGlBackend;
    public void openGlBackend(github.com.gengyoubo.mfcl.bridge.LegacyOpenGl.Backend backend) { this.openGlBackend = backend; }
    private final List<github.com.gengyoubo.mfcl.bridge.LegacyModAdapter> adapters = new ArrayList<>();
    public github.com.gengyoubo.mfcl.bridge.LegacyServices services() { return Objects.requireNonNull(services, "Runtime has not booted"); }
    public List<github.com.gengyoubo.mfcl.bridge.LegacyModAdapter> adapters() { return List.copyOf(adapters); }

    public EmbeddedLegacyRuntime(Path gameDirectory, Path supportDirectory, LegacyRuntimeService.Report report) throws Exception {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        if (report.diagnostics().stream().anyMatch(LegacyDiagnostic::error)) throw new IllegalStateException("Resolve legacy discovery errors first");
        Path runtime = supportDirectory.resolve("minecraft-forge-1.7.10-srg.jar");
        if (!Files.isRegularFile(runtime)) throw new FileNotFoundException("Prepared Forge 1.7.10 runtime missing: " + runtime);
        forge = runtime;
        var inputs = new ArrayList<URL>(); inputs.add(runtime.toUri().toURL());
        for (Path path : runtimeInputs(supportDirectory)) {
            if (path.equals(runtime) || path.getFileName().toString().startsWith("forge-1.7.10-")) continue;
            inputs.add(path.toUri().toURL());
        }
        resourcePacks = runtimeResources(supportDirectory);
        Path helper = extractHelper(this.gameDirectory);
        inputs.add(0, helper.toUri().toURL());
        Set<String> modIds = new HashSet<>();
        for (var archive : report.archives()) for (var mod : archive.mods()) modIds.add(mod.id());
        for (var adapter : ServiceLoader.load(github.com.gengyoubo.mfcl.bridge.LegacyModAdapter.class, getClass().getClassLoader())) {
            if (adapter.supports(Set.copyOf(modIds))) adapters.add(adapter);
        }
        mods = new ArrayList<>();
        for (var archive : report.archives()) {
            Path path = Path.of(archive.file());
            if (!LegacyDiscovery.sha256(path).equals(archive.sha256())) throw new IOException("Legacy JAR changed: " + path);
            inputs.add(path.toUri().toURL()); mods.add(path.toFile());
            originalHashes.put(path, archive.sha256());
        }
        URL[] urls = inputs.toArray(URL[]::new);
        root = new IsolatedLegacyClassLoader(urls, getClass().getClassLoader());
        legacy = (URLClassLoader) root.loadClass("github.com.gengyoubo.mfcl.legacy1710.boot.LegacyLaunchLoader")
                .getConstructor(URL[].class, ClassLoader.class).newInstance(urls, root);
    }
    private static List<Path> runtimeInputs(Path directory) throws Exception {
        Path manifest = directory.resolve("runtime-manifest.json");
        if (!Files.exists(manifest)) {
            try (var stream = Files.list(directory)) { return stream.filter(p -> p.toString().endsWith(".jar")).sorted().toList(); }
        }
        var inputs = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject().getAsJsonArray("inputs");
        var paths = new ArrayList<Path>(); var names = new HashSet<String>();
        for (var entry : inputs) {
            var value = entry.getAsJsonObject(); String name = value.get("file").getAsString();
            if (!Path.of(name).getFileName().toString().equals(name) || !name.endsWith(".jar") || !names.add(name)) throw new IOException("Invalid runtime manifest entry: " + name);
            Path path = directory.resolve(name);
            if (!LegacyDiscovery.sha256(path).equalsIgnoreCase(value.get("sha256").getAsString())) throw new IOException("Runtime input checksum differs: " + name);
            paths.add(path);
        }
        if (!names.contains("minecraft-forge-1.7.10-srg.jar")) throw new IOException("Runtime manifest omits prepared Minecraft/Forge");
        return paths;
    }
    /** Verified resource packs outside the classpath, e.g. the vanilla languages that 1.7.10 kept in the launcher asset index. */
    private static List<Path> runtimeResources(Path directory) throws Exception {
        Path manifest = directory.resolve("runtime-manifest.json");
        if (!Files.exists(manifest)) return List.of();
        var resources = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject().getAsJsonArray("resources");
        if (resources == null) return List.of();
        var paths = new ArrayList<Path>(); var names = new HashSet<String>();
        for (var entry : resources) {
            var value = entry.getAsJsonObject(); String name = value.get("file").getAsString();
            if (!Path.of(name).getFileName().toString().equals(name) || !name.endsWith(".zip") || !names.add(name)) throw new IOException("Invalid runtime resource entry: " + name);
            Path path = directory.resolve(name);
            if (!LegacyDiscovery.sha256(path).equalsIgnoreCase(value.get("sha256").getAsString())) throw new IOException("Runtime resource checksum differs: " + name);
            paths.add(path);
        }
        return List.copyOf(paths);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> boot() throws Exception {
        Thread thread = Thread.currentThread(); ClassLoader before = thread.getContextClassLoader();
        PrintStream stdout = System.out, stderr = System.err;
        try {
            thread.setContextClassLoader(legacy);
            Object logContext = root.loadClass("org.apache.logging.log4j.LogManager").getMethod("getContext", boolean.class).invoke(null, false);
            logContext.getClass().getMethod("setConfigLocation", URI.class).invoke(logContext,
                    Objects.requireNonNull(getClass().getResource("/mfcl/legacy-probe-log4j2.xml")).toURI());
            for (var adapter : adapters) adapter.beforeBoot(legacy);
            Class<?> launch = root.loadClass("net.minecraft.launchwrapper.Launch");
            launch.getField("classLoader").set(null, legacy);
            launch.getField("minecraftHome").set(null, gameDirectory.toFile());
            Map<String, Object> board = new HashMap<>();
            board.put("fml.deobfuscatedEnvironment", true); board.put("Tweaks", new ArrayList<>());
            board.put("TweakClasses", new ArrayList<>()); board.put("launchArgs", new HashMap<>());
            launch.getField("blackboard").set(null, board);
            Class<?> injection = root.loadClass("cpw.mods.fml.relauncher.FMLInjectionData");
            Method build = injection.getDeclaredMethod("build", File.class, root.loadClass("net.minecraft.launchwrapper.LaunchClassLoader"));
            build.setAccessible(true); build.invoke(null, gameDirectory.toFile(), legacy);
            Object side = root.loadClass("cpw.mods.fml.relauncher.Side").getField("CLIENT").get(null);
            for (String type : List.of("cpw.mods.fml.relauncher.FMLLaunchHandler", "cpw.mods.fml.relauncher.FMLRelaunchLog")) set(root.loadClass(type), "side", side);
            set(root.loadClass("cpw.mods.fml.relauncher.FMLRelaunchLog"), "minecraftHome", gameDirectory.toFile());
            Class<?> driver = legacy.loadClass("github.com.gengyoubo.mfcl.legacy1710.RuntimeDriver");
            if (openGlBackend != null) for (String type : List.of("org.lwjgl.opengl.GL11", "org.lwjgl.opengl.GL20", "org.lwjgl.opengl.GL30"))
                github.com.gengyoubo.mfcl.bridge.LegacyOpenGl.attach(legacy.loadClass(type), openGlBackend);
            Map<String, Object> glVerification = openGlBackend != null && Boolean.getBoolean("mfcl.test.openGl")
                    ? openGlBackend.verify(legacy.loadClass("org.lwjgl.opengl.GL11"), legacy.loadClass("org.lwjgl.opengl.GL20")) : Map.of();
            Runnable initializeClient = () -> {
                try {
                    earlyClient = new github.com.gengyoubo.mfcl.bridge.LegacyClientData(legacy, resourcePacks);
                    earlyClient.initializeContext(gameDirectory, List.of("assets/minecraft/lang/en_US.lang"), clientSession);
                } catch (Exception failure) { throw new IllegalStateException("Cannot initialize legacy client services", failure); }
            };
            Map<String, Object> result = (Map<String, Object>) driver.getMethod("boot", File.class, File.class, List.class, Runnable.class)
                    .invoke(driver.getConstructor().newInstance(), gameDirectory.toFile(), forge.toFile(), mods, initializeClient);
            result.put("retainedDisplayLists", github.com.gengyoubo.mfcl.bridge.LegacyDisplayLists.statistics(legacy.loadClass("org.lwjgl.opengl.GL11")));
            if (openGlBackend != null) result.put("hostOpenGl", openGlBackend.statistics());
            if (!glVerification.isEmpty()) result.put("hostOpenGlVerification", glVerification);
            if (Boolean.TRUE.equals(result.get("legacyFmlStarted"))) {
                services = new github.com.gengyoubo.mfcl.bridge.LegacyServices(gameDirectory, legacy, earlyClient);
                result.put("legacyRegistries", services.items().registrySummary());
                var reports = new LinkedHashMap<String, Object>();
                for (var adapter : adapters) {
                    try { reports.put(adapter.id(), adapter.initialize(services)); }
                    catch (Throwable failure) {
                        StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
                        reports.put(adapter.id(), Map.of("status", "FAILED", "failure", trace.toString()));
                    }
                }
                result.put("adapters", reports);
            }
            try { legacy.loadClass("net.minecraft.world.level.Level"); throw new IllegalStateException("Modern Minecraft leaked into legacy domain"); }
            catch (ClassNotFoundException expected) { result.put("modernClassIsolationVerified", true); }
            for (var input : originalHashes.entrySet()) {
                if (!LegacyDiscovery.sha256(input.getKey()).equals(input.getValue())) throw new IOException("Original mod JAR changed during boot: " + input.getKey());
            }
            return result;
        } finally {
            if (openGlBackend != null) openGlBackend.bootstrapComplete();
            thread.setContextClassLoader(before); if (System.out != stdout) System.setOut(stdout); if (System.err != stderr) System.setErr(stderr);
        }
    }
    private static void set(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    private static Path extractHelper(Path gameDirectory) throws Exception {
        byte[] bytes;
        try (var in = EmbeddedLegacyRuntime.class.getResourceAsStream("/mfcl/mfcl-legacy-runtime.jar")) {
            if (in == null) throw new FileNotFoundException("Embedded legacy helper JAR missing"); bytes = in.readAllBytes();
        }
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        Path cache = gameDirectory.resolve("mfcl/cache/1.7.10/helpers"); Files.createDirectories(cache);
        Path target = cache.resolve(hash + ".jar");
        if (!Files.exists(target)) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        return target;
    }
    @Override public void close() throws IOException {
        if (developerApi != null) developerApi.close();
        if (services != null) services.worlds().close();
        if (earlyClient != null) earlyClient.close();
        if (openGlBackend != null) {
            try { openGlBackend.close(); } catch (Exception failure) { throw new IOException("Cannot release legacy host GL resources", failure); }
        }
        legacy.close(); root.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("EmbeddedLegacyRuntime <game-directory> <runtime-directory>");
        // Dedicated CLI only. Embedded callers retain the host's global properties.
        System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector");
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector");
        System.setProperty("log4j.configurationFile", Objects.requireNonNull(EmbeddedLegacyRuntime.class.getResource("/mfcl/legacy-probe-log4j2.xml")).toString());
        Path game = Path.of(args[0]);
        var report = new LegacyRuntimeService().inspect(game);
        try (var runtime = new EmbeddedLegacyRuntime(game, Path.of(args[1]), report)) {
            var result = runtime.boot();
            Path output = game.resolve("logs/mfcl/runtime-1.7.10.json"); Files.createDirectories(output.getParent());
            Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            System.out.println("MFCL_RUNTIME_RESULT=" + new com.google.gson.Gson().toJson(result));
            if (!Boolean.TRUE.equals(result.get("legacyFmlStarted"))) System.exit(1);
        }
    }
}
