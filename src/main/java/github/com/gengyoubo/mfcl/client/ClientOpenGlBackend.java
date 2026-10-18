package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyOpenGl;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModWorkManager;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;

import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.*;

/** Real host-context textures and shader objects, with private ownership and per-call state restoration. */
public final class ClientOpenGlBackend implements LegacyOpenGl.Backend {
    private final Map<Integer, Integer> textures = new HashMap<>();
    private final Set<Integer> programs = new HashSet<>(), shaders = new HashSet<>();
    private final Map<Integer, Integer> pixelStore = new LinkedHashMap<>(Map.of(3317, 4, 3314, 0, 3315, 0, 3316, 0,
            3333, 4, 3330, 0, 3331, 0, 3332, 0));
    private final Map<String, Method> methods = new HashMap<>();
    private final List<String> failures = new ArrayList<>();
    private volatile boolean bootstrapping = true;
    private boolean closed;
    private int boundTexture, boundProgram, textureUploads, nextTexture = 1;
    private String version = "";

    @Override public Object call(String operation, Object[] arguments) throws Exception {
        return render(() -> dispatch(operation, arguments));
    }
    @Override public void bootstrapComplete() { bootstrapping = false; }

    private <T> T render(Callable<T> work) throws Exception {
        if (RenderSystem.isOnRenderThread()) return work.call();
        var result = new CompletableFuture<T>();
        Runnable task = () -> {
            if (result.isCancelled()) return;
            try { result.complete(work.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        };
        // Forge drives this queue on the context-owning thread while constructing mods.
        if (bootstrapping) ModWorkManager.syncExecutor().execute(task);
        else Minecraft.getInstance().execute(task);
        try { return result.get(20, TimeUnit.SECONDS); }
        catch (TimeoutException failure) { result.cancel(false); throw new IllegalStateException("Host render executor did not service legacy GL within 20 seconds", failure); }
        catch (InterruptedException failure) { result.cancel(false); Thread.currentThread().interrupt(); throw failure; }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    private Object dispatch(String operation, Object[] a) throws Exception {
        if (closed) throw new IllegalStateException("Legacy GL backend is closed");
        String name = operation.substring(0, operation.indexOf('('));
        if (name.equals("glGetInteger")) {
            int query = (Integer) a[0];
            if (query == 32873) return boundTexture;
            if (query == 35725) return boundProgram;
            if (pixelStore.containsKey(query)) return pixelStore.get(query);
            if (query == 3379) return GL11C.glGetInteger(query);
            throw new UnsupportedOperationException("Unmapped legacy GL integer query: " + query);
        }
        if (name.equals("glGetString")) {
            int query = (Integer) a[0];
            if (query != 7936 && query != 7937 && query != 7938 && query != 35724)
                throw new UnsupportedOperationException("Unmapped legacy GL string query: " + query);
            String value = GL11C.glGetString(query);
            if (query == 7938) version = value;
            return value;
        }
        if (name.equals("glBindTexture")) {
            requireTextureTarget((Integer) a[0]); int handle = (Integer) a[1];
            if (handle < 0) throw new IllegalArgumentException("Invalid legacy texture name " + handle);
            int nativeTexture = handle == 0 ? 0 : textures.computeIfAbsent(handle, unused -> GL11C.glGenTextures());
            int previous = GL11C.glGetInteger(32873);
            try { GL11C.glBindTexture(3553, nativeTexture); checkError(operation); boundTexture = handle; }
            finally { GL11C.glBindTexture(3553, previous); }
            return null;
        }
        if (name.equals("glPixelStorei")) {
            int query = (Integer) a[0], value = (Integer) a[1];
            if (!pixelStore.containsKey(query) || value < 0 || (query == 3317 || query == 3333) && value != 1 && value != 2 && value != 4 && value != 8)
                throw new UnsupportedOperationException("Invalid or unsupported legacy pixel-store setting: " + query + "=" + value);
            pixelStore.put(query, value); return null;
        }
        if (name.equals("glTexEnvi")) {
            // Legacy GUI textures use GL_MODULATE, the host's position/texture/color shader semantics.
            if (!a[0].equals(8960) || !a[1].equals(8704) || !a[2].equals(8448))
                throw new UnsupportedOperationException("Unsupported fixed-function texture environment " + Arrays.toString(a));
            return null;
        }
        if (name.equals("glUseProgram")) {
            requireOwned(programs, (Integer) a[0], "program", true);
            int previous = GL11C.glGetInteger(35725);
            try { GL20C.glUseProgram((Integer) a[0]); checkError(operation); boundProgram = (Integer) a[0]; }
            finally { GL20C.glUseProgram(previous); }
            return null;
        }
        boolean textureOperation = name.contains("Texture") || name.startsWith("glTex") || name.startsWith("glGetTex") || name.equals("glGenerateMipmap");
        if (textureOperation) return texture(operation, name, a);
        return shader(operation, name, a);
    }

    private Object texture(String operation, String name, Object[] a) throws Exception {
        if (name.equals("glGenTextures")) {
            while (textures.containsKey(nextTexture)) nextTexture = Math.addExact(nextTexture, 1);
            int handle = nextTexture; nextTexture = Math.addExact(nextTexture, 1);
            textures.put(handle, GL11C.glGenTextures()); return handle;
        }
        if (name.equals("glDeleteTextures")) {
            int handle = (Integer) a[0]; Integer nativeTexture = textures.remove(handle);
            if (nativeTexture != null) GL11C.glDeleteTextures(nativeTexture);
            if (boundTexture == handle) boundTexture = 0; return null;
        }
        if (name.equals("glIsTexture")) return textures.containsKey((Integer) a[0]) && GL11C.glIsTexture(textures.get((Integer) a[0]));
        requireTextureTarget((Integer) a[0]);
        if (boundTexture == 0 || !textures.containsKey(boundTexture)) throw new IllegalStateException("No legacy texture is bound");
        int previous = GL11C.glGetInteger(32873);
        int previousUnpackBuffer = GL11C.glGetInteger(35055), previousPackBuffer = GL11C.glGetInteger(35053);
        var previousPixelStore = new LinkedHashMap<Integer, Integer>();
        try {
            GL11C.glBindTexture(3553, textures.get(boundTexture));
            GL15C.glBindBuffer(35052, 0); GL15C.glBindBuffer(35051, 0);
            for (var setting : pixelStore.entrySet()) {
                previousPixelStore.put(setting.getKey(), GL11C.glGetInteger(setting.getKey()));
                GL11C.glPixelStorei(setting.getKey(), setting.getValue());
            }
            Object[] args = a.clone();
            // GL_CLAMP was removed from core contexts. Font atlases sample inside their edges.
            if (name.equals("glTexParameteri") && (a[1].equals(10242) || a[1].equals(10243)) && a[2].equals(10496)) args[2] = 33071;
            Object value = invoke(name.equals("glGenerateMipmap") ? GL30C.class : GL11C.class, operation, args);
            checkError(operation);
            if (name.equals("glTexImage2D") || name.equals("glTexSubImage2D")) textureUploads++;
            return value;
        } finally {
            for (var setting : previousPixelStore.entrySet()) GL11C.glPixelStorei(setting.getKey(), setting.getValue());
            GL11C.glBindTexture(3553, previous);
            GL15C.glBindBuffer(35052, previousUnpackBuffer); GL15C.glBindBuffer(35051, previousPackBuffer);
        }
    }

    private Object shader(String operation, String name, Object[] a) throws Exception {
        if (name.equals("glCreateProgram")) { int id = GL20C.glCreateProgram(); programs.add(id); return id; }
        if (name.equals("glCreateShader")) { int id = GL20C.glCreateShader((Integer) a[0]); shaders.add(id); return id; }
        if (name.startsWith("glUniform")) {
            if ((Integer) a[0] == -1) return null; // The OpenGL-defined inactive uniform behavior.
            requireOwned(programs, boundProgram, "bound program", false);
        } else if (name.equals("glAttachShader") || name.equals("glDetachShader")) {
            requireOwned(programs, (Integer) a[0], "program", false); requireOwned(shaders, (Integer) a[1], "shader", false);
        } else if (name.contains("Program") || name.equals("glGetUniformLocation")) {
            requireOwned(programs, (Integer) a[0], "program", false);
        } else requireOwned(shaders, (Integer) a[0], "shader", false);
        int previous = GL11C.glGetInteger(35725);
        try {
            if (name.startsWith("glUniform")) GL20C.glUseProgram(boundProgram);
            Object value = invoke(GL20C.class, operation, a);
            checkError(operation);
            if (name.equals("glCompileShader") && GL20C.glGetShaderi((Integer) a[0], 35713) == 0)
                failure("Shader compilation failed: " + GL20C.glGetShaderInfoLog((Integer) a[0]));
            if (name.equals("glLinkProgram") && GL20C.glGetProgrami((Integer) a[0], 35714) == 0)
                failure("Program link failed: " + GL20C.glGetProgramInfoLog((Integer) a[0]));
            if (name.equals("glDeleteShader")) shaders.remove((Integer) a[0]);
            if (name.equals("glDeleteProgram")) { programs.remove((Integer) a[0]); if (boundProgram == (Integer) a[0]) boundProgram = 0; }
            return value;
        } finally { GL20C.glUseProgram(previous); }
    }

    private Object invoke(Class<?> api, String operation, Object[] arguments) throws Exception {
        Method method = methods.get(operation);
        if (method == null) {
            int descriptor = operation.indexOf('(');
            var type = MethodType.fromMethodDescriptorString(operation.substring(descriptor), getClass().getClassLoader());
            method = api.getMethod(operation.substring(0, descriptor), type.parameterArray()); methods.put(operation, method);
        }
        Object[] copy = arguments.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof ByteBuffer buffer) copy[i] = buffer.duplicate();
            if (copy[i] instanceof IntBuffer buffer) copy[i] = buffer.duplicate();
        }
        try { return method.invoke(null, copy); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }
    private void checkError(String operation) {
        int error = GL11C.glGetError();
        if (error != 0) {
            for (int i = 0; i < 8 && GL11C.glGetError() != 0; i++) { }
            throw new IllegalStateException(operation + " returned host GL error 0x" + Integer.toHexString(error));
        }
    }
    private void failure(String message) { failures.add(message); LogUtils.getLogger().warn("MFCL: {}", message); }
    private static void requireTextureTarget(int target) { if (target != 3553) throw new UnsupportedOperationException("Only legacy GL_TEXTURE_2D is bridged"); }
    private static void requireOwned(Set<Integer> owned, int id, String kind, boolean zeroAllowed) {
        if (!(id == 0 && zeroAllowed) && !owned.contains(id)) throw new IllegalArgumentException("Foreign or deleted legacy " + kind + " " + id);
    }
    @Override public Map<String, Object> verify(Class<?> gl11, Class<?> gl20) throws Exception {
        return render(() -> {
            int previousTexture = GL11C.glGetInteger(32873), previousProgram = GL11C.glGetInteger(35725);
            int previousAlignment = GL11C.glGetInteger(3317);
            var previousLegacyPixelStore = new LinkedHashMap<>(pixelStore);
            int sentinel = GL11C.glGenTextures(), texture = 0, program = 0, vertex = 0, fragment = 0;
            try {
                GL11C.glBindTexture(3553, sentinel); GL11C.glPixelStorei(3317, 8);
                int max = (Integer) gl(gl11, "glGetInteger", new Class<?>[]{int.class}, 3379);
                if (max < 2048) throw new AssertionError("Host texture capability is below Minecraft's requirement");
                texture = (Integer) gl(gl11, "glGenTextures", new Class<?>[0]);
                gl(gl11, "glBindTexture", new Class<?>[]{int.class, int.class}, 3553, texture);
                gl(gl11, "glPixelStorei", new Class<?>[]{int.class, int.class}, 3317, 1);
                gl(gl11, "glTexImage2D", new Class<?>[]{int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,ByteBuffer.class},
                        3553, 0, 6408, 1, 2, 0, 6408, 5121, null);
                var input = ByteBuffer.allocateDirect(12); input.position(2);
                byte[] pixels = {(byte)255, 0, 0, (byte)255, 0, (byte)255, 0, (byte)127};
                input.put(pixels); input.flip(); input.position(2);
                gl(gl11, "glTexSubImage2D", new Class<?>[]{int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,ByteBuffer.class},
                        3553, 0, 0, 0, 1, 2, 6408, 5121, input);
                var output = ByteBuffer.allocateDirect(8);
                gl(gl11, "glGetTexImage", new Class<?>[]{int.class,int.class,int.class,int.class,ByteBuffer.class}, 3553, 0, 6408, 5121, output);
                for (int i = 0; i < pixels.length; i++) if (output.get(i) != pixels[i]) throw new AssertionError("Legacy texture GPU round-trip mismatch");
                if (input.position() != 2 || output.position() != 0) throw new AssertionError("Legacy texture calls changed buffer positions");
                gl(gl11, "glDeleteTextures", new Class<?>[]{int.class}, texture);
                gl(gl11, "glBindTexture", new Class<?>[]{int.class,int.class}, 3553, texture);
                gl(gl11, "glTexImage2D", new Class<?>[]{int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,ByteBuffer.class},
                        3553, 0, 6408, 1, 2, 0, 6408, 5121, null);
                if (!Boolean.TRUE.equals(gl(gl11, "glIsTexture", new Class<?>[]{int.class}, texture))) throw new AssertionError("Deleted legacy texture could not be recreated under its old name");
                gl(gl11, "glDeleteTextures", new Class<?>[]{int.class}, sentinel);
                if (!GL11C.glIsTexture(sentinel)) throw new AssertionError("Legacy texture deletion affected the host");
                program = (Integer) gl(gl20, "glCreateProgram", new Class<?>[0]);
                vertex = (Integer) gl(gl20, "glCreateShader", new Class<?>[]{int.class}, 35633);
                fragment = (Integer) gl(gl20, "glCreateShader", new Class<?>[]{int.class}, 35632);
                for (int shader : new int[]{vertex, fragment}) {
                    String source = shader == vertex ? "#version 150\nvoid main(){gl_Position=vec4(0,0,0,1);}" : "#version 150\nuniform vec4 tint;out vec4 color;void main(){color=tint;}";
                    gl(gl20, "glShaderSource", new Class<?>[]{int.class,CharSequence.class}, shader, source);
                    gl(gl20, "glCompileShader", new Class<?>[]{int.class}, shader);
                    if (!Integer.valueOf(1).equals(gl(gl20, "glGetShaderi", new Class<?>[]{int.class,int.class}, shader, 35713))) throw new AssertionError("Native shader compilation failed");
                    gl(gl20, "glAttachShader", new Class<?>[]{int.class,int.class}, program, shader);
                }
                gl(gl20, "glLinkProgram", new Class<?>[]{int.class}, program);
                if (!Integer.valueOf(1).equals(gl(gl20, "glGetProgrami", new Class<?>[]{int.class,int.class}, program, 35714))) throw new AssertionError("Native shader link failed");
                gl(gl20, "glUseProgram", new Class<?>[]{int.class}, program);
                int uniform = (Integer) gl(gl20, "glGetUniformLocation", new Class<?>[]{int.class,CharSequence.class}, program, "tint");
                gl(gl20, "glUniform4f", new Class<?>[]{int.class,float.class,float.class,float.class,float.class}, uniform, 0.25f, 0.5f, 0.75f, 1f);
                var uniformValue = ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                GL20C.glGetUniformfv(program, uniform, uniformValue);
                if (uniformValue.get(0) != 0.25f || uniformValue.get(1) != 0.5f || uniformValue.get(2) != 0.75f || uniformValue.get(3) != 1f)
                    throw new AssertionError("Legacy uniform did not reach its GPU program");
                if (GL11C.glGetInteger(32873) != sentinel || GL11C.glGetInteger(3317) != 8 || GL11C.glGetInteger(35725) != previousProgram)
                    throw new AssertionError("Legacy GL calls changed host texture, program or unpack state");
                checkError("host GL verification");
                LogUtils.getLogger().info("MFCL_HOST_OPENGL_SMOKE_PASS: private LWJGL calls, GPU pixel round-trip, native shader/uniform, texture recreation, host-state restoration");
                return Map.<String, Object>of("status", "PASS", "maxTextureSize", max, "textureRoundTrip", true, "shaderUniform", true,
                        "textureRecreation", true, "hostTextureIsolated", true, "hostStateRestored", true);
            } finally {
                if (program != 0) { gl(gl20, "glUseProgram", new Class<?>[]{int.class}, 0); gl(gl20, "glDeleteProgram", new Class<?>[]{int.class}, program); }
                if (vertex != 0) gl(gl20, "glDeleteShader", new Class<?>[]{int.class}, vertex);
                if (fragment != 0) gl(gl20, "glDeleteShader", new Class<?>[]{int.class}, fragment);
                if (texture != 0) gl(gl11, "glDeleteTextures", new Class<?>[]{int.class}, texture);
                pixelStore.clear(); pixelStore.putAll(previousLegacyPixelStore);
                GL11C.glBindTexture(3553, previousTexture); GL11C.glPixelStorei(3317, previousAlignment); GL11C.glDeleteTextures(sentinel);
            }
        });
    }
    private static Object gl(Class<?> type, String name, Class<?>[] parameters, Object... arguments) throws Exception {
        try { return type.getMethod(name, parameters).invoke(null, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }
    @Override public Map<String, Object> statistics() {
        try { return render(() -> Map.of("backend", "HOST_OPENGL", "textures", textures.size(), "textureUploads", textureUploads,
                "programs", programs.size(), "shaders", shaders.size(), "version", version, "failures", List.copyOf(failures)));
        } catch (Exception failure) { return Map.of("statisticsFailure", failure.toString()); }
    }
    @Override public void close() throws Exception {
        render(() -> {
            if (!closed) {
                for (int id : textures.values()) GL11C.glDeleteTextures(id);
                for (int id : shaders) GL20C.glDeleteShader(id);
                for (int id : programs) GL20C.glDeleteProgram(id);
                textures.clear(); shaders.clear(); programs.clear(); closed = true;
            }
            return null;
        });
    }
}
