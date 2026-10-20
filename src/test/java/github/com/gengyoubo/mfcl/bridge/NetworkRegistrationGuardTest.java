package github.com.gengyoubo.mfcl.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkRegistrationGuardTest {
    private static final class First {}
    private static final class Second {}

    @Test void sameTypeMayHaveOneHandlerPerDirection() {
        var guard = NetworkRegistrationGuard.host("Test");
        guard.register(4, First.class, "CLIENT");
        guard.register(4, First.class, "SERVER");
        guard.register(5, Second.class, "SERVER");
        assertEquals(3, guard.size());
    }

    @Test void duplicateIdNamesBothTypes() {
        var guard = NetworkRegistrationGuard.host("Test");
        guard.register(4, First.class, "CLIENT");
        var failure = assertThrows(IllegalStateException.class, () -> guard.register(4, Second.class, "SERVER"));
        assertTrue(failure.getMessage().contains(First.class.getName()) && failure.getMessage().contains(Second.class.getName()), failure.getMessage());
    }

    @Test void idsAliasingAfterByteTruncationAreDuplicates() {
        var guard = NetworkRegistrationGuard.host("Test");
        guard.register(255, First.class, "CLIENT");
        assertThrows(IllegalStateException.class, () -> guard.register(-1, Second.class, "CLIENT"));
        assertThrows(IllegalStateException.class, () -> guard.register(256, Second.class, "CLIENT"));
    }

    @Test void hostTypeWithTwoIdsIsRejected() {
        var guard = NetworkRegistrationGuard.host("Test");
        guard.register(1, First.class, "CLIENT");
        assertThrows(IllegalStateException.class, () -> guard.register(2, First.class, "SERVER"));
    }

    @Test void legacyTypeMayUseOneIdPerSide() {
        // TC4 4.2.3.5 registers PacketNote as 18 (client) and 21 (server); original FML accepts this.
        var guard = NetworkRegistrationGuard.legacy("Test");
        guard.register(18, First.class, "CLIENT");
        guard.register(21, First.class, "SERVER");
        assertThrows(IllegalStateException.class, () -> guard.register(22, First.class, "SERVER"));
        assertThrows(IllegalStateException.class, () -> guard.register(21, Second.class, "CLIENT"));
    }

    @Test void repeatedHandlerForOneDirectionIsRejected() {
        var guard = NetworkRegistrationGuard.host("Test");
        guard.register(1, First.class, "SERVER");
        assertThrows(IllegalStateException.class, () -> guard.register(1, First.class, "SERVER"));
    }

    @Test void legacyWrapperRegistrationFailsImmediately() {
        Object wrapper = new Object();
        LegacyPackets.channel(wrapper, "Guarded");
        LegacyPackets.register(wrapper, new Object(), First.class, 0, "CLIENT");
        assertThrows(IllegalStateException.class, () -> LegacyPackets.register(wrapper, new Object(), Second.class, 0, "SERVER"));
        assertThrows(IllegalStateException.class, () -> LegacyPackets.register(new Object(), new Object(), First.class, 1, "CLIENT"));
    }
}
