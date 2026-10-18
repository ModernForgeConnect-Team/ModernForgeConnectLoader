package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyOpenGl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyOpenGlTest {
    private static class First { }
    private static class Other { }
    @Test void dispatchesOnlyWithinItsAttachedClassIdentity() {
        LegacyOpenGl.attach(First.class, (operation, arguments) -> operation.equals("glGetInteger(I)I") ? 4096 : null);
        try {
            assertEquals(4096, LegacyOpenGl.invoke(First.class, "glGetInteger(I)I", new Object[]{3379}));
            assertThrows(UnsupportedOperationException.class, () -> LegacyOpenGl.invoke(Other.class, "glGetInteger(I)I", new Object[]{3379}));
            assertThrows(IllegalStateException.class, () -> LegacyOpenGl.attach(First.class, (operation, arguments) -> null));
        } finally { LegacyOpenGl.detach(First.class); }
        assertThrows(UnsupportedOperationException.class, () -> LegacyOpenGl.invoke(First.class, "glGetInteger(I)I", new Object[]{3379}));
    }
}
