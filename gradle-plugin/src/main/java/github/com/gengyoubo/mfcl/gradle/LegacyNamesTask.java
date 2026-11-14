package github.com.gengyoubo.mfcl.gradle;

import java.io.File;
import org.gradle.api.DefaultTask;
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

/** Member-less stubs of the original classes, for class literals in host MLA contracts. */
public abstract class LegacyNamesTask extends DefaultTask {
    private final ConfigurableFileCollection mods;

    @Inject
    public LegacyNamesTask(ObjectFactory objects) {
        this.mods = objects.fileCollection();
    }

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getSrgJar();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public ConfigurableFileCollection getMods() {
        return mods;
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void run() throws Exception {
        File srgJar = DeobfMinecraftTask.runtime(getSrgJar().isPresent() ? getSrgJar().get().getAsFile() : null);
        MfclDirectTooling.legacyNames(srgJar, getMods().getFiles(), getOutput().get().getAsFile());
    }
}
