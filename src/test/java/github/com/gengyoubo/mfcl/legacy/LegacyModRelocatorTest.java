package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacyModRelocatorTest {
    @TempDir Path temporary;

    private static Map<String, byte[]> legacyMod(String id, String mcversion) {
        return Map.of("test/" + id + ".class", TestArchives.classBytes("test." + id, id),
                "mcmod.info", TestArchives.text("[{\"modid\":\"" + id + "\",\"mcversion\":\"" + mcversion + "\"}]"));
    }

    @Test void movesOriginal1710ModsOutOfTheModernRootUnchanged() throws Exception {
        var paths = new LegacyPaths(temporary);
        Path root = temporary.resolve("mods");
        Path legacy = TestArchives.jar(root.resolve("Legacy.jar"), Map.of(), legacyMod("Legacy", "1.7.10"));
        String hash = LegacyDiscovery.sha256(legacy);
        TestArchives.jar(root.resolve("modern.jar"), Map.of(), Map.of("META-INF/mods.toml", TestArchives.text("modern")));
        TestArchives.jar(root.resolve("library.jar"), Map.of(), Map.of("lib/Util.class", TestArchives.classBytes("lib.Util", null)));

        var diagnostics = new LegacyModRelocator().relocate(paths);

        assertFalse(Files.exists(legacy));
        Path moved = paths.modDirectory().resolve("Legacy.jar");
        assertEquals(hash, LegacyDiscovery.sha256(moved));
        assertTrue(Files.exists(root.resolve("modern.jar")));
        assertTrue(Files.exists(root.resolve("library.jar")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("LEGACY_MOD_RELOCATED") && !d.error()));
        assertTrue(Files.readString(temporary.resolve("logs/mfcl/legacy-relocations.jsonl")).contains(hash));
        assertEquals(1, new LegacyDiscovery().scan(paths).archives().size());
    }

    @Test void acceptsTheVersionInTheFileNameWhenMetadataOmitsIt() throws Exception {
        var paths = new LegacyPaths(temporary);
        TestArchives.jar(temporary.resolve("mods/Thing-1.7.10-1.0.jar"), Map.of(),
                Map.of("test/Thing.class", TestArchives.classBytes("test.Thing", "thing")));
        new LegacyModRelocator().relocate(paths);
        assertTrue(Files.exists(paths.modDirectory().resolve("Thing-1.7.10-1.0.jar")));
    }

    @Test void leavesOlderFmlModsInPlace() throws Exception {
        var paths = new LegacyPaths(temporary);
        Path old = TestArchives.jar(temporary.resolve("mods/Old.jar"), Map.of(), legacyMod("Old", "1.6.4"));
        var diagnostics = new LegacyModRelocator().relocate(paths);
        assertTrue(Files.exists(old));
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("LEGACY_RELOCATION_SKIPPED")));
    }

    private static Path onlyBackup(Path game) throws Exception {
        try (var stamps = Files.list(game.resolve("mfcl/legacy-replaced"))) {
            Path stamp = stamps.findFirst().orElseThrow();
            try (var files = Files.list(stamp)) { return files.findFirst().orElseThrow(); }
        }
    }

    @Test void replacesTheSameFileNameAndKeepsTheOldCopy() throws Exception {
        var paths = new LegacyPaths(temporary);
        Path existing = TestArchives.jar(paths.modDirectory().resolve("Legacy.jar"), Map.of(), legacyMod("Legacy", "1.7.10"));
        String oldHash = LegacyDiscovery.sha256(existing);
        var update = legacyMod("Legacy", "1.7.10");
        update = new java.util.HashMap<>(update);
        update.put("changed.txt", TestArchives.text("new build"));
        Path root = TestArchives.jar(temporary.resolve("mods/Legacy.jar"), Map.of(), update);
        String newHash = LegacyDiscovery.sha256(root);

        var diagnostics = new LegacyModRelocator().relocate(paths);

        assertFalse(Files.exists(root));
        assertEquals(newHash, LegacyDiscovery.sha256(existing));
        assertEquals(oldHash, LegacyDiscovery.sha256(onlyBackup(temporary)));
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("LEGACY_MOD_REPLACED") && !d.error()));
        assertTrue(new LegacyRuntimeService().inspect(temporary).diagnostics().stream().noneMatch(LegacyDiagnostic::error));
    }

    @Test void aPackUpdateWithANewFileNameReplacesTheOldVersionOfTheSameMod() throws Exception {
        var paths = new LegacyPaths(temporary);
        Path old = TestArchives.jar(paths.modDirectory().resolve("Legacy-1.7.10-1.0.jar"), Map.of(), legacyMod("Legacy", "1.7.10"));
        Path other = TestArchives.jar(paths.modDirectory().resolve("Other-1.7.10.jar"), Map.of(), legacyMod("Other", "1.7.10"));
        var update = new java.util.HashMap<>(legacyMod("Legacy", "1.7.10"));
        update.put("changed.txt", TestArchives.text("1.1"));
        TestArchives.jar(temporary.resolve("mods/Legacy-1.7.10-1.1.jar"), Map.of(), update);

        new LegacyModRelocator().relocate(paths);

        assertFalse(Files.exists(old));
        assertTrue(Files.exists(paths.modDirectory().resolve("Legacy-1.7.10-1.1.jar")));
        assertTrue(Files.exists(other));
        assertEquals("Legacy-1.7.10-1.0.jar", onlyBackup(temporary).getFileName().toString());
        var report = new LegacyRuntimeService().inspect(temporary);
        assertEquals(2, report.archives().size());
        assertTrue(report.diagnostics().stream().noneMatch(LegacyDiagnostic::error));
    }

    @Test void dropsAByteIdenticalDuplicateFromTheRoot() throws Exception {
        var paths = new LegacyPaths(temporary);
        Path existing = TestArchives.jar(paths.modDirectory().resolve("Legacy.jar"), Map.of(), legacyMod("Legacy", "1.7.10"));
        Path root = temporary.resolve("mods/Legacy.jar");
        Files.copy(existing, root);
        var diagnostics = new LegacyModRelocator().relocate(paths);
        assertFalse(Files.exists(root));
        assertTrue(Files.exists(existing));
        assertFalse(Files.exists(temporary.resolve("mfcl/legacy-replaced")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("LEGACY_RELOCATION_DUPLICATE")));
    }

    @Test void serviceReportsRelocationAndCanBeDisabled() throws Exception {
        Path legacy = TestArchives.jar(temporary.resolve("mods/Legacy.jar"), Map.of(), legacyMod("Legacy", "1.7.10"));
        System.setProperty(LegacyModRelocator.PROPERTY, "false");
        try {
            var service = new LegacyRuntimeService();
            service.relocateRootLegacyMods(temporary);
            assertTrue(Files.exists(legacy));
        } finally { System.clearProperty(LegacyModRelocator.PROPERTY); }
        var service = new LegacyRuntimeService();
        service.relocateRootLegacyMods(temporary);
        var report = service.inspect(temporary);
        assertEquals(1, report.archives().size());
        assertTrue(report.diagnostics().stream().anyMatch(d -> d.code().equals("LEGACY_MOD_RELOCATED")));
    }
}
