package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Shared tree-API steps for adapters. Each patch reports whether it found the original layout; callers fail explicitly. */
public final class BytecodePatches {
    private BytecodePatches() {}
    public static ClassNode read(byte[] bytes) { ClassNode node=new ClassNode(Opcodes.ASM5);new ClassReader(bytes).accept(node,0);return node; }
    public static byte[] write(ClassNode node) { ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray(); }
    /** The next real instruction, skipping labels, line numbers and frames. */
    public static AbstractInsnNode next(AbstractInsnNode node) {
        if(node==null)return null;do{node=node.getNext();}while(node!=null&&node.getOpcode()<0);return node;
    }
    public static boolean matches(MethodNode method,String name,String descriptor) { return method.name.equals(name)&&method.desc.equals(descriptor); }
    /**
     * Prefixes the method with {@code if(bridge(arguments)) return handled;}. The bridge returns boolean; a handled boolean
     * method returns true, a void method just returns. The original body stays the fallback.
     */
    public static void handledPrefix(MethodNode method,InsnList arguments,String owner,String bridge,String descriptor) {
        Type result=Type.getReturnType(method.desc);
        LabelNode original=new LabelNode();InsnList prefix=new InsnList();prefix.add(arguments);
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,owner,bridge,descriptor,false));prefix.add(new JumpInsnNode(Opcodes.IFEQ,original));
        if(result.equals(Type.BOOLEAN_TYPE)){prefix.add(new InsnNode(Opcodes.ICONST_1));prefix.add(new InsnNode(Opcodes.IRETURN));}
        else if(result.equals(Type.VOID_TYPE))prefix.add(new InsnNode(Opcodes.RETURN));
        else throw new IllegalArgumentException("Handled prefix needs a boolean or void method: "+method.name+method.desc);
        prefix.add(original);prefix.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(prefix);
    }
}
