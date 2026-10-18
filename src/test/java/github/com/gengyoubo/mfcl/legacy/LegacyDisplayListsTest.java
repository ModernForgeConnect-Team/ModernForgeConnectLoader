package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyDisplayLists;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDisplayListsTest {
    private static class FirstDomain { }
    private static class OtherDomain { }
    private static class InvalidDomain { }

    @Test void retainsVerticesAndStateWithoutAHostFrameOrNativeGlContext() {
        Class<?> owner = FirstDomain.class;
        int id = LegacyDisplayLists.generate(owner, 2);
        assertFalse(LegacyDisplayLists.exists(owner, id));
        LegacyDisplayLists.begin(owner, id, 4864);
        LegacyDisplayLists.record(owner, "glEnable(I)V", new double[]{3042});
        LegacyDisplayLists.record(owner, "glBegin(I)V", new double[]{7});
        LegacyDisplayLists.record(owner, "glColor4d(DDDD)V", new double[]{0.2, 0.3, 0.4, 0.5});
        LegacyDisplayLists.record(owner, "glVertex3d(DDD)V", new double[]{1.25, -2, 3});
        LegacyDisplayLists.record(owner, "glEnd()V", new double[0]);
        LegacyDisplayLists.end(owner);
        assertTrue(LegacyDisplayLists.exists(owner, id));
        assertEquals(5, LegacyDisplayLists.commands(owner, id).size());
        assertEquals(List.of(1.25, -2.0, 3.0), LegacyDisplayLists.commands(owner, id).get(3).arguments());
        assertThrows(UnsupportedOperationException.class, () -> LegacyDisplayLists.commands(owner, id).clear());
        assertThrows(UnsupportedOperationException.class, () -> LegacyDisplayLists.call(owner, id));
        assertFalse(LegacyDisplayLists.exists(OtherDomain.class, id));
        assertEquals(1, LegacyDisplayLists.generate(OtherDomain.class, 1));
        LegacyDisplayLists.delete(owner, id, 2);
        assertFalse(LegacyDisplayLists.exists(owner, id));
        assertDoesNotThrow(() -> LegacyDisplayLists.call(owner, id));
    }

    @Test void rejectsUnsupportedExecutionAndMalformedPrimitives() {
        Class<?> owner = InvalidDomain.class;
        assertThrows(UnsupportedOperationException.class, () -> LegacyDisplayLists.begin(owner, 1, 4865));
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.end(owner));
        assertFalse(LegacyDisplayLists.record(owner, "glBegin(I)V", new double[]{7}));
        LegacyDisplayLists.begin(owner, 1, 4864);
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.begin(owner, 2, 4864));
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.record(owner, "glVertex3d(DDD)V", new double[]{0, 0, 0}));
        LegacyDisplayLists.record(owner, "glBegin(I)V", new double[]{7});
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.end(owner));
        LegacyDisplayLists.record(owner, "glEnd()V", new double[0]);
        LegacyDisplayLists.end(owner);
        LegacyDisplayLists.delete(owner, 1, 1);
    }
}
