package github.com.gengyoubo.mfcl.idea;

import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;

public final class LegacyStubJarTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("mfcl-stub-test"), input = root.resolve("old.jar");
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "old/ForgeDirection", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "offsetX", "I", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "test", "()V", null, null).visitEnd();
        writer.visitEnd();
        try (var jar = new JarOutputStream(Files.newOutputStream(input))) {
            jar.putNextEntry(new JarEntry("old/ForgeDirection.class")); jar.write(writer.toByteArray()); jar.closeEntry();
        }
        var result = LegacyStubJar.create(input, root.resolve("stubs"), "ForgeDirection");
        if (!result.matches().equals(java.util.List.of("old.ForgeDirection"))) throw new AssertionError(result.matches());
        String hash = result.jar().getFileName().toString().replace(".jar", "");
        if (!Files.readString(root.resolve("stubs").resolve(hash + ".origin")).equals(input.toAbsolutePath().normalize().toString()))
            throw new AssertionError("Original JAR location not recorded");
        try (var jar = new JarFile(result.jar().toFile()); var in = jar.getInputStream(jar.getJarEntry("old/ForgeDirection.class"))) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) { throw new AssertionError("field retained"); }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) { throw new AssertionError("method retained"); }
            }, 0);
        }
        try { LegacyStubJar.create(input, root.resolve("stubs"), "Missing"); throw new AssertionError("Missing class accepted"); }
        catch (java.io.IOException expected) { /* expected */ }
    }
}
