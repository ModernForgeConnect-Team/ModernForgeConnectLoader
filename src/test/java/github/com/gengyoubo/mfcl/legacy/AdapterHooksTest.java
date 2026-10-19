package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdapterHooksTest {
    @Test void playerScopesUnwindInReverseAndRollbackPartialEntry() throws Exception {
        var state = new LegacyPlayerState(); var calls = new ArrayList<String>();
        state.register(new LegacyPlayerState.Hooks() {
            public String id() { return "first"; }
            public AutoCloseable enter(Object world) { calls.add("enter1"); return () -> { calls.add("close1"); throw new IllegalStateException("close failed"); }; }
        });
        var registration = state.register(new LegacyPlayerState.Hooks() {
            public String id() { return "second"; }
            public AutoCloseable enter(Object world) { calls.add("enter2"); return () -> calls.add("close2"); }
        });
        try (var scope = state.enter(new Object())) { calls.add("body"); }
        catch (IllegalStateException expected) { assertEquals("close failed", expected.getMessage()); }
        assertEquals(List.of("enter1","enter2","body","close2","close1"), calls);
        registration.close(); calls.clear();
        state.register(new LegacyPlayerState.Hooks() {
            public String id() { return "failed"; }
            public AutoCloseable enter(Object world) { throw new IllegalArgumentException("entry failed"); }
        });
        var failure = assertThrows(IllegalArgumentException.class, () -> state.enter(new Object()));
        assertEquals(List.of("enter1","close1"), calls); assertEquals("close failed", failure.getSuppressed()[0].getMessage());
    }
    @Test void dynamicCaptureSelectsOnlyTheDeclaredItemContextAndRejectsAmbiguity() throws Exception {
        var registry = new LegacyDynamicItems(); var drawing = new LegacyDynamicItems.Drawing(new ArrayList<>(), new ArrayList<>());
        var stack = new LegacyClientData.Stack("test:item",0,1,"","");
        var renderer = new LegacyDynamicItems.Renderer() {
            public String id() { return "test:first"; }
            public boolean supports(String item, String context) { return item.equals("test:item") && context.equals("EQUIPPED"); }
            public Optional<LegacyDynamicItems.Drawing> capture(LegacyClientData.Stack value, String context) { return Optional.of(drawing); }
        };
        try (var registration = registry.register(renderer)) {
            assertFalse(registry.supports("other:item","EQUIPPED"));
            assertTrue(registry.capture(stack,"INVENTORY").isEmpty());
            assertSame(drawing,registry.capture(stack,"EQUIPPED").orElseThrow());
            assertThrows(IllegalArgumentException.class, () -> registry.register(renderer));
            var second = registry.register(new LegacyDynamicItems.Renderer() {
                public String id() { return "test:second"; }
                public boolean supports(String item, String context) { return renderer.supports(item,context); }
                public Optional<LegacyDynamicItems.Drawing> capture(LegacyClientData.Stack value, String context) { return Optional.empty(); }
            });
            assertThrows(IllegalStateException.class, () -> registry.capture(stack,"EQUIPPED"));
            second.close(); assertSame(drawing,registry.capture(stack,"EQUIPPED").orElseThrow());
        }
        assertTrue(registry.capture(stack,"EQUIPPED").isEmpty());
    }
}
