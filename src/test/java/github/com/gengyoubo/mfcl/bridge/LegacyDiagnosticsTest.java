package github.com.gengyoubo.mfcl.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDiagnosticsTest {
    @AfterEach void reset() { LegacyDiagnostics.clear(); }

    @Test void missingMappingsAreCountedOncePerKey() {
        LegacyDiagnostics.missingMapping("block minecraft:sculk", "host block has no original representation");
        LegacyDiagnostics.missingMapping("block minecraft:sculk", "host block has no original representation");
        LegacyDiagnostics.missingMapping("item minecraft:echo_shard", "host item has no original representation");
        var findings = LegacyDiagnostics.findings(LegacyDiagnostics.Kind.MISSING_MAPPING);
        assertEquals(2, findings.size());
        assertEquals("block minecraft:sculk", findings.get(0).key());
        assertEquals(2, findings.get(0).count());
        assertTrue(LegacyDiagnostics.findings(LegacyDiagnostics.Kind.LEGACY_EXCEPTION).isEmpty());
    }

    @Test void failureIsAttributedToFirstNonHostFrame() {
        var cause = new IllegalArgumentException("bad aspect");
        cause.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.util.Objects", "requireNonNull", "Objects.java", 1),
                new StackTraceElement("thaumcraft.common.tiles.TileJar", "func_145845_h", "TileJar.java", 42),
                new StackTraceElement("net.minecraft.world.World", "func_72939_s", "World.java", 7)});
        LegacyDiagnostics.legacyFailure("tile tick Thaumcraft:blockJar", new IllegalStateException("wrapped", cause));
        var finding = LegacyDiagnostics.findings(LegacyDiagnostics.Kind.LEGACY_EXCEPTION).get(0);
        assertEquals("thaumcraft.common.tiles.TileJar.func_145845_h:42", finding.source());
        assertTrue(finding.detail().contains("bad aspect"), finding.detail());
    }

    static class BaseEvent {}
    static class ConcreteEvent extends BaseEvent {}

    @Test void postsCountForSupertypeHandlers() {
        LegacyDiagnostics.posted(new ConcreteEvent());
        assertTrue(LegacyDiagnostics.postedEvents().contains(ConcreteEvent.class.getName()));
        assertTrue(LegacyDiagnostics.postedEvents().contains(BaseEvent.class.getName()));
    }

    @Test void subscriptionsWithoutRuntimeAreEmpty() {
        LegacyDiagnostics.attach(null);
        assertTrue(LegacyDiagnostics.subscriptions().isEmpty());
    }
}
