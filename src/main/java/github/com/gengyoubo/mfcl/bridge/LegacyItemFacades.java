package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;

/** Read-only old Item instances for host catalog entries. They are never registered or serialized into an old world. */
final class LegacyItemFacades {
    static Class<?> define(ClassLoader loader) throws Exception {
        String name = "github/com/gengyoubo/mfcl/legacy1710/HostItemFacade";
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "net/minecraft/item/Item", null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "displayName", "Ljava/lang/String;", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;II)V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/Item", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, name, "displayName", "Ljava/lang/String;");
        for (var property : java.util.List.of(new String[]{"func_77625_d", "2"}, new String[]{"func_77656_e", "3"})) {
            constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ILOAD, Integer.parseInt(property[1]));
            constructor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/Item", property[0], "(I)Lnet/minecraft/item/Item;", false);
            constructor.visitInsn(Opcodes.POP);
        }
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(2, 4); constructor.visitEnd();
        var displayName = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_77653_i", "(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;", null, null);
        displayName.visitCode(); displayName.visitVarInsn(Opcodes.ALOAD, 0); displayName.visitFieldInsn(Opcodes.GETFIELD, name, "displayName", "Ljava/lang/String;");
        displayName.visitInsn(Opcodes.ARETURN); displayName.visitMaxs(1, 2); displayName.visitEnd(); writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        return (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class)
                .invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
    }
}
