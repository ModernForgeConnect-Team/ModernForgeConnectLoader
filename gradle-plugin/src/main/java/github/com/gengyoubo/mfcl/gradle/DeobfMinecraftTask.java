package github.com.gengyoubo.mfcl.gradle;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

/** Copies the prepared SRG runtime with MCP member names for the direct source set compile classpath. */
public abstract class DeobfMinecraftTask extends DefaultTask {
    private final ConfigurableFileCollection mappings;

    @Inject
    public DeobfMinecraftTask(ObjectFactory objects) {
        this.mappings = objects.fileCollection();
    }

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getSrgJar();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public ConfigurableFileCollection getMappings() {
        return mappings;
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void run() throws Exception {
        File runtime = runtime(getSrgJar().isPresent() ? getSrgJar().get().getAsFile() : null);
        MfclDirectTooling.deobf(runtime, getMappings().getSingleFile(), getOutput().get().getAsFile());
    }

    static File runtime(File srgJar) {
        if (srgJar == null || !srgJar.isFile())
            throw new GradleException("No prepared MFCL runtime at " + srgJar
                    + "; start the game once with MFCL installed, then point mfclDirect.runtime at <game>/mfcl/runtime/1.7.10");
        return srgJar;
    }
}
