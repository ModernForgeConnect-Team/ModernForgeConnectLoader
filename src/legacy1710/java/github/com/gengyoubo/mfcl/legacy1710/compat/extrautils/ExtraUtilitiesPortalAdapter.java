package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;

/** Redirect only XU's portal and cached particle renderer; no running 1.7 server is created. */
public final class ExtraUtilitiesPortalAdapter implements LegacyBytecodeAdapter {
    private static final String TRAVEL = "github/com/gengyoubo/mfcl/bridge/LegacyTravel";
    public boolean targets(String name) {
        return name.equals("com.rwtema.extrautils.block.BlockPortal") || name.equals("com.rwtema.extrautils.particle.ParticleHelperClient")
                || name.equals("com.rwtema.extrautils.worldgen.endoftime.TeleporterEndOfTime")
                || name.equals("com.rwtema.extrautils.item.ItemGlove")||name.equals("com.rwtema.extrautils.item.ItemSoul")
                || name.equals("com.rwtema.extrautils.item.ItemGoldenLasso")||name.equals("com.rwtema.extrautils.item.ItemPrecisionShears")
                || name.equals("net.minecraftforge.common.ForgeChunkManager");
    }
    public byte[] adapt(String name, byte[] bytes) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                    @Override public void visitCode(){
                        super.visitCode();
                        if(name.equals("net.minecraftforge.common.ForgeChunkManager")&&(method.equals("requestTicket")||method.equals("requestPlayerTicket")||method.equals("ticketCountAvailableFor")&&descriptor.contains("Lnet/minecraft/world/World;"))){
                            super.visitVarInsn(Opcodes.ALOAD,method.equals("requestPlayerTicket")?2:1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyChunkTickets","ensure","(Ljava/lang/Object;)V",false);
                        }
                    }
                    @Override public void visitInsn(int opcode){
                        if(opcode==Opcodes.RETURN&&name.equals("net.minecraftforge.common.ForgeChunkManager")&&(method.equals("releaseTicket")||method.equals("forceChunk")||method.equals("unforceChunk")||method.equals("reorderChunk"))){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitInsn(method.equals("releaseTicket")?Opcodes.ICONST_1:Opcodes.ICONST_0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyChunkTickets","update","(Ljava/lang/Object;Z)V",false);
                        }
                        super.visitInsn(opcode);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean itf) {
                        if(name.equals("com.rwtema.extrautils.item.ItemGoldenLasso")&&called.equals("func_98035_c"))
                            replace("github/com/gengyoubo/mfcl/bridge/LegacyEntityCapture","write","(Ljava/lang/Object;Ljava/lang/Object;)Z",null);
                        else if(name.equals("com.rwtema.extrautils.item.ItemGoldenLasso")&&called.equals("func_70106_y"))
                            replace("github/com/gengyoubo/mfcl/bridge/LegacyEntityCapture","remove","(Ljava/lang/Object;)V",null);
                        else if(owner.equals("net/minecraft/entity/EntityList")&&called.equals("func_75615_a"))
                            replace("github/com/gengyoubo/mfcl/bridge/LegacyEntityCapture","read","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;","net/minecraft/entity/Entity");
                        else if (owner.equals("com/rwtema/extrautils/helper/XUHelper") && called.equals("isPlayerFake")) {
                            replace(TRAVEL,"hostPlayer","(Ljava/lang/Object;)Z",null);
                            super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IXOR);
                        }
                        else if (owner.equals("net/minecraft/server/MinecraftServer") && called.equals("func_71203_ab"))
                            replace(TRAVEL, "configurationManager", "(Ljava/lang/Object;)Ljava/lang/Object;", "net/minecraft/server/management/ServerConfigurationManager");
                        else if (owner.equals("net/minecraft/server/MinecraftServer") && called.equals("func_71218_a"))
                            replace(TRAVEL, "serverWorld", "(Ljava/lang/Object;I)Ljava/lang/Object;", "net/minecraft/world/WorldServer");
                        else if (owner.equals("net/minecraft/server/management/ServerConfigurationManager") && called.equals("transferPlayerToDimension"))
                            replace(TRAVEL, "transferPlayer", "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;)V", null);
                        else if(owner.equals("net/minecraft/server/management/ServerConfigurationManager")&&called.equals("func_72385_f"))
                            replace("github/com/gengyoubo/mfcl/bridge/LegacyPlayerInventory","synchronize","(Ljava/lang/Object;Ljava/lang/Object;)V",null);
                        else if(owner.equals("net/minecraftforge/common/util/FakePlayerFactory")&&called.equals("getMinecraft")&&method.equals("func_85188_a")) {
                            // Villager recipe initialization accepts the arrival actor; the obsolete factory needs a live old server.
                            super.visitInsn(Opcodes.POP);super.visitVarInsn(Opcodes.ALOAD,1);super.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer");
                        }
                        else super.visitMethodInsn(opcode, owner, called, desc, itf);
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        if(opcode==Opcodes.GETFIELD&&owner.equals("net/minecraft/entity/player/EntityPlayerMP")&&field.equals("field_71133_b"))
                            replace(TRAVEL,"legacyServer","(Ljava/lang/Object;)Ljava/lang/Object;","net/minecraft/server/MinecraftServer");
                        else if (opcode == Opcodes.GETSTATIC && owner.equals("com/rwtema/extrautils/particle/ParticleHelperClient") && field.equals("effectRenderer")) {
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
