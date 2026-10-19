package github.com.gengyoubo.mfcl.resolver;

import org.gradle.testkit.runner.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.jar.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class ResolverTest {
    @TempDir Path project;
    private byte[] jar() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) { var entry = new JarEntry("example.txt"); entry.setTime(0); jar.putNextEntry(entry); jar.write("legacy fixture".getBytes()); jar.closeEntry(); }
        return bytes.toByteArray();
    }
    private GradleRunner runner() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'resolver-fixture'\n");
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withGradleInstallation(Path.of(System.getProperty("mfcl.gradleHome")).toFile())
                .withTestKitDir(project.resolve("test-kit").toFile())
                .withArguments("resolveLegacyDependencies", "--offline", "--stacktrace");
    }
    private void build(String declarations) throws Exception {
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'github.com.gengyoubo.mfcl.legacy-dependencies' }\n" + declarations);
    }
    @Test void localImportIsPinnedRepeatableAndDoesNotChangeOriginal() throws Exception {
        byte[] original = jar(); Files.write(project.resolve("old.jar"), original); String hash = ResolveLegacyDependencies.hash(original);
        build("legacyDependencies { local('old', 'old.jar', '" + hash + "', 'author site', 'local use only') }\n");
        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":resolveLegacyDependencies").getOutcome());
        Path output = project.resolve("build/mfcl/legacy-dependencies"); String manifest = Files.readString(output.resolve("legacy-dependencies.json"));
        assertTrue(manifest.contains(hash)); assertTrue(manifest.contains("author site")); assertTrue(manifest.contains("local use only"));
        assertArrayEquals(original, Files.readAllBytes(output.resolve(hash + ".jar")));
        assertEquals(TaskOutcome.UP_TO_DATE, runner().build().task(":resolveLegacyDependencies").getOutcome());
        assertArrayEquals(original, Files.readAllBytes(project.resolve("old.jar")));
        build("legacyDependencies { local('old', 'old.jar', '" + "0".repeat(64) + "', 'author site', 'local use only') }\n");
        assertTrue(runner().buildAndFail().getOutput().contains("SHA-256 differs"));
        assertEquals(manifest, Files.readString(output.resolve("legacy-dependencies.json")));
    }
    @Test void mavenResolutionUsesOnlyConfiguredRepositoryAndDoesNotFetchTransitively() throws Exception {
        byte[] original = jar(); String hash = ResolveLegacyDependencies.hash(original);
        Path directory = project.resolve("repository/example/old/1.0"); Files.createDirectories(directory);
        Files.write(directory.resolve("old-1.0.jar"), original);
        Files.writeString(directory.resolve("old-1.0.pom"), "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>old</artifactId><version>1.0</version><dependencies><dependency><groupId>missing</groupId><artifactId>never-download</artifactId><version>1</version></dependency></dependencies></project>");
        build("repositories { maven { url = uri('repository') } }\nlegacyDependencies { artifact('old', 'example:old:1.0', '" + hash + "', 'local Maven fixture', 'fixture license') }\n");
        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":resolveLegacyDependencies").getOutcome());
        assertTrue(Files.readString(project.resolve("build/mfcl/legacy-dependencies/legacy-dependencies.json")).contains("example:old:1.0"));
    }
    @Test void invalidInputsCannotPublishOrReplaceAcceptedArtifacts() throws Exception {
        byte[] original = jar(); String hash = ResolveLegacyDependencies.hash(original); Files.write(project.resolve("old.jar"), original);
        build("legacyDependencies { local('same', 'old.jar', '" + hash + "', 'source', 'license'); local('same', 'old.jar', '" + hash + "', 'source', 'license') }\n");
        assertTrue(runner().buildAndFail().getOutput().contains("Duplicate legacy dependency id"));
        build("legacyDependencies { artifact('old', 'example:old:1.+', '" + hash + "', 'source', 'license') }\n");
        assertTrue(runner().buildAndFail().getOutput().contains("exact legacy dependency version"));
        Files.writeString(project.resolve("invalid.jar"), "not a jar"); String invalidHash = ResolveLegacyDependencies.hash(Files.readAllBytes(project.resolve("invalid.jar")));
        build("legacyDependencies { local('invalid', 'invalid.jar', '" + invalidHash + "', 'source', 'license') }\n");
        runner().buildAndFail(); assertFalse(Files.exists(project.resolve("build/mfcl/legacy-dependencies/legacy-dependencies.json")));
    }
}
