package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;

/** Host worlds never fire the old WorldEvent.Load that seeds ForgeChunkManager's per-world maps; seed them on first request
 * and mirror every original ticket change onto host region tickets. Applies to every mod that keeps chunks loaded. */
public final class ForgeChunkManagerAdapter implements LegacyBytecodeAdapter {
    private static final String TICKETS = "github/com/gengyoubo/mfcl/bridge/LegacyChunkTickets";
    public boolean targets(String name) { return name.equals("net.minecraftforge.common.ForgeChunkManager"); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                    @Override public void visitCode() {
                        super.visitCode();
                        if (method.equals("requestTicket") || method.equals("requestPlayerTicket") || method.equals("ticketCountAvailableFor") && descriptor.contains("Lnet/minecraft/world/World;")) {
                            super.visitVarInsn(Opcodes.ALOAD, method.equals("requestPlayerTicket") ? 2 : 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, TICKETS, "ensure", "(Ljava/lang/Object;)V", false);
                        }
                    }
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN && (method.equals("releaseTicket") || method.equals("forceChunk") || method.equals("unforceChunk") || method.equals("reorderChunk"))) {
                            super.visitVarInsn(Opcodes.ALOAD, 0); super.visitInsn(method.equals("releaseTicket") ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, TICKETS, "update", "(Ljava/lang/Object;Z)V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
