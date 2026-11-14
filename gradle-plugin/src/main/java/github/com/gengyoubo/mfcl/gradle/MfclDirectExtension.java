package github.com.gengyoubo.mfcl.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * DSL for the {@code mfcl.direct} plugin. Every value is lazy so a build script may refer to {@code sourceSets} and
 * other providers without forcing them during configuration.
 */
public class MfclDirectExtension {
    public static final String DEFAULT_MAPPINGS = "de.oceanlabs.mcp:mcp_stable:12-1.7.10@zip";
    public static final String DEFAULT_SOURCE_SET = "direct";

    private final Project project;
    private final Property<String> sourceSet;
    private final DirectoryProperty runtime;
    private final ConfigurableFileCollection mods;
    private final ConfigurableFileCollection host;
    private final Property<String> mappings;
    private final Property<Boolean> packaged;
    private final ListProperty<String> names;

    public MfclDirectExtension(Project project, ObjectFactory objects) {
        this.project = project;
        this.sourceSet = objects.property(String.class).convention(DEFAULT_SOURCE_SET);
        this.runtime = objects.directoryProperty();
        this.mods = objects.fileCollection();
        this.host = objects.fileCollection();
        this.mappings = objects.property(String.class).convention(DEFAULT_MAPPINGS);
        this.packaged = objects.property(Boolean.class).convention(true);
        this.names = objects.listProperty(String.class).convention(List.of("main"));
    }

    public Property<String> getSourceSet() {
        return sourceSet;
    }

    public DirectoryProperty getRuntime() {
        return runtime;
    }

    public ConfigurableFileCollection getMods() {
        return mods;
    }

    public ConfigurableFileCollection getHost() {
        return host;
    }

    public Property<String> getMappings() {
        return mappings;
    }

    public Property<Boolean> getPackaged() {
        return packaged;
    }

    public ListProperty<String> getNames() {
        return names;
    }

    public void setSourceSet(String value) {
        sourceSet.set(value);
    }

    public void setRuntime(Object value) {
        runtime.set(project.getLayout().dir(project.provider(() -> project.file(value))));
    }

    public void setMods(Object value) {
        mods.setFrom(value);
    }

    public void setHost(Object value) {
        host.setFrom(value);
    }

    public void setMappings(String value) {
        mappings.set(value);
    }

    public void setPackaged(boolean value) {
        packaged.set(value);
    }

    public void setNames(Iterable<?> value) {
        List<String> result = new ArrayList<>();
        for (Object entry : value) result.add(String.valueOf(entry));
        names.set(result);
    }
}
