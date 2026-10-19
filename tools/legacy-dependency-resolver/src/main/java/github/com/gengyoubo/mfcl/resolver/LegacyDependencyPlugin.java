package github.com.gengyoubo.mfcl.resolver;

import org.gradle.api.*;

public final class LegacyDependencyPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        var task = project.getTasks().register("resolveLegacyDependencies", ResolveLegacyDependencies.class, value -> {
            value.setGroup("mfcl"); value.setDescription("Resolve explicitly declared old JARs and verify their provenance/hash lock.");
            value.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("mfcl/legacy-dependencies"));
        });
        project.getExtensions().add("legacyDependencies", new LegacyDependencies(project, task));
    }
}
