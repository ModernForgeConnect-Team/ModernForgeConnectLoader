package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Redirects reflection call sites in the private domain, without changing JDK classes or launch flags. */
public final class LegacyReflectionTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        String constants = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!constants.contains("getDeclaredField") && !constants.contains("setAccessible")) return bytes;
        final boolean[] changed = {false};
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class") && method.equals("getDeclaredField")
                                && descriptor.equals("(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/LegacyReflection", "declaredField",
                                    "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;", false); changed[0] = true;
                        } else if (opcode == Opcodes.INVOKEVIRTUAL && method.equals("setAccessible") && descriptor.equals("(Z)V")
                                && (owner.equals("java/lang/reflect/Field") || owner.equals("java/lang/reflect/Method")
                                || owner.equals("java/lang/reflect/Constructor") || owner.equals("java/lang/reflect/AccessibleObject"))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/LegacyReflection", "accessible",
                                    "(Ljava/lang/reflect/AccessibleObject;Z)V", false); changed[0] = true;
                        } else super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : bytes;
    }
}
