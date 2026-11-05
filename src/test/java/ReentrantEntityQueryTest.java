package github.com.gengyoubo.mfcl.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReentrantEntityQueryTest {
    @Test void breaksSameEntityCycleButAllowsOtherEntities() {
        var guard = new ReentrantEntityQuery();
        var entity = new Object();
        var other = new Object();
        assertTrue(guard.get(entity, () -> {
            assertFalse(guard.get(entity, () -> { fail("recursive query executed"); return true; }));
            assertTrue(guard.get(other, () -> true));
            return true;
        }));
        assertFalse(guard.get(entity, () -> false));
    }

    @Test void clearsGuardAfterException() {
        var guard = new ReentrantEntityQuery();
        var entity = new Object();
        assertThrows(IllegalStateException.class, () -> guard.get(entity, () -> { throw new IllegalStateException(); }));
        assertTrue(guard.get(entity, () -> true));
    }
}
