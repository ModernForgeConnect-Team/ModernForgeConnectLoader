package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Startup preflight for the private Forge 1.7.10 runtime. Each problem becomes its own readable diagnostic instead of
 * surfacing later as a raw boot exception. Problems are errors only when legacy mods are present and need the runtime.
 */
public final class LegacyRuntimeCheck {
    public static final String SRG_JAR = "minecraft-forge-1.7.10-srg.jar";
    static final String MINECRAFT = "1.7.10", FORGE = "10.13.4.1614";
    private static final int SUMMARY_LIMIT = 5;

    private final Path gameDirectory, runtimeDirectory;
    private final boolean required;
    private final List<LegacyDiagnostic> problems = new ArrayList<>();

    private LegacyRuntimeCheck(Path gameDirectory, Path runtimeDirectory, boolean required) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.runtimeDirectory = runtimeDirectory.toAbsolutePath().normalize();
        this.required = required;
    }

    /**
     * @param archives discovered legacy archives; when empty the same checks run but are reported as notices only.
     * @return diagnostics in report order; when any is an error, a summary error with the fix comes first.
     */
    public static List<LegacyDiagnostic> run(Path gameDirectory, Path runtimeDirectory, List<LegacyArchive> archives) {
        return run(gameDirectory, runtimeDirectory, archives, null);
    }

    /** @param autoPrepare why automatic preparation did not produce a runtime this startup, or null when it was not attempted. */
    public static List<LegacyDiagnostic> run(Path gameDirectory, Path runtimeDirectory, List<LegacyArchive> archives, String autoPrepare) {
        var check = new LegacyRuntimeCheck(gameDirectory, runtimeDirectory, !archives.isEmpty());
        check.verify();
        if (check.problems.stream().noneMatch(LegacyDiagnostic::error)) return List.copyOf(check.problems);
        var result = new ArrayList<LegacyDiagnostic>();
        result.add(LegacyDiagnostic.error("RUNTIME_CHECK_FAILED", check.summary(archives, autoPrepare)));
        result.addAll(check.problems);
        return List.copyOf(result);
    }

    /** The problems that would make the runtime unusable for legacy mods; empty when it passes. */
    public static List<LegacyDiagnostic> errors(Path runtimeDirectory) {
        var check = new LegacyRuntimeCheck(runtimeDirectory, runtimeDirectory, true);
        check.verify();
        return check.problems.stream().filter(LegacyDiagnostic::error).toList();
    }

    private void problem(String code, String message) {
        problems.add(required ? LegacyDiagnostic.error(code, message) : LegacyDiagnostic.notice(code, message));
    }

    private void verify() {
        if (!Files.isDirectory(runtimeDirectory)) {
            problem("RUNTIME_DIRECTORY_MISSING", "Legacy runtime directory does not exist: " + runtimeDirectory);
            return;
        }
        Path manifestFile = runtimeDirectory.resolve("runtime-manifest.json");
        if (!Files.isRegularFile(manifestFile)) {
            // Unprepared development folders are still accepted by the runtime as "every JAR", but nothing is verified.
            if (!Files.isRegularFile(runtimeDirectory.resolve(SRG_JAR))) problem("RUNTIME_INPUT_MISSING", "Missing runtime input " + SRG_JAR);
            problems.add(LegacyDiagnostic.notice("RUNTIME_MANIFEST_MISSING", manifestFile
                    + " is missing; runtime JARs cannot be verified. Re-run tools/prepare-legacy-runtime.ps1 to create it."));
            return;
        }
        JsonObject manifest;
        try { manifest = JsonParser.parseString(Files.readString(manifestFile, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (IOException | RuntimeException failure) {
            problem("RUNTIME_MANIFEST_INVALID", "Cannot read " + manifestFile + ": " + failure.getMessage());
            return;
        }
        String minecraft = string(manifest.get("minecraft")), forge = string(manifest.get("forge"));
        if (!MINECRAFT.equals(minecraft) || !FORGE.equals(forge)) problem("RUNTIME_VERSION_MISMATCH", "runtime-manifest.json describes Minecraft "
                + minecraft + " / Forge " + forge + "; MFCL needs Minecraft " + MINECRAFT + " / Forge " + FORGE);
        var inputs = files(manifest, "inputs", ".jar");
        if (!inputs.contains(SRG_JAR)) problem("RUNTIME_INPUT_MISSING", "runtime-manifest.json does not list " + SRG_JAR);
        files(manifest, "resources", ".zip");
    }

    /** Verifies one manifest section and returns the listed names. */
    private List<String> files(JsonObject manifest, String section, String extension) {
        var names = new HashSet<String>();
        var listed = new ArrayList<String>();
        JsonElement element = manifest.get(section);
        if (element == null && section.equals("resources")) return listed;
        if (!(element instanceof JsonArray entries)) {
            problem("RUNTIME_MANIFEST_INVALID", "runtime-manifest.json has no \"" + section + "\" list");
            return listed;
        }
        String kind = section.equals("inputs") ? "input" : "resource";
        for (var entry : entries) {
            String name = entry.isJsonObject() ? string(entry.getAsJsonObject().get("file")) : null;
            String hash = entry.isJsonObject() ? string(entry.getAsJsonObject().get("sha256")) : null;
            if (name == null || hash == null || !Path.of(name).getFileName().toString().equals(name) || !name.endsWith(extension) || !names.add(name)) {
                problem("RUNTIME_MANIFEST_INVALID", "Invalid runtime " + kind + " entry: " + entry);
                continue;
            }
            listed.add(name);
            Path file = runtimeDirectory.resolve(name);
            if (!Files.isRegularFile(file)) {
                problem("RUNTIME_" + kind.toUpperCase(java.util.Locale.ROOT) + "_MISSING", "Missing runtime " + kind + " " + name);
                continue;
            }
            try {
                if (!LegacyDiscovery.sha256(file).equalsIgnoreCase(hash)) problem("RUNTIME_CHECKSUM_MISMATCH", "Checksum differs for runtime " + kind + " " + name);
            } catch (IOException failure) {
                problem("RUNTIME_CHECKSUM_MISMATCH", "Cannot read runtime " + kind + " " + name + ": " + failure.getMessage());
            }
        }
        return listed;
    }

    private String summary(List<LegacyArchive> archives, String autoPrepare) {
        var errors = problems.stream().filter(LegacyDiagnostic::error).map(LegacyDiagnostic::message).toList();
        String listed = errors.stream().limit(SUMMARY_LIMIT).collect(Collectors.joining("; "))
                + (errors.size() > SUMMARY_LIMIT ? "; and " + (errors.size() - SUMMARY_LIMIT) + " more" : "");
        String mods = archives.stream().map(archive -> Path.of(archive.file()).getFileName().toString()).collect(Collectors.joining(", "));
        return "The private Forge 1.7.10 runtime in " + runtimeDirectory + " is not usable, so " + archives.size()
                + " legacy archive(s) were skipped (" + mods + "). Problems: " + listed
                + ". MFCL downloads and prepares this runtime automatically on startup from the official Mojang, Forge and Maven Central servers"
                + (autoPrepare == null ? "" : " (" + autoPrepare + ")")
                + ". Fix: restart the game with internet access, or from an MFCL source checkout run tools/prepare-legacy-runtime.ps1 -GameDirectory '"
                + gameDirectory + "' -Boot, or copy a prepared run/mfcl/runtime/1.7.10 folder to " + runtimeDirectory + ".";
    }

    private static String string(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
