package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDependencyValidatorTest {
    private LegacyArchive archive(String name, String id, String version, List<String> required, Set<String> classes, List<String> libraries) {
        return new LegacyArchive(name, name, id.isBlank() ? List.of() : List.of(new LegacyMod(id, id, version, "1.7.10", "", "test", List.of())),
                "", "", List.of(), classes, required, libraries);
    }

    @Test void validatesNeiVersionRequirementWithoutRenamingLegacyIds() {
        var nei = archive("nei", "NotEnoughItems", "1.0.5.120", List.of("CodeChickenCore@[1.0.7,)"), Set.of("nei.Plugin"), List.of());
        var old = archive("old", "CodeChickenCore", "1.0.6.1", List.of(), Set.of(), List.of());
        var compatible = archive("new", "CodeChickenCore", "1.0.7.47", List.of(), Set.of(), List.of());
        assertTrue(new LegacyDependencyValidator().validate(List.of(nei)).stream().anyMatch(d -> d.code().equals("MISSING_MOD_DEPENDENCY")));
        assertTrue(new LegacyDependencyValidator().validate(List.of(nei, old)).stream().anyMatch(d -> d.code().equals("DEPENDENCY_VERSION")));
        assertTrue(new LegacyDependencyValidator().validate(List.of(nei, compatible)).isEmpty());
    }

    @Test void resolvesLibrariesByTheirRequiredClassEvenWithoutModMetadata() {
        var core = archive("core", "CodeChickenCore", "1.0.7.47", List.of(), Set.of(), List.of("codechicken.lib.asm.ASMHelper"));
        var library = archive("lib", "", "", List.of(), Set.of("codechicken.lib.asm.ASMHelper"), List.of());
        assertTrue(new LegacyDependencyValidator().validate(List.of(core)).stream().anyMatch(d -> d.code().equals("MISSING_LIBRARY_DEPENDENCY")));
        assertTrue(new LegacyDependencyValidator().validate(List.of(core, library)).isEmpty());
    }

    @Test void rejectsDuplicateModAndClassOwners() {
        var a = archive("a", "same", "1", List.of(), Set.of("test.Shared"), List.of());
        var b = archive("b", "same", "1", List.of(), Set.of("test.Shared"), List.of());
        var diagnostics = new LegacyDependencyValidator().validate(List.of(a, b));
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("DUPLICATE_MOD_ID")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("DUPLICATE_CLASS")));
    }
}
