package github.com.gengyoubo.mfcl.gradle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthetic 1.7.10 runtime: the plugin must deobfuscate, compile a real direct class and reobfuscate it. */
class MfclDirectToolingTest {
    @TempDir
    Path project;

    @Test
    void deobfuscatesCompilesAndReobfuscates() throws Exception {
        Path runtime = Files.createDirectories(project.resolve("runtime"));
        writeOldJar(runtime.resolve("minecraft-forge-1.7.10-srg.jar"));
        writeMappings(project.resolve("repo"));

        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'mfcl.direct' }

                repositories {
                    maven { url = uri('repo') }
                }

                mfclDirect {
                    sourceSet = 'direct'
                    runtime = file('runtime')
                    mappings = 'test.mappings:mcp_stable:12-1.7.10@zip'
                    names = []
                }
                """);

        Path host = project.resolve("src/main/java/fixture/Api.java");
        Files.createDirectories(host.getParent());
        Files.writeString(host, "package fixture; public interface Api { String value(); }\n");

        Path direct = project.resolve("src/direct/java/fixture/Logic.java");
        Files.createDirectories(direct.getParent());
        Files.writeString(direct, "package fixture; public final class Logic implements Api {"
                + " public String value() { return new Old().name(); } }\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments("directReobf", "--offline", "--configuration-cache", "--stacktrace")
                .build();
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));

        byte[] compiled = Files.readAllBytes(project.resolve("build/mfcl-direct/direct/reobf/fixture/Logic.class"));
        assertTrue(new String(compiled, StandardCharsets.ISO_8859_1).contains("func_12345_a"));
        assertTrue(Files.isRegularFile(project.resolve("build/mfcl-direct/direct/reobf/manifest.json")));
    }

    @Test
    void hostSourceSetExposesLegacyForgeClassLiteralAsCompileOnlyDependency() throws Exception {
        Path runtime = Files.createDirectories(project.resolve("runtime"));
        writeOldJar(runtime.resolve("minecraft-forge-1.7.10-srg.jar"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'mfcl.direct' }
                mfclDirect { runtime = file('runtime'); packaged = false }
                tasks.register('verifyModel') {
                    doLast {
                        assert configurations.compileClasspath.files.any { it.name == 'legacy-names-1.7.10.jar' }
                        assert !configurations.runtimeClasspath.files.any { it.name == 'legacy-names-1.7.10.jar' }
                    }
                }
                """);
        Path source = project.resolve("src/main/java/fixture/Contract.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package fixture;
                import net.minecraftforge.common.util.ForgeDirection;
                @interface LegacyType { Class<?> type(); }
                @LegacyType(type = ForgeDirection.class) public interface Contract {}
                """);
        BuildResult result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("compileJava", "verifyModel", "--offline", "--stacktrace").build();
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        assertTrue(Files.isRegularFile(project.resolve("build/mfcl-direct/direct/legacy-names-1.7.10.jar")));
    }

    @Test
    void legacyMavenFeedsStubsDirectCompilationAndGameWithoutModernRuntimeLeak() throws Exception {
        Path runtime = Files.createDirectories(project.resolve("run/mfcl/runtime/1.7.10"));
        writeOldJar(runtime.resolve("minecraft-forge-1.7.10-srg.jar"));
        writeMappings(project.resolve("repo"));
        Path repository = Files.createDirectories(project.resolve("repo/test/legacy/1.0"));
        Files.writeString(repository.resolve("legacy-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                <groupId>test</groupId><artifactId>legacy</artifactId><version>1.0</version>
                <dependencies><dependency><groupId>missing</groupId><artifactId>transitive</artifactId><version>1.0</version></dependency></dependencies>
                </project>
                """);
        ClassWriter library = new ClassWriter(0);
        library.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/LegacyLibrary", null, "java/lang/Object", null);
        MethodVisitor method = library.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()Ljava/lang/String;", null, null);
        method.visitCode(); method.visitLdcInsn("legacy"); method.visitInsn(Opcodes.ARETURN); method.visitMaxs(1, 0); method.visitEnd();
        library.visitEnd();
        try (ZipOutputStream jar = new ZipOutputStream(new FileOutputStream(repository.resolve("legacy-1.0.jar").toFile()))) {
            jar.putNextEntry(new ZipEntry("fixture/LegacyLibrary.class")); jar.write(library.toByteArray()); jar.closeEntry();
        }
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'mfcl.direct' }
                repositories { maven { url = uri('repo') } }
                dependencies { legacyMaven 'test:legacy:1.0' }
                mfclDirect { mappings = 'test.mappings:mcp_stable:12-1.7.10@zip'; packaged = false }
                tasks.register('runClient')
                tasks.register('verifyIsolation') {
                    doLast {
                        assert !configurations.runtimeClasspath.files.any { it.name == 'legacy-1.0.jar' }
                        assert configurations.directCompileClasspath.files.any { it.name == 'legacy-1.0.jar' }
                    }
                }
                """);
        Path main = project.resolve("src/main/java/fixture/Api.java"); Files.createDirectories(main.getParent());
        Files.writeString(main, "package fixture; @interface LegacyType { Class<?> type(); } @LegacyType(type=LegacyLibrary.class) public interface Api { String value(); }\n");
        Path direct = project.resolve("src/direct/java/fixture/Logic.java"); Files.createDirectories(direct.getParent());
        Files.writeString(direct, "package fixture; public final class Logic implements Api { public String value(){return LegacyLibrary.value();} }\n");
        BuildResult result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("compileJava", "directReobf", "runClient", "verifyIsolation", "--offline", "--stacktrace").build();
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        Path manifest = project.resolve("run/mfcl/legacy-maven/1.7.10/legacy-dependencies.json");
        String hash = MfclDirectTooling.sha256(repository.resolve("legacy-1.0.jar").toFile());
        assertTrue(Files.readString(manifest).contains(hash));
        assertTrue(Files.isRegularFile(manifest.getParent().resolve(hash + ".jar")));
        try (var jar = new java.util.jar.JarFile(project.resolve("build/mfcl-direct/direct/legacy-names-1.7.10.jar").toFile())) {
            assertTrue(jar.getEntry("fixture/LegacyLibrary.class") != null);
            assertTrue(jar.getEntry("net/minecraftforge/common/util/ForgeDirection.class") != null);
        }
    }

    @Test
    void rootPluginSuppliesLegacyNamesToJavaSubprojects() throws Exception {
        Path runtime = Files.createDirectories(project.resolve("run/mfcl/runtime/1.7.10"));
        writeOldJar(runtime.resolve("minecraft-forge-1.7.10-srg.jar"));
        Path mod = project.resolve("old-mod.jar");
        ClassWriter library = new ClassWriter(0);
        library.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "oldmod/TileTube", null, "java/lang/Object", null);
        library.visitEnd();
        try (ZipOutputStream jar = new ZipOutputStream(new FileOutputStream(mod.toFile()))) {
            jar.putNextEntry(new ZipEntry("oldmod/TileTube.class")); jar.write(library.toByteArray()); jar.closeEntry();
        }
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'fixture'\ninclude 'addon'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'mfcl.direct' }
                dependencies { legacyMaven files('old-mod.jar') }
                mfclDirect { packaged = false }
                """);
        Path addon = Files.createDirectories(project.resolve("addon"));
        Files.writeString(addon.resolve("build.gradle"), """
                plugins { id 'java' }
                tasks.register('runClient')
                tasks.register('verifyIsolation') {
                    doLast { assert !configurations.runtimeClasspath.files.any { it.name.endsWith('.jar') } }
                }
                """);
        Path source = addon.resolve("src/main/java/fixture/Contract.java"); Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package fixture;
                import net.minecraftforge.common.util.ForgeDirection;
                import oldmod.TileTube;
                @interface LegacyType { Class<?> type(); }
                @LegacyType(type = ForgeDirection.class) interface Direction {}
                @LegacyType(type = TileTube.class) public interface Contract {}
                """);
        BuildResult result = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments(":addon:compileJava", ":addon:runClient", ":addon:verifyIsolation", "--offline", "--stacktrace").build();
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        assertTrue(Files.exists(project.resolve("build/mfcl-direct/direct/legacy-names-1.7.10.jar")));
        assertTrue(Files.readString(project.resolve("run/mfcl/legacy-maven/1.7.10/legacy-dependencies.json"))
                .contains(MfclDirectTooling.sha256(mod.toFile())));
    }

    private static void writeOldJar(Path target) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "fixture/Old", null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_12345_a", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn("legacy");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(target.toFile()))) {
            output.putNextEntry(new ZipEntry("fixture/Old.class"));
            output.write(writer.toByteArray());
            output.closeEntry();
            ClassWriter direction = new ClassWriter(0);
            direction.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                    "net/minecraftforge/common/util/ForgeDirection", null, "java/lang/Object", null);
            direction.visitEnd();
            output.putNextEntry(new ZipEntry("net/minecraftforge/common/util/ForgeDirection.class"));
            output.write(direction.toByteArray());
            output.closeEntry();
        }
    }

    private static void writeMappings(Path repository) throws IOException {
        Path version = Files.createDirectories(repository.resolve("test/mappings/mcp_stable/12-1.7.10"));
        Files.writeString(version.resolve("mcp_stable-12-1.7.10.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test.mappings</groupId>
                  <artifactId>mcp_stable</artifactId>
                  <version>12-1.7.10</version>
                  <packaging>zip</packaging>
                </project>
                """);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(
                version.resolve("mcp_stable-12-1.7.10.zip").toFile()))) {
            output.putNextEntry(new ZipEntry("fields.csv"));
            output.write("searge,name,side,desc\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("methods.csv"));
            output.write("searge,name,side,desc\nfunc_12345_a,name,2,\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
