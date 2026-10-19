package github.com.gengyoubo.mfcl.resolver;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import java.util.*;

public final class LegacyDependencies {
    private final Project project;
    private final TaskProvider<ResolveLegacyDependencies> task;
    private final Set<String> ids = new HashSet<>();
    LegacyDependencies(Project project, TaskProvider<ResolveLegacyDependencies> task) { this.project = project; this.task = task; }
    public void artifact(String id, String coordinate, String sha256, String origin, String license) {
        // Classifiers and @jar are handled by Gradle, not by an embedded Maven client.
        if (coordinate.contains("+") || coordinate.contains("[") || coordinate.contains("(") || coordinate.contains("latest."))
            throw new IllegalArgumentException("Use an exact legacy dependency version: " + coordinate);
        var input = input(id, coordinate, sha256, origin, license);
        var configuration = project.getConfigurations().detachedConfiguration(project.getDependencies().create(coordinate));
        configuration.setTransitive(false);
        input.getFiles().from(configuration);
        task.configure(value -> value.getArtifacts().add(input));
    }
    public void local(String id, Object file, String sha256, String origin, String license) {
        var input = input(id, "local:" + id, sha256, origin, license);
        input.getFiles().from(project.file(file));
        task.configure(value -> value.getArtifacts().add(input));
    }
    private LegacyArtifact input(String id, String requested, String sha256, String origin, String license) {
        if (!ids.add(id)) throw new IllegalArgumentException("Duplicate legacy dependency id: " + id);
        return project.getObjects().newInstance(LegacyArtifact.class, id, requested, sha256, origin, license);
    }
}
