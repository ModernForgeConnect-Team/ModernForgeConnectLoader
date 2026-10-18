package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Replaces only the private LWJGL 2 platform classes. Unsupported native operations fail explicitly. */
public final class GuiPlatformAdapter implements LegacyBytecodeAdapter {
    private static final String API = "github/com/gengyoubo/mfcl/bridge/LegacyGuiGraphics";
    public boolean targets(String name) { return name.equals("org.lwjgl.opengl.GL11") || name.equals("org.lwjgl.input.Keyboard") || name.equals("org.lwjgl.input.Mouse"); }
    public byte[] adapt(String name, byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (MethodNode m : node.methods) {
            if (m.name.equals("<init>")) continue;
            m.access &= ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);
            m.instructions.clear(); m.tryCatchBlocks.clear(); if (m.localVariables != null) m.localVariables.clear();
            String method = m.name, desc = m.desc;
            if (method.equals("<clinit>")) { m.instructions.add(new InsnNode(Opcodes.RETURN)); continue; }
            if (name.endsWith("GL11")) {
                if (method.equals("glPushMatrix")) call(m,"push","()V");
                else if (method.equals("glPopMatrix")) call(m,"pop","()V");
                else if (method.equals("glTranslatef") || method.equals("glScalef")) {
                    for (int i=0;i<3;i++) { m.instructions.add(new VarInsnNode(Opcodes.FLOAD,i)); m.instructions.add(new InsnNode(Opcodes.F2D)); }
                    call(m,method.equals("glTranslatef") ? "translate" : "scale","(DDD)V");
                } else if (method.equals("glTranslated") || method.equals("glScaled")) {
                    for (int i=0;i<3;i++) m.instructions.add(new VarInsnNode(Opcodes.DLOAD,i*2));
                    call(m,method.equals("glTranslated") ? "translate" : "scale","(DDD)V");
                } else if (method.equals("glGetInteger") && desc.equals("(I)I")) { m.instructions.add(new VarInsnNode(Opcodes.ILOAD,0)); call(m,"integer","(I)I"); }
                else if (method.matches("gl(Enable|Disable|Color4f|Color3f|BlendFunc|DepthMask|DepthFunc|ColorMask|Normal3f|ShadeModel|AlphaFunc|PushAttrib|PopAttrib)")) { }
                else { unsupported(m,name+"."+method+desc); continue; }
            } else if (name.endsWith("Keyboard") && method.equals("isKeyDown")) { m.instructions.add(new VarInsnNode(Opcodes.ILOAD,0)); call(m,"key","(I)Z"); }
            else if (name.endsWith("Mouse") && method.equals("getX")) call(m,"mouseX","()I");
            else if (name.endsWith("Mouse") && method.equals("getY")) call(m,"mouseY","()I");
            else if (method.equals("isCreated")) m.instructions.add(new InsnNode(Opcodes.ICONST_1));
            else if (name.endsWith("Keyboard") && (method.equals("getKeyName") || method.equals("getKeyIndex"))) { keyNames(node,m);continue; }
            else if (method.equals("getDWheel") || method.equals("next") || method.equals("isButtonDown")) m.instructions.add(new InsnNode(Opcodes.ICONST_0));
            else if (method.equals("enableRepeatEvents") || method.equals("setGrabbed")) { }
            else { unsupported(m,name+"."+method+desc); continue; }
            m.instructions.add(new InsnNode(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN)));
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static void keyNames(ClassNode node,MethodNode method) {
        for(FieldNode field:node.fields)if(field.name.startsWith("KEY_") && field.value instanceof Integer) {
            LabelNode next=new LabelNode();
            if(method.name.equals("getKeyName")) {
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD,0));method.instructions.add(new LdcInsnNode(field.value));
                method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE,next));method.instructions.add(new LdcInsnNode(field.name.substring(4)));method.instructions.add(new InsnNode(Opcodes.ARETURN));
            } else {
                method.instructions.add(new LdcInsnNode(field.name.substring(4)));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
                method.instructions.add(new JumpInsnNode(Opcodes.IFEQ,next));method.instructions.add(new LdcInsnNode(field.value));method.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            method.instructions.add(next);
        }
        method.instructions.add(new InsnNode(method.name.equals("getKeyName")?Opcodes.ACONST_NULL:Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(method.name.equals("getKeyName")?Opcodes.ARETURN:Opcodes.IRETURN));
    }
    private static void call(MethodNode m,String name,String desc) { m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,API,name,desc,false)); }
    private static void unsupported(MethodNode m,String name) {
        m.instructions.add(new LdcInsnNode(name)); call(m,"unsupported","(Ljava/lang/String;)V");
        m.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); m.instructions.add(new InsnNode(Opcodes.ATHROW));
    }
}
