package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.BytecodePatches;
import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** XU's ranged packets (ender quarry dig particles, sounds...) reach host players around the point; there is no old server list. */
public final class ExtraUtilitiesNetworkAdapter implements LegacyBytecodeAdapter {
    private static final String HANDLER = "com.rwtema.extrautils.network.NetworkHandler";
    public boolean targets(String name) { return name.equals(HANDLER); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassNode node = BytecodePatches.read(bytes); boolean patched = false;
        for (MethodNode method : node.methods)
            if (BytecodePatches.matches(method, "sendToAllAround", "(Lcom/rwtema/extrautils/network/XUPacketBase;Lcpw/mods/fml/common/network/NetworkRegistry$TargetPoint;)V")) {
                InsnList arguments = new InsnList();
                arguments.add(new VarInsnNode(Opcodes.ALOAD, 0)); arguments.add(new VarInsnNode(Opcodes.ALOAD, 1));
                arguments.add(new LdcInsnNode(HANDLER)); arguments.add(new LdcInsnNode("sendPacketToPlayer"));
                BytecodePatches.handledPrefix(method, arguments, "github/com/gengyoubo/mfcl/bridge/LegacyPackets", "sendAround",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z");
                patched = true;
            }
        if (!patched) throw new IllegalStateException("Cannot find XU NetworkHandler.sendToAllAround(XUPacketBase,TargetPoint)");
        return BytecodePatches.write(node);
    }
}
