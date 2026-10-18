package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Generic old Minecraft drawing boundaries; mod layout and event code remains original. */
public final class GuiCaptureTransformer implements IClassTransformer {
    private static final String API = "github/com/gengyoubo/mfcl/bridge/LegacyGuiGraphics";
    public byte[] transform(String name,String transformedName,byte[] bytes) {
        if (bytes == null || !(name.equals("net.minecraft.client.gui.Gui") || name.equals("net.minecraft.client.gui.FontRenderer")
                || name.equals("net.minecraft.client.renderer.entity.RenderItem") || name.equals("net.minecraft.client.renderer.texture.TextureManager")
                || name.equals("net.minecraft.client.Minecraft")
                || name.equals("net.minecraft.client.gui.GuiButton")
                || name.equals("net.minecraft.client.audio.SoundHandler")
                || name.equals("net.minecraft.client.renderer.RenderHelper") || name.equals("net.minecraft.client.renderer.OpenGlHelper"))) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node,0);
        for (MethodNode m : node.methods) {
            InsnList code = new InsnList(); String target=null, descriptor=null;
            int ret=Opcodes.RETURN;
            if (name.endsWith(".Gui") && m.name.equals("func_73734_a")) { load(code,Opcodes.ILOAD,0,5); target="rect"; descriptor="(IIIII)V"; }
            else if (name.endsWith(".Minecraft") && m.name.equals("func_147108_a")) { code.add(new VarInsnNode(Opcodes.ALOAD,1)); target="showScreen";descriptor="(Ljava/lang/Object;)V"; }
            else if (name.endsWith(".GuiButton") && m.name.equals("func_146113_a")) { target="clickSound";descriptor="()V"; }
            else if (name.endsWith(".SoundHandler") && m.name.equals("func_147682_a")) { code.add(new VarInsnNode(Opcodes.ALOAD,1));target="sound";descriptor="(Ljava/lang/Object;)V"; }
            else if (name.endsWith(".Gui") && m.name.equals("func_73733_a")) { load(code,Opcodes.ILOAD,1,6); target="gradient"; descriptor="(IIIIII)V"; }
            else if (name.endsWith(".Gui") && m.name.equals("func_73729_b")) { load(code,Opcodes.ILOAD,1,6); target="texture"; descriptor="(IIIIII)V"; }
            else if (name.endsWith("FontRenderer") && m.name.equals("func_85187_a")) {
                code.add(new VarInsnNode(Opcodes.ALOAD,1)); load(code,Opcodes.ILOAD,2,4); target="text"; descriptor="(Ljava/lang/String;IIIZ)I"; ret=Opcodes.IRETURN;
            } else if (name.endsWith("TextureManager") && m.name.equals("func_110577_a")) { code.add(new VarInsnNode(Opcodes.ALOAD,1)); target="bind"; descriptor="(Ljava/lang/Object;)V"; }
            else if (name.endsWith("RenderItem") && (m.name.equals("func_82406_b") || m.name.equals("func_77015_a"))) {
                code.add(new VarInsnNode(Opcodes.ALOAD,3)); load(code,Opcodes.ILOAD,4,2); code.add(new InsnNode(Opcodes.ICONST_0)); code.add(new InsnNode(Opcodes.ACONST_NULL));
                target="item"; descriptor="(Ljava/lang/Object;IIZLjava/lang/String;)V";
            } else if (name.endsWith("RenderItem") && m.name.equals("func_94148_a")) {
                code.add(new VarInsnNode(Opcodes.ALOAD,3)); load(code,Opcodes.ILOAD,4,2); code.add(new InsnNode(Opcodes.ICONST_1)); code.add(new VarInsnNode(Opcodes.ALOAD,6));
                target="item"; descriptor="(Ljava/lang/Object;IIZLjava/lang/String;)V";
            } else if (name.endsWith("OpenGlHelper") && m.name.equals("func_77472_b") && m.desc.equals("(I)V")) {
                code.add(new VarInsnNode(Opcodes.ILOAD,0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorldGraphics","clientActiveTexture","(I)V",false));
            } else if ((name.endsWith("RenderHelper") || name.endsWith("OpenGlHelper")) && Type.getReturnType(m.desc).getSort()==Type.VOID && !m.name.startsWith("<")) { }
            else continue;
            if (target != null) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,API,target,descriptor,false));
            code.add(new InsnNode(ret)); m.instructions=code; m.tryCatchBlocks.clear(); if(m.localVariables!=null)m.localVariables.clear();
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static void load(InsnList code,int opcode,int first,int count) { for(int i=0;i<count;i++)code.add(new VarInsnNode(opcode,first+i)); }
}
