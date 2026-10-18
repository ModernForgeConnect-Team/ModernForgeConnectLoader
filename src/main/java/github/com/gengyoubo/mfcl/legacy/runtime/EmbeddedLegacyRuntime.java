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
    private github.com.gengyoubo.mfcl.bridge.NeiDataBridge nei;

    public EmbeddedLegacyRuntime(Path gameDirectory, Path supportDirectory, LegacyRuntimeService.Report report) throws Exception {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        if (report.diagnostics().stream().anyMatch(LegacyDiagnostic::error)) throw new IllegalStateException("Resolve legacy discovery errors first");
        Path runtime = supportDirectory.resolve("minecraft-forge-1.7.10-srg.jar");
        if (!Files.isRegularFile(runtime)) throw new FileNotFoundException("Prepared Forge 1.7.10 runtime missing: " + runtime);
        forge = runtime;
        var inputs = new ArrayList<URL>(); inputs.add(runtime.toUri().toURL());
        try (var stream = Files.list(supportDirectory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                if (path.equals(runtime) || path.getFileName().toString().startsWith("forge-1.7.10-")) continue;
                inputs.add(path.toUri().toURL());
            }
        }
        Path helper = extractHelper(this.gameDirectory);
        inputs.add(0, helper.toUri().toURL());
        mods = new ArrayList<>();
        for (var archive : report.archives()) {
            Path path = Path.of(archive.file());
            if (!LegacyDiscovery.sha256(path).equals(archive.sha256())) throw new IOException("Legacy JAR changed: " + path);
            inputs.add(path.toUri().toURL()); mods.add(path.toFile());
        }
        URL[] urls = inputs.toArray(URL[]::new);
        root = new IsolatedLegacyClassLoader(urls, getClass().getClassLoader());
        legacy = (URLClassLoader) root.loadClass("github.com.gengyoubo.mfcl.legacy1710.boot.LegacyLaunchLoader")
                .getConstructor(URL[].class, ClassLoader.class).newInstance(urls, root);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> boot() throws Exception {
        Thread thread = Thread.currentThread(); ClassLoader before = thread.getContextClassLoader();
        PrintStream stdout = System.out, stderr = System.err;
        try {
            thread.setContextClassLoader(legacy);
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
            Map<String, Object> result = (Map<String, Object>) driver.getMethod("boot", File.class, File.class, List.class)
                    .invoke(driver.getConstructor().newInstance(), gameDirectory.toFile(), forge.toFile(), mods);
            if (Boolean.TRUE.equals(result.get("legacyFmlStarted")) && Boolean.TRUE.equals(result.get("neiClientHandlerLoaded"))) {
                try {
                    nei = new github.com.gengyoubo.mfcl.bridge.NeiDataBridge(legacy);
                    result.put("neiData", nei.initialize(gameDirectory));
                    result.put("neiDataReady", true);
                    var craftingTable = nei.search("crafting").stream().filter(s -> s.key().equals("minecraft:crafting_table")).findFirst().orElseThrow();
                    result.put("neiCraftingTableRecipes", nei.recipes(craftingTable, false).size());
                    result.put("neiCraftingTableUsages", nei.recipes(craftingTable, true).size());
                    var diamond = nei.search("diamond").stream().filter(s -> s.key().equals("minecraft:diamond")).findFirst().orElseThrow();
                    var projected = new github.com.gengyoubo.mfcl.bridge.NeiDataBridge.HostRecipe("mfcl:projection_verification", "shapeless", 0, 0, List.of(List.of(diamond)), craftingTable);
                    // A separate verification entry point exercises mutation; normal embedded boot retains actual recipes.
                    if (Boolean.getBoolean("mfcl.test.verifyRecipeProjection")) {
                        nei.projectHostRecipes(List.of(projected));
                        var recipes = nei.recipes(craftingTable, false);
                        if (recipes.size() != 1 || recipes.get(0).ingredients().stream().flatMap(i -> i.alternatives().stream()).noneMatch(s -> s.key().equals("minecraft:diamond")))
                            throw new IllegalStateException("Original NEI did not consume projected host recipe");
                        if (nei.recipes(diamond, true).isEmpty()) throw new IllegalStateException("Original NEI usage handler did not consume projected ingredient");
                        nei.projectHostRecipes(List.of(projected));
                        if (nei.recipes(craftingTable, false).size() != 1) throw new IllegalStateException("Recipe reload duplicated projected recipes");
                        result.put("recipeProjectionVerified", true);
                    }
                } catch (Throwable error) {
                    result.put("neiDataReady", false);
                    StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace)); result.put("neiDataFailure", trace.toString());
                }
            }
            try { legacy.loadClass("net.minecraft.world.level.Level"); throw new IllegalStateException("Modern Minecraft leaked into legacy domain"); }
            catch (ClassNotFoundException expected) { result.put("modernClassIsolationVerified", true); }
            for (File mod : mods) {
                var archive = new LegacyDiscovery().scan(new LegacyPaths(gameDirectory)).archives().stream().filter(a -> Path.of(a.file()).equals(mod.toPath())).findFirst().orElseThrow();
                if (!LegacyDiscovery.sha256(mod.toPath()).equals(archive.sha256())) throw new IOException("Original mod JAR changed during boot: " + mod);
            }
            return result;
        } finally { thread.setContextClassLoader(before); if (System.out != stdout) System.setOut(stdout); if (System.err != stderr) System.setErr(stderr); }
    }
    public Optional<github.com.gengyoubo.mfcl.bridge.NeiDataBridge> nei() { return nei != null && nei.ready() ? Optional.of(nei) : Optional.empty(); }
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
    @Override public void close() throws IOException { legacy.close(); root.close(); }

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
            if (!Boolean.TRUE.equals(result.get("legacyFmlStarted")) || result.containsKey("neiDataReady") && !Boolean.TRUE.equals(result.get("neiDataReady"))) System.exit(1);
        }
    }
}
