package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.util.*;

/** Typed old pickaxe identity; the host retains ownership of per-stack data and wear semantics. */
public final class LegacyHostTools {
    public static final String PICKAXE = "mfcl_host:tool/pickaxe";
    public interface Access {
        int maxDamage(Object stack) throws Exception;
        boolean usable(Object stack) throws Exception;
        Object damage(Object stack, int amount, Object world) throws Exception;
    }
    private static final Map<Object, Access> TOOLS = Collections.synchronizedMap(new WeakHashMap<>());
    private LegacyHostTools() {}
    static void close(ClassLoader loader) {
        synchronized (TOOLS) { TOOLS.keySet().removeIf(item -> item.getClass().getClassLoader() == loader); }
    }

    public static void install(LegacyServices services, Access access) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            String name = "github/com/gengyoubo/mfcl/legacy1710/HostPickaxe";
            String parent = "net/minecraft/item/ItemPickaxe", material = "net/minecraft/item/Item$ToolMaterial";
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, parent, null);
            var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitFieldInsn(Opcodes.GETSTATIC, material, "IRON", "L" + material + ";");
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "(L" + material + ";)V", false);
            ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
            var max = writer.visitMethod(Opcodes.ACC_PUBLIC, "getMaxDamage", "(Lnet/minecraft/item/ItemStack;)I", null, null);
            max.visitCode(); max.visitVarInsn(Opcodes.ALOAD, 1);
            max.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(LegacyHostTools.class), "maxDamage", "(Ljava/lang/Object;)I", false);
            max.visitInsn(Opcodes.IRETURN); max.visitMaxs(0, 0); max.visitEnd(); writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            var type = (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class)
                    .invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
            Object item = type.getConstructor().newInstance();
            LegacyHostRegistry.registerItem(loader, PICKAXE, item);
            TOOLS.put(item, access);
            return null;
        });
    }
    private static Access access(Object stack) {
        if (stack == null) return null;
        try { return TOOLS.get(stack.getClass().getMethod("func_77973_b").invoke(stack)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot resolve host tool", failure); }
    }
    public static boolean supports(Object stack) { return access(stack) != null; }
    public static int maxDamage(Object stack) {
        try { return access(stack).maxDamage(stack); }
        catch (Exception failure) { throw new IllegalStateException("Cannot read host tool durability", failure); }
    }
    public static boolean usable(Object stack) {
        try { return access(stack).usable(stack); }
        catch (Exception failure) { throw new IllegalStateException("Cannot read host tool state", failure); }
    }
    public static Object damage(int amount, Object stack, Object world) {
        try { return access(stack).damage(stack, amount, world); }
        catch (Exception failure) { throw new IllegalStateException("Cannot damage host tool", failure); }
    }
}
