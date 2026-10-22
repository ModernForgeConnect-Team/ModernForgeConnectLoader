package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Forge's FakePlayer constructor reads the absent old MinecraftServer; every fake player becomes a host-backed actor instead. */
public final class FakePlayerAdapter implements LegacyBytecodeAdapter {
    private static final String GET = "(Lnet/minecraft/world/WorldServer;Lcom/mojang/authlib/GameProfile;)Lnet/minecraftforge/common/util/FakePlayer;";
    public boolean targets(String name) { return name.equals("net.minecraftforge.common.util.FakePlayerFactory"); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassNode node = BytecodePatches.read(bytes); boolean patched = false;
        for (MethodNode method : node.methods) if (BytecodePatches.matches(method, "get", GET)) {
            InsnList body = new InsnList();
            body.add(new VarInsnNode(Opcodes.ALOAD, 0)); body.add(new VarInsnNode(Opcodes.ALOAD, 1));
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/bridge/LegacyTiles", "fakePlayer", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
            body.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraftforge/common/util/FakePlayer")); body.add(new InsnNode(Opcodes.ARETURN));
            method.instructions = body; method.tryCatchBlocks.clear(); method.localVariables = null; patched = true;
        }
        if (!patched) throw new IllegalStateException("Cannot find FakePlayerFactory.get(WorldServer,GameProfile)");
        return BytecodePatches.write(node);
    }
}
