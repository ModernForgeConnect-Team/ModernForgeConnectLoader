package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;

/**
 * TC4's eldritch portal moves players through the old MinecraftServer, which does not exist in the legacy domain.
 * Its server/manager/world lookups and the transfer itself go to MFCL's LegacyTravel; the host moves the real player.
 */
public final class ThaumcraftPortalAdapter implements LegacyBytecodeAdapter {
    private static final String TRAVEL = "github/com/gengyoubo/mfcl/bridge/LegacyTravel";
    public boolean targets(String name) { return name.equals("thaumcraft.common.tiles.TileEldritchPortal"); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String calledDescriptor, boolean itf) {
                        if (owner.equals("cpw/mods/fml/common/FMLCommonHandler") && called.equals("getMinecraftServerInstance")) replace("legacyServer", "(Ljava/lang/Object;)Ljava/lang/Object;", "net/minecraft/server/MinecraftServer");
                        else if (owner.equals("net/minecraft/server/MinecraftServer") && called.equals("func_71203_ab")) replace("configurationManager", "(Ljava/lang/Object;)Ljava/lang/Object;", "net/minecraft/server/management/ServerConfigurationManager");
                        else if (owner.equals("net/minecraft/server/MinecraftServer") && called.equals("func_71218_a")) replace("serverWorld", "(Ljava/lang/Object;I)Ljava/lang/Object;", "net/minecraft/world/WorldServer");
                        else if (owner.equals("net/minecraft/server/management/ServerConfigurationManager") && called.equals("transferPlayerToDimension"))
                            replace("transferPlayer", "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;)V", null);
                        else super.visitMethodInsn(opcode, owner, called, calledDescriptor, itf);
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String fieldDescriptor) {
                        if (opcode == Opcodes.PUTFIELD && owner.equals("net/minecraft/entity/player/EntityPlayerMP") && field.equals("field_71088_bW"))
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, TRAVEL, "portalCooldown", "(Ljava/lang/Object;I)V", false);
                        else super.visitFieldInsn(opcode, owner, field, fieldDescriptor);
                    }
                    private void replace(String bridge, String bridgeDescriptor, String cast) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, TRAVEL, bridge, bridgeDescriptor, false);
                        if (cast != null) super.visitTypeInsn(Opcodes.CHECKCAST, cast);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
