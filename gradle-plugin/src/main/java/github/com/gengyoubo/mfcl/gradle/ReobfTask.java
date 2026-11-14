package github.com.gengyoubo.mfcl.gradle;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

/** Renames MCP member references and overrides in the direct classes back to the original SRG names. */
public abstract class ReobfTask extends DefaultTask {
    private final ConfigurableFileCollection classes;
    private final ConfigurableFileCollection mappings;
    private final ConfigurableFileCollection mods;

    @Inject
    public ReobfTask(ObjectFactory objects) {
        this.classes = objects.fileCollection();
        this.mappings = objects.fileCollection();
        this.mods = objects.fileCollection();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getClasses() {
        return classes;
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

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public ConfigurableFileCollection getMods() {
        return mods;
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @Input
    public abstract Property<String> getSourceSetName();

    @Input
    public abstract Property<String> getMappingsCoordinate();

    @Input
    public abstract Property<String> getPluginVersion();

    @TaskAction
    public void run() throws Exception {
        File srgJar = DeobfMinecraftTask.runtime(getSrgJar().isPresent() ? getSrgJar().get().getAsFile() : null);
        File output = getOutput().get().getAsFile();
        MfclDirectTooling.reobf(getClasses().getFiles(), srgJar, getMappings().getSingleFile(), getMods().getFiles(), output);
        MfclDirectTooling.writeManifest(output, getPluginVersion().get(), getSourceSetName().get(),
                getMappingsCoordinate().get(), MfclDirectTooling.sha256(srgJar));
    }
}
