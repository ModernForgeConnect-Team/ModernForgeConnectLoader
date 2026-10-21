package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Moves original Forge 1.7.10 JARs that a launcher installed directly into {@code mods/} to {@code mods/1.7.10/}.
 * <p>
 * Forge 1.20.1's mods-folder locator skips such JARs silently (no {@code mods.toml}, no {@code FMLModType}, so
 * {@code AbstractModProvider} returns a plain {@code ModFileLoadingException}, which is not recorded as a broken file),
 * and its transformer discoverer only claims JARs that provide ModLauncher services. They never reach the module layer,
 * so MFCL can relocate them from its constructor, before {@link LegacyRuntimeService#prepareRuntime} and the legacy scan,
 * and load them in the same launch without a restart.
 * <p>
 * Only JARs that {@link LegacyDiscovery#inspect} accepts and that identify as 1.7.10 are moved; older FML mods use the
 * same annotation and are left alone. The JAR in {@code mods/} wins over what is already in {@code mods/1.7.10/}
 * (same file name or same mod ID, as after a modpack update); replaced files are moved, never deleted, to
 * {@code mfcl/legacy-replaced/<time>/}. Only a byte-identical duplicate is removed from {@code mods/}. Disable with
 * {@code -Dmfcl.autoRelocateLegacyMods=false}.
 */
public final class LegacyModRelocator {
    public static final String PROPERTY = "mfcl.autoRelocateLegacyMods";
    private static final String TARGET_VERSION = "1.7.10";
    private final String backupStamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(java.time.ZoneId.systemDefault()).format(Instant.now());

    public static boolean enabled() { return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true").trim()); }

    public List<LegacyDiagnostic> relocate(LegacyPaths paths) {
        List<LegacyDiagnostic> diagnostics = new ArrayList<>();
        Path root = paths.modDirectory().getParent();
        if (!Files.isDirectory(root)) return diagnostics;
        List<Path> candidates;
        try (var files = Files.list(root)) {
            candidates = files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(LegacyModRelocator::looksLegacy)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        } catch (IOException failure) {
            diagnostics.add(LegacyDiagnostic.notice("LEGACY_RELOCATION_IO", root + ": " + failure.getMessage()));
            return diagnostics;
        }
        List<Map<String, String>> moved = new ArrayList<>();
        for (Path source : candidates) {
            String name = source.getFileName().toString();
            LegacyArchive archive;
            try { archive = new LegacyDiscovery().inspect(source); }
            catch (IOException | RuntimeException notLegacy) { continue; }
            if (archive.mods().isEmpty() && archive.corePlugin().isEmpty()) continue;
            if (!declares1710(name, archive)) {
                diagnostics.add(LegacyDiagnostic.notice("LEGACY_RELOCATION_SKIPPED", name
                        + " looks like an old FML mod but does not identify as Minecraft 1.7.10; move it to mods/1.7.10/ yourself if it is one"));
                continue;
            }
            Path target = paths.modDirectory().resolve(name);
            try {
                Files.createDirectories(paths.modDirectory());
                if (Files.exists(target) && LegacyDiscovery.sha256(target).equals(archive.sha256())) {
                    // Byte-identical copy already in place (e.g. a launcher re-downloaded it): nothing is lost.
                    Files.delete(source);
                    diagnostics.add(LegacyDiagnostic.notice("LEGACY_RELOCATION_DUPLICATE", "mods/" + name
                            + " was identical to mods/1.7.10/" + name + "; removed the duplicate from mods/"));
                    continue;
                }
                List<Path> superseded = superseded(paths.modDirectory(), target, archive);
                Path backups = backupDirectory(paths);
                Map<Path, Path> backedUp = new LinkedHashMap<>();
                try {
                    for (Path old : superseded) {
                        Files.createDirectories(backups);
                        Path backup = unique(backups.resolve(old.getFileName().toString()));
                        move(old, backup);
                        backedUp.put(old, backup);
                    }
                    move(source, target);
                } catch (IOException failure) {
                    for (var restore : backedUp.entrySet()) {
                        try { move(restore.getValue(), restore.getKey()); } catch (IOException ignored) { /* reported below */ }
                    }
                    throw failure;
                }
                if (!LegacyDiscovery.sha256(target).equals(archive.sha256())) throw new IOException("content changed while moving");
                String ids = String.join(", ", archive.mods().stream().map(LegacyMod::id).toList());
                if (backedUp.isEmpty()) {
                    diagnostics.add(LegacyDiagnostic.notice("LEGACY_MOD_RELOCATED", "Moved mods/" + name + " to mods/1.7.10/ (" + ids + ")"));
                } else {
                    diagnostics.add(LegacyDiagnostic.notice("LEGACY_MOD_REPLACED", "Moved mods/" + name + " to mods/1.7.10/ (" + ids
                            + "), replacing " + String.join(", ", backedUp.keySet().stream().map(p -> p.getFileName().toString()).toList())
                            + "; the replaced files were kept in " + paths.gameDirectory().relativize(backups)));
                }
                var entry = new LinkedHashMap<String, String>();
                entry.put("time", Instant.now().toString());
                entry.put("source", source.toString());
                entry.put("target", target.toString());
                entry.put("sha256", archive.sha256());
                if (!backedUp.isEmpty()) entry.put("replaced", String.join(";", backedUp.values().stream().map(Path::toString).toList()));
                moved.add(entry);
            } catch (IOException failure) {
                diagnostics.add(LegacyDiagnostic.notice("LEGACY_RELOCATION_FAILED", "Could not move mods/" + name
                        + " to mods/1.7.10/: " + failure + ". Move it manually"));
            }
        }
        if (!moved.isEmpty()) {
            try { appendJournal(paths.gameDirectory().resolve("logs/mfcl/legacy-relocations.jsonl"), moved); }
            catch (IOException failure) { diagnostics.add(LegacyDiagnostic.notice("LEGACY_RELOCATION_IO", "Cannot write relocation journal: " + failure.getMessage())); }
        }
        return diagnostics;
    }

    /** Cheap pre-filter so a large modern pack is not fully inspected on every launch. */
    static boolean looksLegacy(Path jar) {
        try (JarFile file = new JarFile(jar.toFile(), false)) {
            if (file.getEntry("META-INF/mods.toml") != null || file.getEntry("META-INF/neoforge.mods.toml") != null
                    || file.getEntry("fabric.mod.json") != null || file.getEntry("quilt.mod.json") != null) return false;
            var manifest = file.getManifest();
            var attributes = manifest == null ? null : manifest.getMainAttributes();
            if (attributes != null && attributes.getValue("FMLModType") != null) return false;
            if (file.stream().anyMatch(e -> e.getName().startsWith("META-INF/services/cpw.mods.modlauncher")
                    || e.getName().startsWith("META-INF/services/net.minecraftforge."))) return false;
            if (attributes != null && attributes.getValue("FMLCorePlugin") != null) return true;
            if (jar.getFileName().toString().contains(TARGET_VERSION)) return true;
            return file.stream().anyMatch(e -> !e.isDirectory() && !e.getName().contains("/")
                    && e.getName().endsWith(".info") && !e.getName().equals("dependencies.info"));
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean declares1710(String fileName, LegacyArchive archive) {
        if (fileName.contains(TARGET_VERSION)) return true;
        return archive.mods().stream().anyMatch(mod -> mod.minecraftVersion().contains(TARGET_VERSION));
    }

    /**
     * Files in mods/1.7.10/ that the incoming JAR replaces: the one with the same file name, and any other JAR that
     * declares one of its mod IDs (a pack update usually changes the version in the file name). JARs placed in mods/
     * are the ones the launcher manages, so they win; anything unreadable in mods/1.7.10/ is left to the normal scan.
     */
    private static List<Path> superseded(Path legacyDirectory, Path target, LegacyArchive incoming) throws IOException {
        Set<String> ids = new HashSet<>(incoming.mods().stream().map(LegacyMod::id).toList());
        List<Path> result = new ArrayList<>();
        try (var files = Files.list(legacyDirectory)) {
            for (Path existing : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                if (existing.equals(target)) { result.add(existing); continue; }
                try {
                    if (new LegacyDiscovery().inspect(existing).mods().stream().anyMatch(mod -> ids.contains(mod.id()))) result.add(existing);
                } catch (IOException | RuntimeException unreadable) { /* not ours to judge */ }
            }
        }
        return result;
    }

    /** Outside mods/ so neither Forge nor the legacy scan sees the replaced files. */
    private Path backupDirectory(LegacyPaths paths) {
        return paths.gameDirectory().resolve("mfcl/legacy-replaced").resolve(backupStamp);
    }

    private static Path unique(Path path) {
        if (!Files.exists(path)) return path;
        String name = path.getFileName().toString();
        int dot = name.toLowerCase(Locale.ROOT).endsWith(".jar") ? name.length() - 4 : name.length();
        for (int i = 2; ; i++) {
            Path candidate = path.resolveSibling(name.substring(0, dot) + "-" + i + name.substring(dot));
            if (!Files.exists(candidate)) return candidate;
        }
    }

    /** No ATOMIC_MOVE: on Windows it may replace an existing target; a plain move refuses to and is still a rename here. */
    private static void move(Path source, Path target) throws IOException { Files.move(source, target); }

    private static void appendJournal(Path journal, List<Map<String, String>> entries) throws IOException {
        Files.createDirectories(journal.getParent());
        var gson = new GsonBuilder().disableHtmlEscaping().create();
        StringBuilder lines = new StringBuilder();
        for (var entry : entries) lines.append(gson.toJson(entry)).append('\n');
        Files.writeString(journal, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
