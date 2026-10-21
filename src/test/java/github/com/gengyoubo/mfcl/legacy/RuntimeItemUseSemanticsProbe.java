package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import org.objectweb.asm.*;
import java.util.*;

/** A real old item that requires inventory aliasing and returns a different metadata/NBT stack. */
final class RuntimeItemUseSemanticsProbe {
    static void install(java.nio.file.Path game) throws Exception {
        String name="mfcl/fixture/ReturnedStackFixture";
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/item/Item",null);
        var method=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);method.visitCode();method.visitVarInsn(Opcodes.ALOAD,0);method.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);method.visitInsn(Opcodes.RETURN);method.visitMaxs(0,0);method.visitEnd();
        method=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_77659_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",null,null);method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD,3);method.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/player/EntityPlayer","field_71071_by","Lnet/minecraft/entity/player/InventoryPlayer;");method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/InventoryPlayer","func_70448_g","()Lnet/minecraft/item/ItemStack;",false);method.visitVarInsn(Opcodes.ALOAD,1);
        var alias=new Label();method.visitJumpInsn(Opcodes.IF_ACMPEQ,alias);method.visitTypeInsn(Opcodes.NEW,"java/lang/IllegalStateException");method.visitInsn(Opcodes.DUP);method.visitLdcInsn("Callback stack differs from inventory");method.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/IllegalStateException","<init>","(Ljava/lang/String;)V",false);method.visitInsn(Opcodes.ATHROW);method.visitLabel(alias);method.visitFrame(Opcodes.F_SAME,0,null,0,null);
        method.visitVarInsn(Opcodes.ALOAD,3);method.visitTypeInsn(Opcodes.NEW,"net/minecraft/util/ChatComponentText");method.visitInsn(Opcodes.DUP);method.visitLdcInsn("used");method.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/util/ChatComponentText","<init>","(Ljava/lang/String;)V",false);method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/EntityPlayer","func_145747_a","(Lnet/minecraft/util/IChatComponent;)V",false);
        method.visitTypeInsn(Opcodes.NEW,"net/minecraft/item/ItemStack");method.visitInsn(Opcodes.DUP);method.visitVarInsn(Opcodes.ALOAD,0);
        method.visitVarInsn(Opcodes.ALOAD,1);method.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77994_a","I");method.visitInsn(Opcodes.ICONST_1);method.visitInsn(Opcodes.ISUB);
        method.visitVarInsn(Opcodes.ALOAD,1);method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77960_j","()I",false);method.visitInsn(Opcodes.ICONST_1);method.visitInsn(Opcodes.IADD);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemStack","<init>","(Lnet/minecraft/item/Item;II)V",false);method.visitLdcInsn("Revealed");method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_151001_c","(Ljava/lang/String;)Lnet/minecraft/item/ItemStack;",false);method.visitInsn(Opcodes.ARETURN);method.visitMaxs(0,0);method.visitEnd();writer.visitEnd();
        byte[] itemBytes=writer.toByteArray();writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,"mfcl/fixture/ReturnedStackMod",null,"java/lang/Object",null);
        var annotation=writer.visitAnnotation("Lcpw/mods/fml/common/Mod;",true);annotation.visit("modid","mfcl_return_fixture");annotation.visit("version","1.0");annotation.visitEnd();
        method=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);method.visitCode();method.visitVarInsn(Opcodes.ALOAD,0);method.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);method.visitInsn(Opcodes.RETURN);method.visitMaxs(0,0);method.visitEnd();
        method=writer.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true).visitEnd();method.visitCode();
        // GregTech queries FML's active language through this overload during preinitialization.
        method.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/LanguageRegistry","instance","()Lcpw/mods/fml/common/registry/LanguageRegistry;",false);
        method.visitLdcInsn("mfcl.test.preinit.language");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/registry/LanguageRegistry","getStringLocalization","(Ljava/lang/String;)Ljava/lang/String;",false);
        method.visitInsn(Opcodes.POP);
        method.visitTypeInsn(Opcodes.NEW,name);method.visitInsn(Opcodes.DUP);method.visitMethodInsn(Opcodes.INVOKESPECIAL,name,"<init>","()V",false);method.visitLdcInsn("returned_stack_fixture");method.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);method.visitInsn(Opcodes.RETURN);method.visitMaxs(0,0);method.visitEnd();writer.visitEnd();
        TestArchives.jar(game.resolve("mods/1.7.10/returned-stack-fixture.jar"),Map.of(),Map.of(name+".class",itemBytes,"mfcl/fixture/ReturnedStackMod.class",writer.toByteArray()));
    }
    static void verify(LegacyServices services,Object world,Map<String,Object> report) throws Exception {
        var bridge=new LegacyItemUse(services);var placer=new LegacyTiles.Placer(new UUID(91,92),"ReturnedStack",0,0,0,0,0);
        var input=new LegacyClientData.Stack("mfcl_return_fixture:returned_stack_fixture",0,2,"","");
        var first=bridge.rightClick(world,placer,input);Object actor=bridge.clientActor(world,placer,null);var returned=services.playerInventory().read(actor).get(0);
        if(!first.used()||!first.feedback().equals(List.of("used"))||returned==null||returned.metadata()!=1||returned.count()!=1||!returned.tag().contains("Revealed"))throw new AssertionError("Original returned stack/feedback was lost");
        bridge.rightClick(world,placer,returned);if(services.playerInventory().read(actor).get(0)!=null)throw new AssertionError("Consumed original item remained in the held slot");
        var loader=services.loader();var dimension=loader.loadClass("net.minecraft.entity.Entity").getField("field_71093_bK");
        Object provider=loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
        int expected=loader.loadClass("net.minecraft.world.WorldProvider").getField("field_76574_g").getInt(provider);
        dimension.setInt(actor,expected+13);
        if(bridge.clientActor(world,placer,null)!=actor||dimension.getInt(actor)!=expected)throw new AssertionError("Reused player retained its previous dimension id");
        report.put("genericPlayerDimensionRefreshVerified",true);
        report.put("genericReturnedStackAndFeedbackVerified",true);
    }
}
