package github.com.gengyoubo.mfcl.legacy;

import com.electronwill.nightconfig.toml.TomlParser;
import github.com.gengyoubo.mfcl.api.LegacyModInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.moddiscovery.NightConfigWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyAddonDependenciesTest {
    private static final Map<String, LegacyModInfo> LOADED = Map.of("Thaumcraft",
            new LegacyModInfo("Thaumcraft", "Thaumcraft", "4.2.3.5", "1.7.10"));

    @Test void readsRealTomlAndChecksOriginalCaseSensitiveIdAndVersion() {
        assertTrue(check("modId='Thaumcraft'\nversionRange='[4.2.3.5,5.0)'", LOADED, Dist.CLIENT).isEmpty());
        assertTrue(check("modId='Thaumcraft'\nversionRange='4.2.3.5'", LOADED, Dist.CLIENT).isEmpty());
        assertTrue(check("modId='Thaumcraft'\nversionRange='[4.2.3.5]'", LOADED, Dist.CLIENT).isEmpty());
        assertTrue(check("modId='thaumcraft'", LOADED, Dist.CLIENT).get(0).contains("not successfully loaded"));
        var errors = check("modId='Thaumcraft'\nversionRange='[5.0,)'", LOADED, Dist.CLIENT);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("mfclDependencies.addon[0]"));
        assertTrue(errors.get(0).contains("found 4.2.3.5"));
    }

    @Test void missingOrFailedRuntimeCannotSatisfyMandatoryDependency() {
        var errors = check("modId='Thaumcraft'", Map.of(), Dist.CLIENT);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("mods/1.7.10/"));
        assertTrue(errors.get(0).contains("runtime-1.7.10.json"));
        assertTrue(check("modId='Thaumcraft'\nmandatory=false", Map.of(), Dist.CLIENT).isEmpty());
        assertEquals(1, check("modId='Thaumcraft'\nmandatory=false\nversionRange='[5,)'", LOADED, Dist.CLIENT).size());
    }

    @Test void honorsPhysicalSideAndChecksBothOnDedicatedServer() {
        assertTrue(check("modId='Thaumcraft'\nside='CLIENT'", Map.of(), Dist.DEDICATED_SERVER).isEmpty());
        assertTrue(check("modId='Thaumcraft'\nside='SERVER'", Map.of(), Dist.CLIENT).isEmpty());
        assertEquals(1, check("modId='Thaumcraft'\nside='SERVER'", Map.of(), Dist.DEDICATED_SERVER).size());
        assertEquals(1, check("modId='Thaumcraft'", Map.of(), Dist.DEDICATED_SERVER).size());
    }

    @Test void unknownVersionOnlySatisfiesUnconstrainedDependency() {
        var unknown = Map.of("dynamic", new LegacyModInfo("dynamic", "dynamic", "", "1.7.10"));
        assertTrue(check("modId='dynamic'", unknown, Dist.CLIENT).isEmpty());
        assertTrue(check("modId='dynamic'\nversionRange='[1,)'", unknown, Dist.CLIENT).get(0).contains("unknown version"));
    }

    @Test void malformedDeclarationsFailInsteadOfSilentlyDisablingChecks() {
        for (String entry : List.of("mandatory=true", "modId=''", "modId=123", "modId='x'\nmandatory='true'",
                "modId='x'\nside='client'", "modId='x'\nversionRange=4", "modId='x'\nversionRange='[4,5'",
                "modId='x'\nordering='AFTER'")) {
            assertFalse(check(entry, LOADED, Dist.CLIENT).isEmpty(), entry);
        }
        assertFalse(validate("[mfclDependencies.addon]\nmodId='Thaumcraft'", LOADED, Dist.CLIENT).isEmpty());
        assertFalse(validate("[[mfclDependencies.typo]]\nmodId='Thaumcraft'", LOADED, Dist.CLIENT).isEmpty());
        assertTrue(validate("[[mods]]\nmodId='addon'", LOADED, Dist.CLIENT).isEmpty());
    }

    @Test void aggregatesMultipleRequirementsAndModernOwnersInOneJar() {
        var config = new NightConfigWrapper(new TomlParser().parse("""
                [[mfclDependencies.first]]
                modId='missing1'
                [[mfclDependencies.first]]
                modId='Thaumcraft'
                versionRange='[5,)'
                [[mfclDependencies.second]]
                modId='missing2'
                """));
        var errors = LegacyAddonDependencies.validateFile(Set.of("first", "second"), config, LOADED, Dist.CLIENT);
        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("first[1]")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("second[0]")));
    }

    private static List<String> check(String entry, Map<String, LegacyModInfo> mods, Dist dist) {
        return validate("[[mfclDependencies.addon]]\n" + entry, mods, dist);
    }
    private static List<String> validate(String toml, Map<String, LegacyModInfo> mods, Dist dist) {
        return LegacyAddonDependencies.validateFile(Set.of("addon"),
                new NightConfigWrapper(new TomlParser().parse(toml)), mods, dist);
    }
}
