package github.com.gengyoubo.mfcl.bridge;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Thread-confined capture of private legacy world rendering. The real old Tessellator, immediate-mode and
 * vertex-array calls are recorded as geometry instead of touching the host OpenGL state; the host replays them.
 */
public final class LegacyWorldGraphics {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private LegacyWorldGraphics() { }

    public static Frame begin() {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested legacy world capture");
        Frame frame = new Frame(); CURRENT.set(frame); return frame;
    }
    public static boolean active() { return CURRENT.get() != null; }
    public static Frame current() { return CURRENT.get(); }

    public static final class Frame implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final Deque<double[]> stack = new ArrayDeque<>();
        private double[] matrix = LegacyVertexStream.identity();
        private String texture = "";
        private final List<LegacyVertexStream.Primitive> primitives = new ArrayList<>();
        private final List<LegacyVertexStream.Vertex> pending = new ArrayList<>();
        private int mode = -1;
        private float u, v, nx, ny = 0, nz = 1;
        private int argb = 0xFFFFFFFF;
        private Object vertexBuffer; private int vertexSize = 3, vertexStride; private int vertexPosition; private boolean vertexEnabled;
        private Object texBuffer; private int texSize = 2, texStride; private int texPosition; private boolean texEnabled;
        private Object colorBuffer; private int colorSize = 4, colorStride; private int colorPosition; private boolean colorEnabled;
        private Object normalBuffer; private int normalStride; private int normalPosition; private boolean normalEnabled;

