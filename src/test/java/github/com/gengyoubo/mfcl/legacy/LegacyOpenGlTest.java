package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyOpenGl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyOpenGlTest {
    private static class First { }
    private static class Other { }
    @Test void geometryCaptureInitializesOldTextureUtilitiesWithoutNativeCalls() {
        LegacyOpenGl.attach(Other.class, (operation,arguments) -> { throw new AssertionError("Native call during geometry capture: " + operation); });
        try (var frame = github.com.gengyoubo.mfcl.bridge.LegacyWorldGraphics.begin()) {
            int texture = (int)LegacyOpenGl.invoke(Other.class,"glGenTextures()I",new Object[]{});
            LegacyOpenGl.invoke(Other.class,"glBindTexture(II)V",new Object[]{3553,texture});
            LegacyOpenGl.invoke(Other.class,"glTexImage2D(IIIIIIIILjava/nio/IntBuffer;)V",new Object[]{3553,0,6408,16,32,0,6408,5121,java.nio.IntBuffer.allocate(512)});
            assertEquals(16,LegacyOpenGl.invoke(Other.class,"glGetTexLevelParameteri(III)I",new Object[]{3553,0,4096}));
            assertEquals(32,LegacyOpenGl.invoke(Other.class,"glGetTexLevelParameteri(III)I",new Object[]{3553,0,4097}));
            assertEquals(true,LegacyOpenGl.invoke(Other.class,"glIsTexture(I)Z",new Object[]{texture}));
        } finally { LegacyOpenGl.detach(Other.class); }
    }
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
