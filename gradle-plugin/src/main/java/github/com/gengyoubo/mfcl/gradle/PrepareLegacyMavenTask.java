package github.com.gengyoubo.mfcl.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import javax.inject.Inject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Stages resolved artifacts for the legacy runtime; they never enter the modern runtime classpath. */
@DisableCachingByDefault(because = "Writes a verified local game instance manifest")
public abstract class PrepareLegacyMavenTask extends DefaultTask {
    private final ConfigurableFileCollection artifacts;
    @Inject public PrepareLegacyMavenTask(ObjectFactory objects) { artifacts = objects.fileCollection(); }
    @InputFiles @PathSensitive(PathSensitivity.NONE) public ConfigurableFileCollection getArtifacts() { return artifacts; }
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();

    @TaskAction public void prepare() throws Exception {
        Map<String, File> inputs = new TreeMap<>();
        for (File jar : artifacts.getFiles()) {
            if (!jar.isFile() || !jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
                throw new GradleException("Legacy Maven artifact is not a JAR: " + jar);
            try (JarFile archive = new JarFile(jar, false)) {
                if (!archive.entries().hasMoreElements()) throw new GradleException("Empty legacy Maven JAR: " + jar);
            }
            inputs.putIfAbsent(MfclDirectTooling.sha256(jar), jar);
        }
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(output);
        List<String> entries = new ArrayList<>();
        for (var entry : inputs.entrySet()) {
            String hash = entry.getKey(), filename = hash + ".jar";
            Path temporary = Files.createTempFile(output, ".artifact-", ".tmp");
            try {
                Files.copy(entry.getValue().toPath(), temporary, StandardCopyOption.REPLACE_EXISTING);
                if (!hash.equals(MfclDirectTooling.sha256(temporary.toFile()))) throw new GradleException("Legacy Maven JAR changed while copying: " + entry.getValue());
                Files.move(temporary, output.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            entries.add("    {\"sha256\":\"" + hash + "\",\"file\":\"" + filename + "\"}");
        }
        Path temporary = Files.createTempFile(output, ".manifest-", ".tmp");
        try {
            Files.writeString(temporary, "{\n  \"format\":1,\n  \"artifacts\":[\n" + String.join(",\n", entries) + "\n  ]\n}\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, output.resolve("legacy-dependencies.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, output.resolve("legacy-dependencies.json"), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
