package github.com.gengyoubo.mfcl.legacy.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;
import org.objectweb.asm.*;

/** An isolated tool root. No fallback to the host's Minecraft, Forge or libraries. */
public final class IsolatedLegacyClassLoader extends URLClassLoader {
    static { registerAsParallelCapable(); }
    private final ClassLoader bridgeOwner;

    public IsolatedLegacyClassLoader(URL[] inputs, ClassLoader bridgeOwner) {
        super("mfcl-legacy-1710-tools", inputs, ClassLoader.getPlatformClassLoader());
        this.bridgeOwner = Objects.requireNonNull(bridgeOwner);
    }

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!name.equals("org.apache.logging.log4j.core.impl.Log4jContextFactory")) return super.findClass(name);
        // Host ModLauncher selects its own log context through a JVM property. The isolated copy owns its context.
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) throw new ClassNotFoundException(name);
        try {
            var connection = resource.openConnection(); connection.setUseCaches(false);
            byte[] bytes; try (var in = connection.getInputStream()) { bytes = in.readAllBytes(); }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                    var method = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    if (!methodName.equals("createContextSelector") || !descriptor.equals("()Lorg/apache/logging/log4j/core/selector/ContextSelector;")) return method;
                    method.visitCode(); method.visitTypeInsn(Opcodes.NEW, "org/apache/logging/log4j/core/selector/BasicContextSelector");
                    method.visitInsn(Opcodes.DUP); method.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/apache/logging/log4j/core/selector/BasicContextSelector", "<init>", "()V", false);
                    method.visitInsn(Opcodes.ARETURN); method.visitMaxs(2, 0); method.visitEnd(); return null;
                }
            }, 0);
            byte[] transformed = writer.toByteArray(); return defineClass(name, transformed, 0, transformed.length);
        } catch (java.io.IOException error) { throw new ClassNotFoundException(name, error); }
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                if (name.startsWith("github.com.gengyoubo.mfcl.bridge.")) type = bridgeOwner.loadClass(name);
                else if (name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.")) type = getParent().loadClass(name);
                else {
                    try { type = findClass(name); }
                    catch (ClassNotFoundException absent) { type = getParent().loadClass(name); }
                }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }
}
