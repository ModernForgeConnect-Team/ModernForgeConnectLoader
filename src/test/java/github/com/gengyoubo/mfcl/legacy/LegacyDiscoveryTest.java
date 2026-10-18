package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDiscoveryTest {
    @TempDir Path temporary;

    @Test void readsOnlyDirectJarsInTheVersionFolder() throws Exception {
        var paths = new LegacyPaths(temporary);
        TestArchives.jar(temporary.resolve("mods/modern.jar"), Map.of(), Map.of("META-INF/mods.toml", TestArchives.text("modern")));
        TestArchives.jar(paths.modDirectory().resolve("one.jar"), Map.of(), Map.of("test/One.class", TestArchives.classBytes("test.One", "one")));
        TestArchives.jar(paths.modDirectory().resolve("nested/ignored.jar"), Map.of(), Map.of("broken.class", new byte[]{0}));
        var result = new LegacyDiscovery().scan(paths);
        assertEquals(1, result.archives().size());
        assertEquals("one", result.archives().get(0).mods().get(0).id());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test void createsAnEmptyVersionFolder() throws Exception {
        var paths = new LegacyPaths(temporary);
        assertTrue(new LegacyDiscovery().scan(paths).archives().isEmpty());
        assertTrue(Files.isDirectory(paths.modDirectory()));
    }

    @Test void inspectsLegacyAnnotationsWithoutExecutingStaticInitializers() throws Exception {
        System.clearProperty("mfcl.test.scanExecuted");
        Path jar = TestArchives.jar(temporary.resolve("passive.jar"), Map.of(),
                Map.of("test/Passive.class", TestArchives.classBytes("test.Passive", "UpperCaseLegacyId")));
        String hash = LegacyDiscovery.sha256(jar);
        var archive = new LegacyDiscovery().inspect(jar);
        assertEquals("UpperCaseLegacyId", archive.mods().get(0).id());
        assertEquals("test.Passive", archive.mods().get(0).entryClass());
        assertNull(System.getProperty("mfcl.test.scanExecuted"));
        assertEquals(hash, LegacyDiscovery.sha256(jar));
    }

    @Test void understandsCustomMetadataAndLiteralNewlinesUsedByOldFml() throws Exception {
        Path jar = TestArchives.jar(temporary.resolve("custom.jar"), Map.of(), Map.of("neimod.info", TestArchives.text(
                "[{\"modid\":\"NotEnoughItems\",\"version\":\"1.0.5.120\",\"description\":\"line one\nline two\",\"mcversion\":\"1.7.10\"}]")));
        var archive = new LegacyDiscovery().inspect(jar);
        assertEquals("NotEnoughItems", archive.mods().get(0).id());
        assertEquals("neimod.info", archive.mods().get(0).metadataResource());
    }

    @Test void rejectsMissingCorePluginAndAccessTransformerResources() throws Exception {
        Path missingPlugin = TestArchives.jar(temporary.resolve("plugin.jar"), Map.of("FMLCorePlugin", "missing.Plugin"), Map.of());
        assertThrows(java.io.IOException.class, () -> new LegacyDiscovery().inspect(missingPlugin));
        Path missingAt = TestArchives.jar(temporary.resolve("at.jar"), Map.of("FMLAT", "missing.cfg"), Map.of());
        assertThrows(java.io.IOException.class, () -> new LegacyDiscovery().inspect(missingAt));
    }

    @Test void recordsMalformedJarWithoutHidingItAsAnEmptyDirectory() throws Exception {
        var paths = new LegacyPaths(temporary);
        Files.createDirectories(paths.modDirectory());
        Files.writeString(paths.modDirectory().resolve("broken.jar"), "not a zip");
        var report = new LegacyRuntimeService().inspect(temporary);
        assertEquals(LegacyRuntimeService.State.DEPENDENCY_ERRORS, report.state());
        assertTrue(report.diagnostics().stream().anyMatch(d -> d.code().equals("INVALID_ARCHIVE")));
        assertTrue(Files.exists(paths.reportFile()));
    }

    @Test void rejectsModernDescriptorsInsideTheLegacyFolder() throws Exception {
        Path jar = TestArchives.jar(temporary.resolve("modern.jar"), Map.of(), Map.of("META-INF/mods.toml", TestArchives.text("modern")));
        assertThrows(java.io.IOException.class, () -> new LegacyDiscovery().inspect(jar));
    }

    @Test void discoversCoremodMetadataFactoriesWithoutRunningThem() throws Exception {
        var plugin = new org.objectweb.asm.ClassWriter(0);
        plugin.visit(52, 1, "test/Plugin", null, "java/lang/Object", null);
        var getter = plugin.visitMethod(1, "getModContainerClass", "()Ljava/lang/String;", null, null);
        getter.visitCode(); getter.visitLdcInsn("test.Container"); getter.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        getter.visitMaxs(1, 1); getter.visitEnd(); plugin.visitEnd();
        var container = new org.objectweb.asm.ClassWriter(0);
        container.visit(52, 1, "test/Container", null, "cpw/mods/fml/common/DummyModContainer", null);
        var metadata = container.visitMethod(10, "metadata", "()Lcpw/mods/fml/common/ModMetadata;", null, null);
        metadata.visitCode(); metadata.visitTypeInsn(org.objectweb.asm.Opcodes.NEW, "cpw/mods/fml/common/ModMetadata");
        metadata.visitInsn(org.objectweb.asm.Opcodes.DUP);
        metadata.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "cpw/mods/fml/common/ModMetadata", "<init>", "()V", false);
        metadata.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, 0);
        for (var pair : Map.of("modId", "Example|Core", "name", "Example Core", "version", "1.2.3").entrySet()) {
            metadata.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0); metadata.visitLdcInsn(pair.getValue());
            metadata.visitFieldInsn(org.objectweb.asm.Opcodes.PUTFIELD, "cpw/mods/fml/common/ModMetadata", pair.getKey(), "Ljava/lang/String;");
        }
        metadata.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0); metadata.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        metadata.visitMaxs(2, 1); metadata.visitEnd(); container.visitEnd();
        Path jar = TestArchives.jar(temporary.resolve("core.jar"), Map.of("FMLCorePlugin", "test.Plugin"),
                Map.of("test/Plugin.class", plugin.toByteArray(), "test/Container.class", container.toByteArray()));
        var archive = new LegacyDiscovery().inspect(jar);
        assertEquals("test.Container", archive.coreContainer());
        assertEquals("Example|Core", archive.mods().get(0).id());
        assertEquals("1.2.3", archive.mods().get(0).version());
        assertNull(System.getProperty("mfcl.test.scanExecuted"));
    }
}
