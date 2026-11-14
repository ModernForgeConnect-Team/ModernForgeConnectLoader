package github.com.gengyoubo.mfcl.gradle;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MfclDirectPluginTest {
    @TempDir
    Path project;

    @Test
    void exposesDefaultsAndRegistersTasks() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'mfcl.direct' }

                mfclDirect {
                    runtime = file('runtime')
                }

                tasks.register('verify') {
                    doLast {
                        assert mfclDirect.sourceSet.get() == 'direct'
                        assert mfclDirect.mappings.get() == 'de.oceanlabs.mcp:mcp_stable:12-1.7.10@zip'
                        assert mfclDirect.packaged.get()
                        assert mfclDirect.names.get() == ['main']
                        assert tasks.findByName('directDeobfMinecraft') != null
                        assert tasks.findByName('directReobf') != null
                        assert tasks.findByName('directLegacyNames') != null
                    }
                }
                """);

        BuildResult result = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments("verify", "--stacktrace")
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }
}
