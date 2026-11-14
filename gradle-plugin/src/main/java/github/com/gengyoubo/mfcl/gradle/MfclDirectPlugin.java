package github.com.gengyoubo.mfcl.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.language.jvm.tasks.ProcessResources;

/**
 * Ports {@code gradle/mfcl-direct.gradle} into a real plugin:
 * <pre>
 * plugins { id 'mfcl.direct' version '1.2.0' }
 * mfclDirect {
 *     runtime = file('run/mfcl/runtime/1.7.10')
 *     mods = fileTree('run/mods') { include 'Thaumcraft-*.jar' }
 * }
 * </pre>
 */
public final class MfclDirectPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "mfclDirect";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        MfclDirectExtension extension = new MfclDirectExtension(project, project.getObjects());
        project.getExtensions().add(EXTENSION_NAME, extension);

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        extension.getHost().from(sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME)
                .map(sourceSet -> sourceSet.getJava().getClassesDirectory()));

        project.getRepositories().maven(repository -> {
            repository.setName("MinecraftForge");
            repository.setUrl("https://maven.minecraftforge.net/");
            repository.content(content -> content.includeGroup("de.oceanlabs.mcp"));
        });

        // The source set name and most file collections are configured by the build script after apply.
        project.afterEvaluate(evaluated -> configure(evaluated, extension, sourceSets));
    }

    private void configure(Project project, MfclDirectExtension extension, SourceSetContainer sourceSets) {
        if (!extension.getRuntime().isPresent())
            throw new GradleException("mfclDirect.runtime is required: point it at <game>/mfcl/runtime/1.7.10 after starting the game once with MFCL");
        String setName = extension.getSourceSet().get();
        File runtime = extension.getRuntime().get().getAsFile();
        File srgJar = new File(runtime, "minecraft-forge-1.7.10-srg.jar");
        SourceSet sourceSet = sourceSets.maybeCreate(setName);

        Configuration mappings = project.getConfigurations().maybeCreate(setName + "McpMappings");
        mappings.setCanBeConsumed(false);
        mappings.setCanBeResolved(true);
        mappings.setTransitive(false);
        project.getDependencies().add(mappings.getName(), extension.getMappings().get());

        ConfigurableFileCollection libraries = project.files(project.fileTree(runtime, tree -> {
            tree.include("*.jar");
            tree.exclude(srgJar.getName());
        }));

        Provider<RegularFile> deobfOutput = project.getLayout().getBuildDirectory()
                .file("mfcl-direct/" + setName + "/minecraft-forge-1.7.10-mcp.jar");
        TaskProvider<DeobfMinecraftTask> deobf = project.getTasks().register(setName + "DeobfMinecraft", DeobfMinecraftTask.class, task -> {
            task.setGroup("mfcl direct");
            task.setDescription("Copy the prepared SRG runtime with MCP member names as the direct source set compile classpath.");
            task.getSrgJar().set(srgJar);
            task.getMappings().from(mappings);
            task.getOutput().set(deobfOutput);
        });

        // Original classes first: names that collide with host classes resolve to the originals, as at runtime.
        project.getDependencies().add(sourceSet.getCompileOnlyConfigurationName(), project.files(deobf.flatMap(DeobfMinecraftTask::getOutput)));
        project.getDependencies().add(sourceSet.getCompileOnlyConfigurationName(), libraries);
        project.getDependencies().add(sourceSet.getCompileOnlyConfigurationName(), extension.getMods());
        project.getDependencies().add(sourceSet.getCompileOnlyConfigurationName(), extension.getHost());
        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                .configure(task -> task.getOptions().getRelease().set(8));

        Provider<Directory> reobfOutput = project.getLayout().getBuildDirectory()
                .dir("mfcl-direct/" + setName + "/reobf");
        TaskProvider<ReobfTask> reobf = project.getTasks().register(setName + "Reobf", ReobfTask.class, task -> {
            task.setGroup("mfcl direct");
            task.setDescription("Rename MCP member references and overrides in the direct classes to the original SRG names.");
            task.getClasses().from(sourceSet.getOutput().getClassesDirs());
            task.getSrgJar().set(srgJar);
            task.getMappings().from(mappings);
            task.getMods().from(extension.getMods());
            task.getOutput().set(reobfOutput);
            task.getSourceSetName().set(setName);
            task.getMappingsCoordinate().set(extension.getMappings());
            task.getPluginVersion().set(version());
        });

        Provider<RegularFile> namesOutput = project.getLayout().getBuildDirectory()
                .file("mfcl-direct/" + setName + "/legacy-names-1.7.10.jar");
        TaskProvider<LegacyNamesTask> names = project.getTasks().register(setName + "LegacyNames", LegacyNamesTask.class, task -> {
            task.setGroup("mfcl direct");
            task.setDescription("Member-less stubs of the original classes, for class literals in host MLA contracts.");
            task.getSrgJar().set(srgJar);
            task.getMods().from(extension.getMods());
            task.getOutput().set(namesOutput);
        });

        for (String hostName : extension.getNames().get()) {
            SourceSet hostSet = sourceSets.findByName(hostName);
            if (hostSet == null)
                throw new GradleException("mfclDirect.names contains unknown source set '" + hostName + "'");
            project.getTasks().named(hostSet.getCompileJavaTaskName(), JavaCompile.class).configure(task ->
                    task.setClasspath(task.getClasspath().plus(project.files(names.flatMap(LegacyNamesTask::getOutput)))));
        }

        if (extension.getPackaged().get()) {
            project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources.class).configure(task ->
                    task.from(reobf.flatMap(ReobfTask::getOutput), spec -> spec.into("META-INF/mfcl/direct")));
        }
    }

    private static String version() {
        try (InputStream input = MfclDirectPlugin.class.getResourceAsStream("/mfcl-direct.properties")) {
            if (input == null) return "unknown";
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("version", "unknown");
        } catch (IOException failure) {
            return "unknown";
        }
    }
}