        public List<LegacyVertexStream.Primitive> primitives() { check(); return List.copyOf(primitives); }
        private void check() { if (Thread.currentThread() != owner || CURRENT.get() != this) throw new IllegalStateException("Expired or foreign legacy world capture"); }
        @Override public void close() { check(); if (mode >= 0) throw new IllegalStateException("Unbalanced legacy world primitive"); CURRENT.remove(); }
        private void emit() { if (!pending.isEmpty()) primitives.add(new LegacyVertexStream.Primitive(mode, texture, List.copyOf(pending))); }
        private LegacyVertexStream.Vertex capture(double x, double y, double z, float u, float v, float nx, float ny, float nz, int argb) {
            double[] p = LegacyVertexStream.transform(matrix, x, y, z);
            return new LegacyVertexStream.Vertex(p[0], p[1], p[2], u, v, nx, ny, nz, argb);
        }
    }

    private static Frame frame() { return CURRENT.get(); }

    public static int integer(int value) {
        Frame f = frame();
        if (value == 2979) return f == null ? 0 : f.stack.size() + 1; // GL_MODELVIEW_STACK_DEPTH
        if (value == 3415) return 0; // No legacy stencil buffer exists.
        throw new UnsupportedOperationException("Unsupported legacy world GL integer query: " + value);
    }

    public static void push() { Frame f = frame(); if (f != null) { f.check(); f.stack.push(LegacyVertexStream.copy(f.matrix)); } }
    public static void pop() { Frame f = frame(); if (f != null) { f.check(); f.matrix = f.stack.pop(); } }
    public static void loadIdentity() { Frame f = frame(); if (f != null) { f.check(); f.matrix = LegacyVertexStream.identity(); } }
    public static void translate(double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); LegacyVertexStream.translate(f.matrix, x, y, z); } }
    public static void scale(double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); LegacyVertexStream.scale(f.matrix, x, y, z); } }
    public static void rotate(double angle, double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); LegacyVertexStream.rotate(f.matrix, angle, x, y, z); } }

    public static void bindTexture(int handle) { Frame f = frame(); if (f != null) { f.check(); f.texture = "legacy:" + handle; } }
    /** Old TextureManager.bindTexture resource path, used by model-based item renderers. */
    public static void texture(String resource) { Frame f = frame(); if (f != null) { f.check(); f.texture = resource; } }

    public static void glEnableClientState(int cap) { Frame f = frame(); if (f == null) return; f.check(); if (cap == 32884) f.vertexEnabled = true; else if (cap == 32888) f.texEnabled = true; else if (cap == 32886) f.colorEnabled = true; else if (cap == 32885) f.normalEnabled = true; }
    public static void glDisableClientState(int cap) { Frame f = frame(); if (f == null) return; f.check(); if (cap == 32884) f.vertexEnabled = false; else if (cap == 32888) f.texEnabled = false; else if (cap == 32886) f.colorEnabled = false; else if (cap == 32885) f.normalEnabled = false; }

    public static void glVertexPointer(int size, int stride, FloatBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.vertexBuffer = buffer; f.vertexSize = size; f.vertexStride = stride; f.vertexPosition = buffer == null ? 0 : buffer.position(); }
    public static void glVertexPointer(int size, int stride, DoubleBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.vertexBuffer = buffer; f.vertexSize = size; f.vertexStride = stride; f.vertexPosition = buffer == null ? 0 : buffer.position(); }
    public static void glTexCoordPointer(int size, int stride, FloatBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.texBuffer = buffer; f.texSize = size; f.texStride = stride; f.texPosition = buffer == null ? 0 : buffer.position(); }
    public static void glTexCoordPointer(int size, int stride, ShortBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.texBuffer = buffer; f.texSize = size; f.texStride = stride; f.texPosition = buffer == null ? 0 : buffer.position(); }
    public static void glColorPointer(int size, boolean normalized, int stride, ByteBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.colorBuffer = buffer; f.colorSize = size; f.colorStride = stride; f.colorPosition = buffer == null ? 0 : buffer.position(); }
    public static void glColorPointer(int size, int stride, FloatBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.colorBuffer = buffer; f.colorSize = size; f.colorStride = stride; f.colorPosition = buffer == null ? 0 : buffer.position(); }
    public static void glNormalPointer(int stride, ByteBuffer buffer) { Frame f = frame(); if (f == null) return; f.check(); f.normalBuffer = buffer; f.normalStride = stride; f.normalPosition = buffer == null ? 0 : buffer.position(); }

    public static void glBegin(int mode) {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.mode >= 0) throw new IllegalStateException("Nested legacy world primitive");
        f.mode = mode; f.pending.clear();
    }
    public static void glEnd() {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.mode < 0) throw new IllegalStateException("Legacy world primitive was not started");
        f.emit(); f.pending.clear(); f.mode = -1;
    }
    public static void glVertex2f(float x, float y) { vertex(x, y, 0); }
    public static void glVertex2d(double x, double y) { vertex(x, y, 0); }
    public static void glVertex3f(float x, float y, float z) { vertex(x, y, z); }
    public static void glVertex3d(double x, double y, double z) { vertex(x, y, z); }
    public static void glTexCoord2f(float u, float v) { Frame f = frame(); if (f != null) { f.check(); f.u = u; f.v = v; } }
    public static void glTexCoord2d(double u, double v) { Frame f = frame(); if (f != null) { f.check(); f.u = (float) u; f.v = (float) v; } }
    public static void glNormal3f(float x, float y, float z) { Frame f = frame(); if (f != null) { f.check(); f.nx = x; f.ny = y; f.nz = z; } }
    public static void glNormal3d(double x, double y, double z) { Frame f = frame(); if (f != null) { f.check(); f.nx = (float) x; f.ny = (float) y; f.nz = (float) z; } }
    public static void glColor3f(float r, float g, float b) { glColor4f(r, g, b, 1); }
    public static void glColor4f(float r, float g, float b, float a) { setColor(byte255(r), byte255(g), byte255(b), byte255(a)); }
    public static void glColor3d(double r, double g, double b) { glColor4f((float) r, (float) g, (float) b, 1); }
    public static void glColor4d(double r, double g, double b, double a) { glColor4f((float) r, (float) g, (float) b, (float) a); }
    public static void glColor3ub(byte r, byte g, byte b) { setColor(r & 0xFF, g & 0xFF, b & 0xFF, 255); }
    public static void glColor4ub(byte r, byte g, byte b, byte a) { setColor(r & 0xFF, g & 0xFF, b & 0xFF, a & 0xFF); }
    private static int byte255(float value) { int v = Math.round(value * 255f); return v < 0 ? 0 : Math.min(v, 255); }
    private static void setColor(int r, int g, int b, int a) { Frame f = frame(); if (f != null) { f.check(); f.argb = (a << 24) | (r << 16) | (g << 8) | b; } }
    private static void vertex(double x, double y, double z) {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.mode < 0) throw new IllegalStateException("Legacy world vertex outside a primitive");
        f.pending.add(f.capture(x, y, z, f.u, f.v, f.nx, f.ny, f.nz, f.argb));
    }

    public static void glDrawArrays(int mode, int first, int count) {
        Frame f = frame(); if (f == null) return; f.check();
        if (f.vertexBuffer == null) throw new IllegalStateException("Legacy world array draw without a vertex pointer");
        int previousMode = f.mode;
        f.mode = mode; f.pending.clear();
        for (int i = 0; i < count; i++) {
            int vertexIndex = first + i;
            double x = component(f.vertexBuffer, f.vertexStride, f.vertexPosition, f.vertexSize, vertexIndex, 0);
            double y = component(f.vertexBuffer, f.vertexStride, f.vertexPosition, f.vertexSize, vertexIndex, 1);
            double z = component(f.vertexBuffer, f.vertexStride, f.vertexPosition, f.vertexSize, vertexIndex, 2);
            float u = 0, v = 0;
            if (f.texEnabled && f.texBuffer != null) {
                u = (float) component(f.texBuffer, f.texStride, f.texPosition, f.texSize, vertexIndex, 0);
                v = (float) component(f.texBuffer, f.texStride, f.texPosition, f.texSize, vertexIndex, 1);
                if (f.texBuffer instanceof ShortBuffer) { u /= 32768f; v /= 32768f; }
            }
            int argb = f.colorEnabled && f.colorBuffer != null ? color(f, vertexIndex) : 0xFFFFFFFF;
            float nx = 0, ny = 0, nz = 1;
            if (f.normalEnabled && f.normalBuffer instanceof ByteBuffer normalBuffer) {
                int base = f.normalPosition + vertexIndex * (f.normalStride == 0 ? 3 : f.normalStride);
                nx = normalBuffer.get(base) / 127f; ny = normalBuffer.get(base + 1) / 127f; nz = normalBuffer.get(base + 2) / 127f;
            }
            f.pending.add(f.capture(x, y, z, u, v, nx, ny, nz, argb));
        }
        f.emit(); f.pending.clear(); f.mode = previousMode;
    }
    private static int color(Frame f, int vertexIndex) {
        if (f.colorBuffer instanceof ByteBuffer buffer) {
            int base = f.colorPosition + vertexIndex * (f.colorStride == 0 ? f.colorSize : f.colorStride);
            return (buffer.get(base + 3) & 0xFF) << 24 | (buffer.get(base) & 0xFF) << 16 | (buffer.get(base + 1) & 0xFF) << 8 | (buffer.get(base + 2) & 0xFF);
        }
        if (f.colorBuffer instanceof FloatBuffer buffer) {
            int base = f.colorPosition + vertexIndex * (f.colorStride == 0 ? f.colorSize : f.colorStride / 4);
            return byte255(buffer.get(base + 3)) << 24 | byte255(buffer.get(base)) << 16 | byte255(buffer.get(base + 1)) << 8 | byte255(buffer.get(base + 2));
        }
        throw new IllegalStateException("Unsupported legacy color pointer buffer");
    }
    private static double component(Object buffer, int byteStride, int positionElements, int size, int vertexIndex, int component) {
        int elementSize = elementSize(buffer);
        int strideElements = byteStride == 0 ? size : byteStride / elementSize;
        int index = positionElements + vertexIndex * strideElements + component;
        if (buffer instanceof FloatBuffer b) return b.get(index);
        if (buffer instanceof DoubleBuffer b) return b.get(index);
        if (buffer instanceof ByteBuffer b) return b.get(index) & 0xFF;
        if (buffer instanceof ShortBuffer b) return b.get(index);
        if (buffer instanceof IntBuffer b) return b.get(index);
        throw new IllegalStateException("Unsupported legacy array buffer " + buffer.getClass().getName());
    }
    private static int elementSize(Object buffer) {
        if (buffer instanceof ByteBuffer) return 1;
        if (buffer instanceof ShortBuffer) return 2;
        if (buffer instanceof IntBuffer || buffer instanceof FloatBuffer) return 4;
        if (buffer instanceof DoubleBuffer) return 8;
        throw new IllegalStateException("Unsupported legacy array buffer " + buffer.getClass().getName());
    }

    /** Expand a recorded GL_COMPILE list into the active capture instead of touching host OpenGL. */
    public static void callList(List<LegacyDisplayLists.Command> commands) {
        Frame f = frame(); if (f == null) return;
        for (LegacyDisplayLists.Command command : commands) interpret(command.operation(), command.arguments());
    }
    private static void interpret(String operation, List<Double> a) {
        double d0 = a.size() > 0 ? a.get(0) : 0, d1 = a.size() > 1 ? a.get(1) : 0, d2 = a.size() > 2 ? a.get(2) : 0, d3 = a.size() > 3 ? a.get(3) : 0;
        if (operation.equals("glBegin(I)V")) glBegin((int) d0);
        else if (operation.equals("glEnd()V")) glEnd();
        else if (operation.startsWith("glVertex2")) glVertex2d(d0, d1);
        else if (operation.startsWith("glVertex3")) glVertex3d(d0, d1, d2);
        else if (operation.startsWith("glTexCoord2")) glTexCoord2d(d0, d1);
        else if (operation.startsWith("glNormal3")) glNormal3d(d0, d1, d2);
        else if (operation.startsWith("glColor3")) setColor(byte255((float) d0), byte255((float) d1), byte255((float) d2), 255);
        else if (operation.startsWith("glColor4")) setColor(byte255((float) d0), byte255((float) d1), byte255((float) d2), byte255((float) d3));
        else if (operation.equals("glPushMatrix()V")) push();
        else if (operation.equals("glPopMatrix()V")) pop();
        else if (operation.startsWith("glTranslate")) translate(d0, d1, d2);
        else if (operation.startsWith("glScale")) scale(d0, d1, d2);
        else if (operation.startsWith("glRotate")) rotate(d0, d1, d2, d3);
        // Immediate state changes (enable/disable/blend/etc.) do not affect captured geometry.
    }
}
