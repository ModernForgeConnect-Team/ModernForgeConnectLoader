package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;

/** Opaque native inventory stacks: old containers can move them without treating them as empty. */
public final class LegacyHostStacks {
    public static final String ITEM = "mfcl_host:inventory/stack";
    public static final String LIMIT = "mfcl_stack_limit";
    private LegacyHostStacks() {}

    public static void install(LegacyServices services) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            String name = "github/com/gengyoubo/mfcl/legacy1710/HostInventoryItem";
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "net/minecraft/item/Item", null);
            var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/Item", "<init>", "()V", false);
            ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
            var limit = writer.visitMethod(Opcodes.ACC_PUBLIC, "getItemStackLimit", "(Lnet/minecraft/item/ItemStack;)I", null, null);
            limit.visitCode(); limit.visitVarInsn(Opcodes.ALOAD, 1);
            limit.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(LegacyHostStacks.class), "stackLimit", "(Ljava/lang/Object;)I", false);
            limit.visitInsn(Opcodes.IRETURN); limit.visitMaxs(0, 0); limit.visitEnd(); writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            var type = (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class)
                    .invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
            LegacyHostRegistry.registerItem(loader, ITEM, type.getConstructor().newInstance());
            return null;
        });
    }

    public static int stackLimit(Object stack) {
        try {
            Object tag = stack.getClass().getMethod("func_77978_p").invoke(stack);
            return tag == null ? 1 : Math.max(1, Math.min(64, (int) tag.getClass().getMethod("func_74762_e", String.class).invoke(tag, LIMIT)));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot read native stack limit", failure); }
    }
}
