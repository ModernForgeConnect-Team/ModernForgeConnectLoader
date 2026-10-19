package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyModCatalogTest {
    @Test void countsLoadedModsRatherThanArchivesOrRuntimeComponents() {
        var catalog = catalog(true, Set.of("mcp", "FML", "Forge", "CodeChickenCore", "NotEnoughItems"),
                archive("ccc.jar", mod("CodeChickenCore")), archive("nei.jar", mod("NotEnoughItems")), archive("ccl.jar"));
        assertEquals(2, catalog.loadedCount());
        assertEquals(2, catalog.entries().size());
        assertEquals(5, catalog.totalLoaded(3));
        assertEquals("Mod loading complete - 5 mods loaded", catalog.completionMessage("Mod loading complete - 3 mods loaded"));
        assertEquals("ERROR DURING MOD LOADING", catalog.completionMessage("ERROR DURING MOD LOADING"));
        assertEquals("Loading 3 mods", catalog.completionMessage("Loading 3 mods"));
    }

    @Test void discoveredOrFailedModsStayVisibleWithoutIncreasingLoadedCount() {
        var discovered = catalog(true, Set.of("FML", "Forge"), archive("pending.jar", mod("pending")));
        var failed = catalog(false, Set.of("pending"), archive("pending.jar", mod("pending")));
        assertTrue(discovered.failure().isBlank());
        assertFalse(failed.failure().isBlank());
        for (var catalog : List.of(discovered, failed)) {
            assertEquals(1, catalog.entries().size());
            assertFalse(catalog.entries().get(0).loaded());
            assertEquals(0, catalog.loadedCount());
            assertTrue(catalog.loadedMods(Map.of("pending", "2.0")).isEmpty());
            assertEquals("Mod loading complete - 3 mods loaded", catalog.completionMessage("Mod loading complete - 3 mods loaded"));
        }
    }

    @Test void publicMetadataUsesRuntimeVersionsAndIncludesDynamicContainers() {
        var catalog = catalog(true, Set.of("Thaumcraft", "dynamic", "Forge"),
                archive("tc.jar", mod("Thaumcraft")), archive("pending.jar", mod("pending")));
        var mods = catalog.loadedMods(Map.of("Thaumcraft", "4.2.3.5", "dynamic", "2.3"));
        assertEquals(Set.of("Thaumcraft", "dynamic"), mods.keySet());
        assertEquals("4.2.3.5", mods.get("Thaumcraft").version());
        assertEquals("2.3", mods.get("dynamic").version());
        assertEquals("1.7.10", mods.get("Thaumcraft").sourceMinecraft());
        assertThrows(UnsupportedOperationException.class, mods::clear);
    }

    @Test void deduplicatesMetadataAndIncludesActualDynamicCoremodContainers() {
        var catalog = catalog(true, Set.of("same", "dynamic", "FML"), archive("one.jar", mod("same")),
                archive("duplicate.jar", mod("same")), archive("pending.jar", mod("pending")));
        assertEquals(3, catalog.entries().size());
        assertEquals(2, catalog.loadedCount());
        assertEquals("one.jar", catalog.entries().get(0).file());
        assertTrue(catalog.entries().stream().anyMatch(entry -> entry.mod().id().equals("dynamic") && entry.loaded()));
        assertFalse(catalog.entries().stream().filter(entry -> entry.mod().id().equals("pending")).findFirst().orElseThrow().loaded());
    }

    private static LegacyModCatalog catalog(boolean started, Set<String> loaded, LegacyArchive... archives) {
        return LegacyModCatalog.from(new LegacyRuntimeService.Report("1.20.1", "1.7.10", "mods/1.7.10", "report.json",
                started ? LegacyRuntimeService.State.FML_AVAILABLE : LegacyRuntimeService.State.RUNTIME_FAILED,
                started, false, List.of(archives), List.of()), loaded);
    }
    private static LegacyMod mod(String id) { return new LegacyMod(id, id, "1.0", "1.7.10", "", "", List.of()); }
    private static LegacyArchive archive(String file, LegacyMod... mods) {
        return new LegacyArchive(file, "", List.of(mods), null, null, List.of(), Set.of(), List.of(), List.of());
    }
}
