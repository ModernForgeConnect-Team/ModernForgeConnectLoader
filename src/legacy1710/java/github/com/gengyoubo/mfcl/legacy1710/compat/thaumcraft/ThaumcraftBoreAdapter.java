package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Keep TC4's mining logic; native pickaxes supply their own availability and wear. */
public final class ThaumcraftBoreAdapter implements LegacyBytecodeAdapter {
    private static final String OWNER = "thaumcraft/common/tiles/TileArcaneBore";
    private static final String HOST = "github/com/gengyoubo/mfcl/bridge/LegacyHostTools";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String DAMAGE = "(I" + STACK + "Lnet/minecraft/world/World;)" + STACK;
    public boolean targets(String name) { return name.equals(OWNER.replace('/', '.')); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassNode node = BytecodePatches.read(bytes); boolean valid = false, wear = false;
        for (MethodNode method : node.methods) {
            if (BytecodePatches.matches(method, "areItemsValid", "()Z")) {
                InsnList prefix = new InsnList(); LabelNode original = new LabelNode();
                pick(prefix);
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST, "supports", "(Ljava/lang/Object;)Z", false));
                prefix.add(new JumpInsnNode(Opcodes.IFEQ, original));
                prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
                prefix.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "hasFocus", "Z"));
                pick(prefix);
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST, "usable", "(Ljava/lang/Object;)Z", false));
                prefix.add(new InsnNode(Opcodes.IAND)); prefix.add(new InsnNode(Opcodes.IRETURN));
                prefix.add(original); prefix.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(prefix); valid = true;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("thaumcraft/common/lib/utils/InventoryUtils") && call.name.equals("damageItem") && call.desc.equals(DAMAGE)) {
                    call.owner = OWNER; call.name = "mfcl$damagePickaxe"; wear = true;
                }
            }
        }
        if (!valid || !wear) throw new IllegalStateException("Cannot locate TC4 bore validity/wear hooks");
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "mfcl$damagePickaxe", DAMAGE, null, null);
        InsnList body = method.instructions; LabelNode original = new LabelNode();
        body.add(new VarInsnNode(Opcodes.ALOAD, 1));
        body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST, "supports", "(Ljava/lang/Object;)Z", false));
        body.add(new JumpInsnNode(Opcodes.IFEQ, original));
        arguments(body);
        body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOST, "damage", "(ILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
        body.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/item/ItemStack")); body.add(new InsnNode(Opcodes.ARETURN));
        body.add(original); body.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        arguments(body);
        body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "thaumcraft/common/lib/utils/InventoryUtils", "damageItem", DAMAGE, false));
        body.add(new InsnNode(Opcodes.ARETURN)); node.methods.add(method);
        return BytecodePatches.write(node);
    }
    private static void pick(InsnList code) {
        code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(new InsnNode(Opcodes.ICONST_1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "func_70301_a", "(I)" + STACK, false));
    }
    private static void arguments(InsnList code) {
        code.add(new VarInsnNode(Opcodes.ILOAD, 0)); code.add(new VarInsnNode(Opcodes.ALOAD, 1)); code.add(new VarInsnNode(Opcodes.ALOAD, 2));
    }
}
