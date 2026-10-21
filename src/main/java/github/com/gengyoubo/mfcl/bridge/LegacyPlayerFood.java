package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.lang.reflect.Field;
import java.util.UUID;

/** Original FoodStats for callback players, with live host hunger and authoritative exhaustion. */
public final class LegacyPlayerFood {
    private static final String NAME="github/com/gengyoubo/mfcl/legacy1710/HostPlayerFood";
    private static final String FOOD="net/minecraft/util/FoodStats";
    private static final String BRIDGE=Type.getInternalName(LegacyPlayerFood.class);
    private LegacyPlayerFood() {}

    public static void initialize(Object player) throws ReflectiveOperationException {
        ClassLoader loader=player.getClass().getClassLoader();Class<?> type;
        synchronized(loader) {
            try {type=loader.loadClass(NAME.replace('/','.'));}
            catch(ClassNotFoundException absent) {type=define(loader);}
        }
        Field field=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getDeclaredField("field_71100_bB");
        field.setAccessible(true);field.set(player,type.getConstructor(Object.class).newInstance(player));
    }

    private static Class<?> define(ClassLoader loader) throws ReflectiveOperationException {
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,NAME,null,FOOD,null);
        writer.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"player","Ljava/lang/Object;",null,null).visitEnd();
        var method=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/Object;)V",null,null);method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD,0);method.visitMethodInsn(Opcodes.INVOKESPECIAL,FOOD,"<init>","()V",false);
        method.visitVarInsn(Opcodes.ALOAD,0);method.visitVarInsn(Opcodes.ALOAD,1);method.visitFieldInsn(Opcodes.PUTFIELD,NAME,"player","Ljava/lang/Object;");
        method.visitInsn(Opcodes.RETURN);method.visitMaxs(0,0);method.visitEnd();
        for(String[] entry:new String[][]{{"func_75116_a","()I"},{"func_75115_e","()F"},{"func_75121_c","()Z"},
                {"func_75122_a","(IF)V"},{"func_75114_a","(I)V"},{"func_75119_b","(F)V"}}) {
            String descriptor=entry[1];method=writer.visitMethod(Opcodes.ACC_PUBLIC,entry[0],descriptor,null,null);method.visitCode();
            sync(method,"pull");method.visitVarInsn(Opcodes.ALOAD,0);int slot=1;
            for(Type argument:Type.getArgumentTypes(descriptor)) {method.visitVarInsn(argument.getOpcode(Opcodes.ILOAD),slot);slot+=argument.getSize();}
            method.visitMethodInsn(Opcodes.INVOKESPECIAL,FOOD,entry[0],descriptor,false);
            if(Type.getReturnType(descriptor).equals(Type.VOID_TYPE))sync(method,"push");
            method.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN));method.visitMaxs(0,0);method.visitEnd();
        }
        method=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_75113_a","(F)V",null,null);method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD,0);method.visitFieldInsn(Opcodes.GETFIELD,NAME,"player","Ljava/lang/Object;");method.visitVarInsn(Opcodes.FLOAD,1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,BRIDGE,"exhaustion","(Ljava/lang/Object;F)Z",false);
        var original=new Label();method.visitJumpInsn(Opcodes.IFEQ,original);method.visitInsn(Opcodes.RETURN);
        method.visitLabel(original);method.visitFrame(Opcodes.F_SAME,0,null,0,null);
        method.visitVarInsn(Opcodes.ALOAD,0);method.visitVarInsn(Opcodes.FLOAD,1);method.visitMethodInsn(Opcodes.INVOKESPECIAL,FOOD,"func_75113_a","(F)V",false);
        method.visitInsn(Opcodes.RETURN);method.visitMaxs(0,0);method.visitEnd();writer.visitEnd();
        byte[] bytes=writer.toByteArray();
        return (Class<?>)loader.getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class)
                .invoke(loader,NAME.replace('/','.'),bytes,0,bytes.length);
    }

    private static void sync(MethodVisitor method,String operation) {
        method.visitVarInsn(Opcodes.ALOAD,0);method.visitVarInsn(Opcodes.ALOAD,0);method.visitFieldInsn(Opcodes.GETFIELD,NAME,"player","Ljava/lang/Object;");
        method.visitMethodInsn(Opcodes.INVOKESTATIC,BRIDGE,operation,"(Ljava/lang/Object;Ljava/lang/Object;)V",false);
    }
    private static HostWorldAccess host(Object player) throws ReflectiveOperationException {
        return LegacyWorlds.hostOrNull(player.getClass().getField("field_70170_p").get(player));
    }
    private static UUID id(Object player) throws ReflectiveOperationException {
        return (UUID)player.getClass().getMethod("func_110124_au").invoke(player);
    }
    private static Field field(Object food,String name) throws ReflectiveOperationException {
        Field field=food.getClass().getClassLoader().loadClass("net.minecraft.util.FoodStats").getDeclaredField(name);field.setAccessible(true);return field;
    }
    public static void pull(Object food,Object player) throws ReflectiveOperationException {
        var host=host(player);var state=host==null?null:host.food(id(player));if(state==null)return;
        field(food,"field_75127_a").setInt(food,state.level());field(food,"field_75125_b").setFloat(food,state.saturation());
    }
    public static void push(Object food,Object player) throws ReflectiveOperationException {
        var host=host(player);if(host==null||host.clientSide()||host.food(id(player))==null)return;
        host.setFood(id(player),new HostWorldAccess.Food(field(food,"field_75127_a").getInt(food),field(food,"field_75125_b").getFloat(food)));
    }
    public static boolean exhaustion(Object player,float amount) throws ReflectiveOperationException {
        var host=host(player);if(host==null||host.food(id(player))==null)return false;
        if(!host.clientSide())host.addExhaustion(id(player),amount);return true;
    }
}
