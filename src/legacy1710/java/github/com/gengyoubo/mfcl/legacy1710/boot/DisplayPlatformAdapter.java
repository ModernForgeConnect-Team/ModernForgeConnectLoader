package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Read-only view of the host window. The embedded game never owns a native display. */
public final class DisplayPlatformAdapter implements LegacyBytecodeAdapter {
    private static final String BRIDGE = "github/com/gengyoubo/mfcl/bridge/LegacyOpenGl";
    private static final String STATE = BRIDGE + "$DisplayState";

    @Override public boolean targets(String name) { return name.equals("org.lwjgl.opengl.Display"); }

    @Override public byte[] adapt(String name, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (method.name.equals("<init>")) continue;
            method.access &= ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            InsnList code = method.instructions;
            // LWJGL2's original initializer creates a platform DisplayImplementation and loads natives.
            if (method.name.equals("<clinit>")) {
                code.add(new InsnNode(Opcodes.RETURN));
                continue;
            }
            String query = query(method.name + method.desc);
            if (query != null) {
                code.add(new LdcInsnNode(Type.getObjectType("org/lwjgl/opengl/GL11")));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "displayState", "(Ljava/lang/Class;)L" + STATE + ";", false));
                code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, STATE, query, method.desc, false));
                code.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
            } else {
                code.add(new TypeInsnNode(Opcodes.NEW, "java/lang/UnsupportedOperationException"));
                code.add(new InsnNode(Opcodes.DUP));
                code.add(new LdcInsnNode("MFCL host owns the window and OpenGL context; unsupported legacy Display." + method.name + method.desc));
                code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false));
                code.add(new InsnNode(Opcodes.ATHROW));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String query(String signature) {
        switch (signature) {
            case "getWidth()I": return "width";
            case "getHeight()I": return "height";
            case "getX()I": return "x";
            case "getY()I": return "y";
            case "isCreated()Z": return "created";
            case "isActive()Z": return "active";
            case "isVisible()Z": return "visible";
            case "isFullscreen()Z": return "fullscreen";
            case "isCloseRequested()Z": return "closeRequested";
            case "isResizable()Z": return "resizable";
            default: return null;
        }
    }
}
