package github.com.gengyoubo.mfcl.resolver;

import org.gradle.api.*;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;

@DisableCachingByDefault(because = "Local imports and provenance should be verified on the requesting machine")
public abstract class ResolveLegacyDependencies extends DefaultTask {
    private final List<LegacyArtifact> artifacts = new ArrayList<>();
    @Nested public List<LegacyArtifact> getArtifacts() { return artifacts; }
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    private record Verified(LegacyArtifact artifact, byte[] bytes) { }
    @TaskAction public void resolve() throws Exception {
        var verified = new ArrayList<Verified>();
        // Verify all inputs before changing any previously accepted output/manifest.
        for (var artifact : artifacts.stream().sorted(Comparator.comparing(LegacyArtifact::getId)).toList()) {
            var files = artifact.getFiles().getFiles();
            if (files.size() != 1) throw new GradleException("Expected one JAR for " + artifact.getId() + ", found " + files.size());
            Path file = files.iterator().next().toPath();
            byte[] bytes = Files.readAllBytes(file);
            if (!hash(bytes).equals(artifact.getSha256())) throw new GradleException("SHA-256 differs for " + artifact.getId());
            try (var jar = new JarFile(file.toFile())) { if (!jar.entries().hasMoreElements()) throw new GradleException("Empty JAR: " + artifact.getId()); }
            verified.add(new Verified(artifact, bytes));
        }
        Path output = getOutputDirectory().get().getAsFile().toPath(); Files.createDirectories(output);
        try (var channel = FileChannel.open(output.resolve(".resolver.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            for (var value : verified) {
                Path file = output.resolve(value.artifact.getSha256() + ".jar");
                if (Files.exists(file) && !hash(Files.readAllBytes(file)).equals(value.artifact.getSha256()))
                    throw new GradleException("Previously imported JAR is corrupt: " + file);
            }
            var entries = new ArrayList<String>();
            for (var value : verified) {
                var artifact = value.artifact; String filename = artifact.getSha256() + ".jar";
                Path file = output.resolve(filename);
                if (!Files.exists(file)) atomicWrite(file, value.bytes);
                entries.add("    {\"id\":" + json(artifact.getId()) + ",\"requested\":" + json(artifact.getRequested())
                        + ",\"sha256\":" + json(artifact.getSha256()) + ",\"origin\":" + json(artifact.getOrigin())
                        + ",\"license\":" + json(artifact.getLicense()) + ",\"file\":" + json(filename) + "}");
            }
            String manifest = "{\n  \"format\":1,\n  \"artifacts\":[\n" + String.join(",\n", entries) + "\n  ]\n}\n";
            atomicWrite(output.resolve("legacy-dependencies.json"), manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        getLogger().lifecycle("MFCL verified {} explicitly declared legacy dependencies in {}", verified.size(), output);
    }
    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".import-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    static String hash(byte[] bytes) throws NoSuchAlgorithmException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String json(String value) {
        var result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '\\' || c == '"') result.append('\\').append(c);
            else if (c < 32) result.append(String.format("\\u%04x", (int)c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
