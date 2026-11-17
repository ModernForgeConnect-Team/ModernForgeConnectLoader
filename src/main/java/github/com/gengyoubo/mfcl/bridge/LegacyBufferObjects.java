package github.com.gengyoubo.mfcl.bridge;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * CPU-side LWJGL 2 {@code GL15} buffer objects. Legacy mods (for example HBM's VBO models) upload geometry during
 * pre-initialization, off the host render thread and before any world frame exists. The data is kept in heap memory
 * and offset pointer calls ({@code glVertexPointer(size, type, stride, offset)}) resolve against it, so buffer-backed
 * {@code glDrawArrays} goes through the same {@link LegacyWorldGraphics} capture as client-memory arrays.
 */
public final class LegacyBufferObjects {
    private static final int ARRAY_BUFFER = 34962, BUFFER_SIZE = 34660;
    private static final int GL_BYTE = 5120, GL_UNSIGNED_BYTE = 5121, GL_SHORT = 5122, GL_INT = 5124, GL_FLOAT = 5126, GL_DOUBLE = 5130;

    private static final ClassValue<Domain> DOMAINS = new ClassValue<>() {
        @Override protected Domain computeValue(Class<?> type) { return new Domain(); }
    };

    private static final class Domain {
        private int next = 1;
        private final Map<Integer, ByteBuffer> buffers = new HashMap<>();
        private final Map<Integer, Integer> bindings = new HashMap<>();
        synchronized int generate() { int id = next++; buffers.put(id, empty()); return id; }
        synchronized ByteBuffer bound(int target) {
            Integer id = bindings.get(target);
            return id == null || id == 0 ? null : buffers.get(id);
        }
    }

    private LegacyBufferObjects() { }

    private static ByteBuffer empty() { return ByteBuffer.allocate(0).order(ByteOrder.nativeOrder()); }
    private static Domain domain(Class<?> owner) { return DOMAINS.get(owner); }

    public static int glGenBuffers(Class<?> owner) { return domain(owner).generate(); }
    public static void glGenBuffers(Class<?> owner, IntBuffer out) {
        for (int i = out.position(); i < out.limit(); i++) out.put(i, domain(owner).generate());
    }
    public static void glBindBuffer(Class<?> owner, int target, int buffer) {
        Domain d = domain(owner);
        synchronized (d) {
            if (buffer != 0) d.buffers.putIfAbsent(buffer, empty());
            d.bindings.put(target, buffer);
        }
    }
    public static boolean glIsBuffer(Class<?> owner, int buffer) {
        Domain d = domain(owner);
        synchronized (d) { return d.buffers.containsKey(buffer); }
    }
    public static void glDeleteBuffers(Class<?> owner, int buffer) {
        Domain d = domain(owner);
        synchronized (d) {
            d.buffers.remove(buffer);
            d.bindings.replaceAll((target, bound) -> bound == buffer ? 0 : bound);
        }
    }
    public static void glDeleteBuffers(Class<?> owner, IntBuffer buffers) {
        for (int i = buffers.position(); i < buffers.limit(); i++) glDeleteBuffers(owner, buffers.get(i));
    }
    public static int glGetBufferParameteri(Class<?> owner, int target, int pname) {
        ByteBuffer data = domain(owner).bound(target);
        return pname == BUFFER_SIZE && data != null ? data.capacity() : 0;
    }

    /** glBufferData(target, size, usage): allocate zeroed storage. */
    public static void glBufferData(Class<?> owner, int target, long size, int usage) {
        store(owner, target, ByteBuffer.allocate(Math.toIntExact(size)).order(ByteOrder.nativeOrder()));
    }
    public static void glBufferData(Class<?> owner, int target, Buffer data, int usage) { store(owner, target, copy(data)); }

    public static void glBufferSubData(Class<?> owner, int target, long offset, Buffer data) {
        Domain d = domain(owner);
        synchronized (d) {
            ByteBuffer storage = d.bound(target);
            if (storage == null) throw new IllegalStateException("glBufferSubData without a bound buffer for target " + target);
            ByteBuffer bytes = copy(data);
            ByteBuffer destination = storage.duplicate().order(ByteOrder.nativeOrder());
            destination.position(Math.toIntExact(offset));
            destination.put(bytes);
        }
    }

    private static void store(Class<?> owner, int target, ByteBuffer data) {
        Domain d = domain(owner);
        synchronized (d) {
            Integer id = d.bindings.get(target);
            if (id == null || id == 0) throw new IllegalStateException("glBufferData without a bound buffer for target " + target);
            d.buffers.put(id, data);
        }
    }

    /** Copies the remaining elements of any NIO buffer into native-order heap bytes. */
    private static ByteBuffer copy(Buffer source) {
        if (source == null) return empty();
        int elements = source.remaining();
        ByteBuffer out;
        if (source instanceof ByteBuffer b) { out = ByteBuffer.allocate(elements); out.put(b.duplicate()); }
        else if (source instanceof FloatBuffer b) { out = ByteBuffer.allocate(elements * 4).order(ByteOrder.nativeOrder()); out.asFloatBuffer().put(b.duplicate()); }
        else if (source instanceof IntBuffer b) { out = ByteBuffer.allocate(elements * 4).order(ByteOrder.nativeOrder()); out.asIntBuffer().put(b.duplicate()); }
        else if (source instanceof ShortBuffer b) { out = ByteBuffer.allocate(elements * 2).order(ByteOrder.nativeOrder()); out.asShortBuffer().put(b.duplicate()); }
        else if (source instanceof DoubleBuffer b) { out = ByteBuffer.allocate(elements * 8).order(ByteOrder.nativeOrder()); out.asDoubleBuffer().put(b.duplicate()); }
        else throw new IllegalArgumentException("Unsupported legacy buffer data " + source.getClass().getName());
        out.clear();
        return out.order(ByteOrder.nativeOrder());
    }

    /** A typed view of the bound ARRAY_BUFFER, positioned at {@code offset} bytes. */
    private static Buffer view(Class<?> owner, int type, long offset, String call) {
        ByteBuffer data = domain(owner).bound(ARRAY_BUFFER);
        if (data == null) throw new IllegalStateException(call + " with a buffer offset but no bound GL_ARRAY_BUFFER");
        ByteBuffer bytes = data.duplicate().order(ByteOrder.nativeOrder());
        int start = Math.toIntExact(offset);
        Buffer typed = switch (type) {
            case GL_FLOAT -> bytes.asFloatBuffer();
            case GL_DOUBLE -> bytes.asDoubleBuffer();
            case GL_SHORT -> bytes.asShortBuffer();
            case GL_INT -> bytes.asIntBuffer();
            case GL_BYTE, GL_UNSIGNED_BYTE -> bytes;
            default -> throw new UnsupportedOperationException(call + " type " + type);
        };
        int size = type == GL_DOUBLE ? 8 : type == GL_FLOAT || type == GL_INT ? 4 : type == GL_SHORT ? 2 : 1;
        if (start % size != 0) throw new UnsupportedOperationException(call + " offset " + start + " is not aligned to its element type");
        return typed.position(start / size);
    }

    public static void glVertexPointer(Class<?> owner, int size, int type, int stride, long offset) {
        Buffer view = view(owner, type, offset, "glVertexPointer");
        if (view instanceof FloatBuffer b) LegacyWorldGraphics.glVertexPointer(size, stride, b);
        else if (view instanceof DoubleBuffer b) LegacyWorldGraphics.glVertexPointer(size, stride, b);
        else throw new UnsupportedOperationException("glVertexPointer type " + type);
    }
    public static void glTexCoordPointer(Class<?> owner, int size, int type, int stride, long offset) {
        Buffer view = view(owner, type, offset, "glTexCoordPointer");
        if (view instanceof FloatBuffer b) LegacyWorldGraphics.glTexCoordPointer(size, stride, b);
        else if (view instanceof ShortBuffer b) LegacyWorldGraphics.glTexCoordPointer(size, stride, b);
        else throw new UnsupportedOperationException("glTexCoordPointer type " + type);
    }
    public static void glNormalPointer(Class<?> owner, int type, int stride, long offset) {
        Buffer view = view(owner, type, offset, "glNormalPointer");
        if (view instanceof FloatBuffer b) LegacyWorldGraphics.glNormalPointer(stride, b);
        else if (view instanceof ByteBuffer b && type == GL_BYTE) LegacyWorldGraphics.glNormalPointer(stride, b);
        else throw new UnsupportedOperationException("glNormalPointer type " + type);
    }
    public static void glColorPointer(Class<?> owner, int size, int type, int stride, long offset) {
        Buffer view = view(owner, type, offset, "glColorPointer");
        if (view instanceof FloatBuffer b) LegacyWorldGraphics.glColorPointer(size, stride, b);
        else if (view instanceof ByteBuffer b && type == GL_UNSIGNED_BYTE) LegacyWorldGraphics.glColorPointer(size, true, stride, b);
        else throw new UnsupportedOperationException("glColorPointer type " + type);
    }
}
