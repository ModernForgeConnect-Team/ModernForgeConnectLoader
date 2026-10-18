package github.com.gengyoubo.mfcl.legacy.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationBarrierTest {
    @Test void lateRegistrationFailsBeforeChangingTheSealedPlan() {
        var barrier = new RegistrationBarrier<String>();
        barrier.declare("old:item", "old object");
        var snapshot = barrier.seal();
        assertThrows(IllegalStateException.class, () -> barrier.declare("old:late", "late object"));
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("old:late", "late"));
    }

    @Test void declaredDoesNotMeanBoundAndCompletionRequiresAllBindings() {
        var barrier = new RegistrationBarrier<String>();
        barrier.declare("old:item", "value");
        barrier.seal();
        assertThrows(IllegalStateException.class, () -> barrier.resolve("old:item"));
        barrier.beginBinding();
        assertThrows(IllegalStateException.class, barrier::complete);
        barrier.bind("old:item", "mfcl_legacy:item");
        barrier.complete();
        assertEquals("mfcl_legacy:item", barrier.resolve("old:item"));
        assertThrows(IllegalStateException.class, () -> barrier.bind("old:item", "other:item"));
    }

    @Test void failedRegistrationCannotResolveAnEarlierBinding() {
        var barrier = new RegistrationBarrier<String>();
        barrier.declare("old:item", "value");
        barrier.seal();
        barrier.beginBinding();
        barrier.bind("old:item", "modern:item");
        var failure = new RuntimeException("registration failed");
        barrier.fail(failure);
        var rejected = assertThrows(IllegalStateException.class, () -> barrier.resolve("old:item"));
        assertSame(failure, rejected.getCause());
    }
}
