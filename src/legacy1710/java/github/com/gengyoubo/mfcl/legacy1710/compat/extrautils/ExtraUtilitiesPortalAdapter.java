package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;

/** Redirect only XU's portal and cached particle renderer; no running 1.7 server is created. */
public final class ExtraUtilitiesPortalAdapter implements LegacyBytecodeAdapter {
    private static final String TRAVEL = "github/com/gengyoubo/mfcl/bridge/LegacyTravel";
    public boolean targets(String name) {
        return name.equals("com.rwtema.extrautils.block.BlockPortal") || name.equals("com.rwtema.extrautils.particle.ParticleHelperClient")
                || name.equals("com.rwtema.extrautils.worldgen.endoftime.TeleporterEndOfTime");
    }
    public byte[] adapt(String name, byte[] bytes) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean itf) {
                        if (owner.equals("com/rwtema/extrautils/helper/XUHelper") && called.equals("isPlayerFake")) {
                            replace(TRAVEL,"hostPlayer","(Ljava/lang/Object;)Z",null);
                            super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IXOR);
                        }
                        else if (owner.equals("net/minecraft/server/MinecraftServer") && called.equals("func_71203_ab"))
                            replace(TRAVEL, "configurationManager", "(Ljava/lang/Object;)Ljava/lang/Object;", "net/minecraft/server/management/ServerConfigurationManager");
                        else if (owner.equals("net/minecraft/server/MinecraftServer") && called.equals("func_71218_a"))
                            replace(TRAVEL, "serverWorld", "(Ljava/lang/Object;I)Ljava/lang/Object;", "net/minecraft/world/WorldServer");
                        else if (owner.equals("net/minecraft/server/management/ServerConfigurationManager") && called.equals("transferPlayerToDimension"))
                            replace(TRAVEL, "transferPlayer", "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;)V", null);
                        else if(owner.equals("net/minecraftforge/common/util/FakePlayerFactory")&&called.equals("getMinecraft")&&method.equals("func_85188_a")) {
                            // Villager recipe initialization accepts the arrival actor; the obsolete factory needs a live old server.
                            super.visitInsn(Opcodes.POP);super.visitVarInsn(Opcodes.ALOAD,1);super.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer");
                        }
                        else super.visitMethodInsn(opcode, owner, called, desc, itf);
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals("com/rwtema/extrautils/particle/ParticleHelperClient") && field.equals("effectRenderer")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/client/Minecraft","func_71410_x","()Lnet/minecraft/client/Minecraft;",false);
                            super.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/client/Minecraft","field_71452_i",desc);
                        } else super.visitFieldInsn(opcode,owner,field,desc);
                    }
                    private void replace(String owner, String bridge, String desc, String cast) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,owner,bridge,desc,false);
                        if(cast!=null)super.visitTypeInsn(Opcodes.CHECKCAST,cast);
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }
}
