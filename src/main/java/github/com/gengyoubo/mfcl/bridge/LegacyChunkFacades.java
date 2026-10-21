package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;

/** Typed old chunks whose block and tile data belongs to the host world rather than old section arrays. */
final class LegacyChunkFacades {
    private static final String NAME = "github/com/gengyoubo/mfcl/legacy1710/HostChunkFacade";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String WORLD = "net/minecraft/world/World";

    static Object create(ClassLoader loader, Object world, int x, int z) throws ReflectiveOperationException {
        Class<?> type;
        synchronized (loader) {
            try { type = loader.loadClass(NAME.replace('/', '.')); }
            catch (ClassNotFoundException absent) { type = define(loader); }
        }
        return type.getConstructor(loader.loadClass(WORLD.replace('/', '.')), int.class, int.class).newInstance(world, x, z);
    }

    private static Class<?> define(ClassLoader loader) throws ReflectiveOperationException {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, NAME, null, CHUNK, null);
        String signature = "(L" + WORLD + ";II)V";
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", signature, null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ILOAD, 2); constructor.visitVarInsn(Opcodes.ILOAD, 3);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, CHUNK, "<init>", signature, false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(4, 4); constructor.visitEnd();
        forward(writer, "func_150810_a", "(III)Lnet/minecraft/block/Block;", "func_147439_a", 3, false);
        forward(writer, "func_76628_c", "(III)I", "func_72805_g", 3, false);
        forward(writer, "func_150807_a", "(IIILnet/minecraft/block/Block;I)Z", "func_147465_d", 3, true);
        forward(writer, "func_76589_b", "(IIII)Z", "func_72921_c", 3, true);
        forward(writer, "func_150806_e", "(III)Lnet/minecraft/tileentity/TileEntity;", "func_147438_o", 3, false);
        forward(writer, "getTileEntityUnsafe", "(III)Lnet/minecraft/tileentity/TileEntity;", "func_147438_o", 3, false);
        forward(writer, "func_150812_a", "(IIILnet/minecraft/tileentity/TileEntity;)V", "func_147455_a", 3, false);
        forward(writer, "func_150805_f", "(III)V", "func_147475_p", 3, false);
        forward(writer, "func_76611_b", "(II)I", "func_72976_f", 2, false);
        forward(writer, "func_76626_d", "(II)I", "func_72976_f", 2, false);
        forward(writer, "func_76619_d", "(III)Z", "func_72937_j", 3, false);
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        return (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class)
                .invoke(loader, NAME.replace('/', '.'), bytes, 0, bytes.length);
    }

    private static void forward(ClassWriter writer, String name, String descriptor, String target, int coordinates, boolean flags) {
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, CHUNK, "field_76637_e", "L" + WORLD + ";");
        coordinate(method, "field_76635_g", 1);
        if (coordinates == 3) method.visitVarInsn(Opcodes.ILOAD, 2); // Preserve the absolute y, including negatives.
        coordinate(method, "field_76647_h", coordinates);
        Type[] arguments = Type.getArgumentTypes(descriptor);
        int slot = coordinates + 1;
        for (int i = coordinates; i < arguments.length; i++) {
            method.visitVarInsn(arguments[i].getOpcode(Opcodes.ILOAD), slot); slot += arguments[i].getSize();
        }
        // Raw Chunk writes leave world notifications to their caller, just like the original implementation.
        if (flags) method.visitInsn(Opcodes.ICONST_0);
        String targetDescriptor = flags ? descriptor.replace(")", "I)") : descriptor;
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WORLD, target, targetDescriptor, false);
        method.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN));
        method.visitMaxs(0, 0); method.visitEnd();
    }

    private static void coordinate(MethodVisitor method, String field, int slot) {
        method.visitVarInsn(Opcodes.ALOAD, 0); method.visitFieldInsn(Opcodes.GETFIELD, CHUNK, field, "I");
        method.visitIntInsn(Opcodes.BIPUSH, 16); method.visitInsn(Opcodes.IMUL);
        method.visitVarInsn(Opcodes.ILOAD, slot); method.visitInsn(Opcodes.IADD);
    }
}
