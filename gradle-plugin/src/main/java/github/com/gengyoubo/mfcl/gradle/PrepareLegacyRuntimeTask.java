package github.com.gengyoubo.mfcl.gradle;

import github.com.gengyoubo.mfcl.legacy.runtime.RuntimeInstaller;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import java.io.IOException;

/** Runs the same verified installer used by MFCL in game, only when the prepared JAR is absent. */
@DisableCachingByDefault(because = "Downloads and prepares a private game runtime in the selected instance")
public abstract class PrepareLegacyRuntimeTask extends DefaultTask {
    @OutputFile public abstract RegularFileProperty getSrgJar();
    @Input public abstract Property<Boolean> getOffline();

    @TaskAction public void prepare() throws IOException {
        var jar = getSrgJar().get().getAsFile().toPath();
        if (java.nio.file.Files.isRegularFile(jar)) return;
        if (getOffline().get()) throw new GradleException("MFCL 1.7.10 runtime is missing at " + jar
                + "; run again without --offline to download and prepare it, or configure mfclDirect.runtime");
        // Gradle may create the output's parent before this task starts. Do not make the installer back up that empty
        // directory as if it were an existing runtime. Non-empty directories are preserved by the installer.
        if (java.nio.file.Files.isDirectory(jar.getParent())) {
            try (var entries = java.nio.file.Files.newDirectoryStream(jar.getParent())) {
                if (!entries.iterator().hasNext()) {
                    try { java.nio.file.Files.deleteIfExists(jar.getParent()); }
                    catch (java.nio.file.DirectoryNotEmptyException concurrentWrite) { /* Preserve concurrent data. */ }
                }
            }
        }
        getLogger().lifecycle("MFCL: Preparing the private 1.7.10 runtime at {} (first run downloads official files)", jar.getParent());
        new RuntimeInstaller(jar.getParent(), message -> getLogger().lifecycle("MFCL: {}", message)).install(false);
        if (!java.nio.file.Files.isRegularFile(jar)) throw new GradleException("MFCL runtime preparation did not produce " + jar);
    }
}
