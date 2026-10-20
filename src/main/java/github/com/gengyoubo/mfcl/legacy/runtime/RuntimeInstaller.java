package github.com.gengyoubo.mfcl.legacy.runtime;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import github.com.gengyoubo.mfcl.legacy.LegacyDiagnostic;
import github.com.gengyoubo.mfcl.legacy.LegacyDiscovery;
import github.com.gengyoubo.mfcl.legacy.LegacyRuntimeCheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Downloads, verifies and prepares the private Minecraft 1.7.10 / Forge 10.13.4.1614 runtime. Nothing from Mojang or
 * Forge ships with MFCL: every input comes from its official URL and is checked against a pinned hash. The runtime is
 * assembled in a sibling temporary directory and moved into place only after it passes {@link LegacyRuntimeCheck}.
 * Used by the mod on startup and, through {@link #main}, by tools/prepare-legacy-runtime.ps1.
 */
public final class RuntimeInstaller {
    public static final String AUTO_PREPARE_PROPERTY = "mfcl.autoPrepareRuntime";
    static final String MANIFEST = "runtime-manifest.json", LANGUAGE_PACK = "minecraft-1.7.10-languages.zip";
    // Mojang's package URLs are the SHA-1 of the file itself.
    static final Download VERSION_JSON = new Download("minecraft-1.7.10-version.json",
            "https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json", "SHA-1", "ed5d8789ed29872ea2ef1c348302b0c55e3f3468");
    static final Download ASSET_INDEX = new Download("minecraft-1.7.10-assets.json",
            "https://launchermeta.mojang.com/v1/packages/1863782e33ce7b584fc45b037325a1964e095d3e/1.7.10.json", "SHA-1", "1863782e33ce7b584fc45b037325a1964e095d3e");
    // The archive itself is not pinned; the complete mapping file inside it is.
    static final String MCP_URL = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.7.10/mcp-1.7.10-srg.zip";
    static final String SRG_SHA256 = "43d4376110b2638213d3096f80f8395c076eb05b58d6c0dbd22c0d999b37a357";
    static final String FORGE_UNIVERSAL = "forge-1.7.10-10.13.4.1614-1.7.10-universal.jar";
    /** Libraries of MFCL's own patching tool, kept out of the modern game and loaded only for preparation. */
    static final List<Download> TOOLING = List.of(
            maven("org/ow2/asm/asm/9.7.1/asm-9.7.1.jar", "8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281"),
            maven("org/ow2/asm/asm-tree/9.7.1/asm-tree-9.7.1.jar", "9929881f59eb6b840e86d54570c77b59ce721d104e6dfd7a40978991c2d3b41f"),
            maven("org/ow2/asm/asm-commons/9.7.1/asm-commons-9.7.1.jar", "9a579b54d292ad9be171d4313fd4739c635592c2b5ac3a459bbd1049cddec6a0"),
            maven("org/glavo/pack200/0.3.0/pack200-0.3.0.jar", "9b65fae9a51947f8d9bb9f2baf5eeacbddc74423037372fe3d4f62b78f30635d"),
            maven("org/tukaani/xz/1.10/xz-1.10.jar", "95c63c1a55b22dd6453890a419cc1a640f790bbf7d8ae82db1e30aefefb08888"));
    // Forge upgrades Guava and commons-lang; Log4j uses the fixed modern version from the reference specification.
    private static final Pattern REPLACED_LIBRARY = Pattern.compile("guava|log4j|commons-lang3");
    private static final Pattern LANGUAGE_LIBRARY = Pattern.compile("^(org\\.scala-lang(?:\\.plugins)?|com\\.typesafe(?:\\.akka)?):.*");
    private static final Pattern LANGUAGE_ASSET = Pattern.compile("^minecraft/lang/[^/]+\\.lang$");
    private static final FileTime ASSET_TIME = FileTime.fromMillis(1403740800000L); // 2014-06-26, the 1.7.10 release window
    private static final int ATTEMPTS = 3;

    /** A file with its official URL and the pinned digest it must have. */
    record Download(String file, String url, String algorithm, String hash) {}

    /** Copies one URL to a file; replaced in tests. */
    @FunctionalInterface interface Fetcher { void fetch(String url, Path target) throws IOException; }

    private final Path runtimeDirectory, downloadDirectory;
    private final Consumer<String> progress;
    private final Fetcher fetcher;

    RuntimeInstaller(Path runtimeDirectory, Path downloadDirectory, Consumer<String> progress, Fetcher fetcher) {
        this.runtimeDirectory = runtimeDirectory.toAbsolutePath().normalize();
        this.downloadDirectory = downloadDirectory.toAbsolutePath().normalize();
        this.progress = progress;
        this.fetcher = fetcher;
    }

    public RuntimeInstaller(Path runtimeDirectory, Consumer<String> progress) {
        this(runtimeDirectory, defaultDownloads(runtimeDirectory), progress, RuntimeInstaller::httpFetch);
    }

    static Path defaultDownloads(Path runtimeDirectory) {
        Path runtime = runtimeDirectory.toAbsolutePath().normalize();
        return runtime.resolveSibling(runtime.getFileName() + ".downloads");
    }

    /**
     * Startup entry: prepares the runtime only when {@code mods/1.7.10} contains JARs, the runtime fails
     * {@link LegacyRuntimeCheck} and {@code -Dmfcl.autoPrepareRuntime=false} is not set. Never throws.
     *
     * @return null when the runtime is usable afterwards or not needed; otherwise why it was not prepared.
     */
    public static String prepareIfNeeded(Path gameDirectory, Path runtimeDirectory, Consumer<String> progress) {
        if (!hasLegacyJars(gameDirectory.resolve("mods/1.7.10"))) return null;
        List<LegacyDiagnostic> errors = LegacyRuntimeCheck.errors(runtimeDirectory);
        if (errors.isEmpty()) return null;
        if (!Boolean.parseBoolean(System.getProperty(AUTO_PREPARE_PROPERTY, "true"))) {
            progress.accept("Private Forge 1.7.10 runtime is not usable and automatic preparation is disabled by -D" + AUTO_PREPARE_PROPERTY + "=false");
            return "automatic preparation is disabled by -D" + AUTO_PREPARE_PROPERTY + "=false";
        }
        progress.accept("Private Forge 1.7.10 runtime is not usable (" + errors.get(0).message() + "); preparing it automatically");
        try {
            new RuntimeInstaller(runtimeDirectory, progress).install(false);
            return null;
        } catch (IOException | RuntimeException | LinkageError failure) {
            progress.accept("Automatic runtime preparation failed: " + failure);
            return "the automatic download failed this startup: " + failure;
        }
    }

    static boolean hasLegacyJars(Path modDirectory) {
        if (!Files.isDirectory(modDirectory)) return false;
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(modDirectory,
                file -> Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))) {
            return jars.iterator().hasNext();
        } catch (IOException failure) {
            return false;
        }
    }

    /**
     * Installs the runtime, replacing an unusable one. Concurrent installers for the same directory wait for each other.
     *
     * @param force also rebuild a runtime that already passes the check.
     */
    public void install(boolean force) throws IOException {
        Path parent = runtimeDirectory.getParent();
        Files.createDirectories(parent);
        Files.createDirectories(downloadDirectory);
        try (var lockFile = FileChannel.open(parent.resolve(runtimeDirectory.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = lockFile.lock()) {
            if (!force && LegacyRuntimeCheck.errors(runtimeDirectory).isEmpty()) {
                progress.accept("Private Forge 1.7.10 runtime is already prepared: " + runtimeDirectory);
                return;
            }
            Path staging = parent.resolve(runtimeDirectory.getFileName() + ".installing-" + UUID.randomUUID());
            try {
                Files.createDirectories(staging);
                build(staging);
                var problems = LegacyRuntimeCheck.errors(staging);
                if (!problems.isEmpty()) throw new IOException("Prepared runtime failed verification: " + problems.get(0).message());
                replace(staging);
            } finally {
                deleteTree(staging);
            }
        }
        // The default cache only exists to resume an interrupted first launch; an explicit one is kept for reuse.
        if (downloadDirectory.equals(defaultDownloads(runtimeDirectory))) deleteTree(downloadDirectory);
        progress.accept("Private Forge 1.7.10 runtime is ready: " + runtimeDirectory);
    }

    private void build(Path staging) throws IOException {
        var spec = json(resource("/mfcl/runtime-inputs/nei-reference-inputs.json"));
        var languagePins = json(resource("/mfcl/runtime-inputs/legacy-language-inputs.json"));
        Path versionFile = download(VERSION_JSON, "Minecraft 1.7.10 version metadata");
        var metadata = json(Files.readString(versionFile, StandardCharsets.UTF_8));

        var runtimeInputs = new LinkedHashMap<String, Download>();
        for (var element : spec.getAsJsonArray("dependencies")) {
            var dependency = element.getAsJsonObject();
            if (!"runtime".equals(dependency.get("directory").getAsString())) continue;
            runtimeInputs.put(dependency.get("file").getAsString(), new Download(dependency.get("file").getAsString(),
                    dependency.get("url").getAsString(), "SHA-256", dependency.get("sha256").getAsString()));
        }
        runtimeInputs.putAll(minecraftLibraries(metadata));
        var client = metadata.getAsJsonObject("downloads").getAsJsonObject("client");
        var clientJar = new Download("minecraft-client-1.7.10.jar", client.get("url").getAsString(), "SHA-1", client.get("sha1").getAsString());
        Download forge = runtimeInputs.get(FORGE_UNIVERSAL);
        if (forge == null) throw new IOException("Runtime specification has no " + FORGE_UNIVERSAL);

        Path srg = mappings();
        Path clientFile = download(clientJar, "Minecraft 1.7.10 client");
        Path forgeFile = download(forge, "Forge 10.13.4.1614");
        var inputs = new ArrayList<String>();
        int index = 0;
        for (var input : runtimeInputs.values()) {
            index++;
            if (input == forge) continue;
            Path file = download(input, "runtime library " + index + "/" + runtimeInputs.size());
            Files.copy(file, staging.resolve(input.file()), StandardCopyOption.REPLACE_EXISTING);
            inputs.add(input.file());
        }
        for (var library : languageLibraries(forgeFile, languagePins)) {
            Path file = download(library, "Forge language library");
            Files.copy(file, staging.resolve(library.file()), StandardCopyOption.REPLACE_EXISTING);
            if (!inputs.contains(library.file())) inputs.add(library.file());
        }
        patch(clientFile, forgeFile, srg, staging.resolve(LegacyRuntimeCheck.SRG_JAR));
        inputs.add(LegacyRuntimeCheck.SRG_JAR);
        languagePack(staging.resolve(LANGUAGE_PACK));
        writeManifest(staging, inputs, List.of(LANGUAGE_PACK));
    }

    /** The vanilla libraries that the legacy runtime keeps, keyed by file name. */
    static Map<String, Download> minecraftLibraries(JsonObject metadata) {
        var result = new LinkedHashMap<String, Download>();
        for (var element : metadata.getAsJsonArray("libraries")) {
            var library = element.getAsJsonObject();
            var downloads = library.getAsJsonObject("downloads");
            if (downloads == null || !downloads.has("artifact") || REPLACED_LIBRARY.matcher(library.get("name").getAsString()).find()) continue;
            var artifact = downloads.getAsJsonObject("artifact");
            String path = artifact.get("path").getAsString();
            String file = path.substring(path.lastIndexOf('/') + 1);
            result.putIfAbsent(file, new Download(file, artifact.get("url").getAsString(), "SHA-1", artifact.get("sha1").getAsString()));
        }
        return result;
    }

    /** Forge's Scala/Typesafe libraries, resolved to pinned canonical Maven artifacts. */
    static List<Download> languageLibraries(Path forgeJar, JsonObject pins) throws IOException {
        JsonObject forgeVersion;
        try (var zip = new ZipFile(forgeJar.toFile())) {
            var entry = zip.getEntry("version.json");
            if (entry == null) throw new IOException("Forge JAR has no version.json");
            try (var in = zip.getInputStream(entry)) { forgeVersion = json(new String(in.readAllBytes(), StandardCharsets.UTF_8)); }
        }
        var result = new ArrayList<Download>();
        for (var element : forgeVersion.getAsJsonArray("libraries")) {
            String name = element.getAsJsonObject().get("name").getAsString();
            if (!LANGUAGE_LIBRARY.matcher(name).matches()) continue;
            var matches = new ArrayList<JsonObject>();
            for (var pin : pins.getAsJsonArray("dependencies")) if (name.equals(pin.getAsJsonObject().get("coordinate").getAsString())) matches.add(pin.getAsJsonObject());
            if (matches.size() != 1) throw new IOException("Unpinned Forge library: " + name);
            var pin = matches.get(0);
            result.add(new Download(pin.get("file").getAsString(), pin.get("url").getAsString(), "SHA-256", pin.get("sha256").getAsString()));
        }
        return result;
    }

    private Path mappings() throws IOException {
        Path srg = downloadDirectory.resolve("joined-1.7.10.srg");
        if (Files.isRegularFile(srg) && digest(srg, "SHA-256").equals(SRG_SHA256)) return srg;
        Path archive = downloadDirectory.resolve("mcp-1.7.10-srg.zip");
        for (int attempt = 1; ; attempt++) {
            try {
                if (!Files.isRegularFile(archive)) fetchVerified(new Download(archive.getFileName().toString(), MCP_URL, null, null), archive, "MCP 1.7.10 mappings");
                Path temporary = downloadDirectory.resolve(srg.getFileName() + ".part");
                try (var zip = new ZipFile(archive.toFile())) {
                    var entry = zip.getEntry("joined.srg");
                    if (entry == null) throw new IOException("MCP archive has no joined.srg");
                    try (var in = zip.getInputStream(entry)) { Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING); }
                }
                if (!digest(temporary, "SHA-256").equals(SRG_SHA256)) {
                    Files.delete(temporary);
                    throw new IOException("MCP mappings differ from the pinned reference");
                }
                move(temporary, srg);
                return srg;
            } catch (IOException failure) {
                Files.deleteIfExists(archive);
                if (attempt >= ATTEMPTS) throw failure;
                progress.accept("Retrying MCP mappings after: " + failure.getMessage());
            }
        }
    }

    /** Applies Forge's binary patches and the SRG mappings with MFCL's patching tool, isolated from the modern game. */
    private void patch(Path client, Path forge, Path srg, Path output) throws IOException {
        progress.accept("Applying Forge binary patches and SRG mappings (this can take a minute)");
        var urls = new ArrayList<URL>();
        Path tool = downloadDirectory.resolve("mfcl-runtime-preparation.jar");
        try (var in = RuntimeInstaller.class.getResourceAsStream("/mfcl/mfcl-runtime-preparation.jar")) {
            if (in == null) throw new IOException("MFCL JAR has no mfcl/mfcl-runtime-preparation.jar");
            Files.copy(in, tool, StandardCopyOption.REPLACE_EXISTING);
        }
        urls.add(tool.toUri().toURL());
        for (var library : TOOLING) urls.add(download(library, "preparation library").toUri().toURL());
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            thread.setContextClassLoader(loader);
            loader.loadClass("github.com.gengyoubo.mfcl.tools.PrepareLegacyRuntime").getMethod("main", String[].class)
                    .invoke(null, (Object) new String[]{client.toString(), forge.toString(), srg.toString(), output.toString()});
        } catch (InvocationTargetException failure) {
            throw new IOException("Runtime patching failed: " + failure.getCause(), failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Cannot start the runtime patching tool", failure);
        } finally {
            thread.setContextClassLoader(previous);
        }
        if (!Files.isRegularFile(output)) throw new IOException("Runtime patching produced no " + output.getFileName());
    }

    /** 1.7.10 ships only en_US.lang in the client JAR; the other vanilla languages come from the launcher assets. */
    private void languagePack(Path target) throws IOException {
        var objects = json(Files.readString(download(ASSET_INDEX, "Minecraft 1.7.10 asset index"), StandardCharsets.UTF_8)).getAsJsonObject("objects");
        var wanted = objects.keySet().stream().filter(name -> LANGUAGE_ASSET.matcher(name).matches() || name.equals("pack.mcmeta")).sorted().toList();
        if (!wanted.contains("minecraft/lang/zh_CN.lang")) throw new IOException("Asset index has no zh_CN language");
        progress.accept("Downloading " + wanted.size() + " Minecraft 1.7.10 language files");
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (String name : wanted) {
                String hash = objects.getAsJsonObject(name).get("hash").getAsString();
                Path file = download(new Download("assets/" + hash, "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, "SHA-1", hash), null);
                var entry = new ZipEntry(name.equals("pack.mcmeta") ? name : "assets/" + name);
                entry.setLastModifiedTime(ASSET_TIME);
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    /** Writes runtime-manifest.json for files already in {@code directory}, hashing each one. */
    static void writeManifest(Path directory, List<String> inputs, List<String> resources) throws IOException {
        var manifest = new JsonObject();
        manifest.addProperty("minecraft", "1.7.10");
        manifest.addProperty("forge", "10.13.4.1614");
        manifest.add("inputs", entries(directory, inputs));
        manifest.add("resources", entries(directory, resources));
        Path temporary = directory.resolve(MANIFEST + ".part");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n", StandardCharsets.UTF_8);
        move(temporary, directory.resolve(MANIFEST));
    }

    private static JsonArray entries(Path directory, List<String> files) throws IOException {
        var result = new JsonArray();
        for (String file : files.stream().distinct().sorted().toList()) {
            var entry = new JsonObject();
            entry.addProperty("file", file);
            entry.addProperty("sha256", LegacyDiscovery.sha256(directory.resolve(file)));
            result.add(entry);
        }
        return result;
    }

    /** Returns the verified cached file, downloading it first when it is missing or differs from the pinned hash. */
    Path download(Download download, String label) throws IOException {
        Path target = downloadDirectory.resolve(download.file());
        if (Files.isRegularFile(target)) {
            if (digest(target, download.algorithm()).equalsIgnoreCase(download.hash())) return target;
            progress.accept("Cached " + download.file() + " differs from its pinned hash; downloading it again");
            Files.delete(target);
        }
        for (int attempt = 1; ; attempt++) {
            try {
                fetchVerified(download, target, label);
                return target;
            } catch (IOException failure) {
                if (attempt >= ATTEMPTS) throw failure;
                progress.accept("Retrying " + download.file() + " after: " + failure.getMessage());
            }
        }
    }

    private void fetchVerified(Download download, Path target, String label) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".part");
        try {
            if (label != null) progress.accept("Downloading " + label + ": " + download.url());
            fetcher.fetch(download.url(), temporary);
            if (download.hash() != null) {
                String actual = digest(temporary, download.algorithm());
                if (!actual.equalsIgnoreCase(download.hash())) throw new IOException("Downloaded " + download.file() + " has "
                        + download.algorithm() + " " + actual + ", expected " + download.hash() + " (" + download.url() + ")");
            }
            move(temporary, target);
            if (label != null) progress.accept(String.format(Locale.ROOT, "Verified %s (%.1f MB)", download.file(), Files.size(target) / 1048576.0));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Swaps the verified staging directory into place; an unusable previous runtime is kept beside it, never deleted. */
    private void replace(Path staging) throws IOException {
        Path backup = null;
        if (Files.exists(runtimeDirectory)) {
            backup = runtimeDirectory.resolveSibling(runtimeDirectory.getFileName() + ".replaced-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            move(runtimeDirectory, backup);
        }
        try {
            move(staging, runtimeDirectory);
        } catch (IOException failure) {
            if (backup != null) move(backup, runtimeDirectory);
            throw failure;
        }
        if (backup != null) progress.accept("The previous, unusable runtime was moved to " + backup + "; delete it when it is no longer needed");
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    static String digest(Path file, String algorithm) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance(algorithm); }
        catch (NoSuchAlgorithmException failure) { throw new IOException(failure); }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) > 0;) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void httpFetch(String url, Path target) throws IOException {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "MFCL-RuntimeInstaller");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + status + " for " + url);
            try (var in = connection.getInputStream()) { Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            connection.disconnect();
        }
    }

    private static Download maven(String path, String sha256) {
        return new Download(path.substring(path.lastIndexOf('/') + 1), "https://repo.maven.apache.org/maven2/" + path, "SHA-256", sha256);
    }

    private static String resource(String name) throws IOException {
        try (var in = RuntimeInstaller.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("MFCL JAR has no " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(String text) {
        JsonElement element = JsonParser.parseString(text);
        return element.getAsJsonObject();
    }

    /** {@code <gameDirectory> [--runtime <dir>] [--downloads <dir>] [--force]} */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) throw new IllegalArgumentException("Usage: <gameDirectory> [--runtime <dir>] [--downloads <dir>] [--force]");
        Path game = Path.of(args[0]).toAbsolutePath().normalize();
        Path runtime = game.resolve("mfcl/runtime/1.7.10"), downloads = null;
        boolean force = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--runtime" -> runtime = Path.of(args[++i]);
                case "--downloads" -> downloads = Path.of(args[++i]);
                case "--force" -> force = true;
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        Consumer<String> log = message -> System.out.println("MFCL: " + message);
        var installer = new RuntimeInstaller(runtime, downloads == null ? defaultDownloads(runtime) : downloads, log, RuntimeInstaller::httpFetch);
        installer.install(force);
        Files.createDirectories(game.resolve("mods/1.7.10"));
        log.accept("Put original 1.7.10 mods and their dependencies in: " + game.resolve("mods/1.7.10"));
    }
}
