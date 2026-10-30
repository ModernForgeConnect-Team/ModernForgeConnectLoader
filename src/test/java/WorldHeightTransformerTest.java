import github.com.gengyoubo.mfcl.legacy.runtime.IsolatedLegacyClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WorldHeightTransformerTest {
    @TempDir Path temporary;
    private IsolatedLegacyClassLoader loader()throws Exception {
        String support=System.getProperty("mfcl.test.legacySupportDir");assumeTrue(support!=null,"Requires the private runtime");
        Path runtime=Path.of(support);if(!Files.isRegularFile(runtime.resolve("minecraft-forge-1.7.10-srg.jar")))runtime=runtime.resolve("runtime");
        Path helper=temporary.resolve("helper.jar");try(var input=getClass().getResourceAsStream("/mfcl/mfcl-legacy-runtime.jar")){assertNotNull(input);Files.copy(input,helper);}
        var urls=new ArrayList<URL>();urls.add(helper.toUri().toURL());try(var jars=Files.list(runtime)){for(var jar:jars.filter(p->p.toString().endsWith(".jar")).toList())urls.add(jar.toUri().toURL());}
        return new IsolatedLegacyClassLoader(urls.toArray(URL[]::new),getClass().getClassLoader());
    }
    private byte[] transform(ClassLoader loader,String name,byte[] bytes)throws Exception {
        Class<?> type=loader.loadClass("github.com.gengyoubo.mfcl.legacy1710.WorldHeightTransformer");
        return (byte[])type.getMethod("transform",String.class,String.class,byte[].class).invoke(type.getConstructor().newInstance(),name,name,bytes);
    }
    @Test void sharedGeneratorBoundsDoNotRewriteHorizontalLimitsOrArraySizes()throws Exception {
        try(var loader=loader()){
            var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_6,Opcodes.ACC_PUBLIC,"fixture/Height",null,"java/lang/Object",null);
            var method=writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"generate","(Lnet/minecraft/world/World;Ljava/util/Random;III)Z",null,null);method.visitCode();
            Label invalid=new Label();
            method.visitVarInsn(Opcodes.ILOAD,3);method.visitInsn(Opcodes.ICONST_1);method.visitJumpInsn(Opcodes.IF_ICMPLT,invalid);
            method.visitVarInsn(Opcodes.ILOAD,3);method.visitIntInsn(Opcodes.BIPUSH,9);method.visitInsn(Opcodes.IADD);method.visitIntInsn(Opcodes.SIPUSH,256);method.visitJumpInsn(Opcodes.IF_ICMPGT,invalid);
            method.visitVarInsn(Opcodes.ILOAD,3);method.visitJumpInsn(Opcodes.IFLT,invalid);
            method.visitVarInsn(Opcodes.ILOAD,2);method.visitIntInsn(Opcodes.SIPUSH,256);method.visitJumpInsn(Opcodes.IF_ICMPGE,invalid);
            method.visitIntInsn(Opcodes.SIPUSH,256);method.visitIntInsn(Opcodes.NEWARRAY,Opcodes.T_INT);method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.ICONST_1);method.visitInsn(Opcodes.IRETURN);method.visitLabel(invalid);method.visitInsn(Opcodes.ICONST_0);method.visitInsn(Opcodes.IRETURN);method.visitMaxs(0,0);method.visitEnd();writer.visitEnd();
            byte[] output=transform(loader,"fixture.Height",writer.toByteArray());ClassNode parsed=new ClassNode();new ClassReader(output).accept(parsed,0);
            assertEquals(2,calls(parsed,"minBuildHeight"));assertEquals(1,calls(parsed,"maxBuildHeight"));
            long constants=Arrays.stream(parsed.methods.get(0).instructions.toArray()).filter(n->n instanceof IntInsnNode i&&i.operand==256).count();assertEquals(2,constants);
            class FixtureLoader extends ClassLoader {FixtureLoader(){super(loader);}Class<?> define(byte[] code){return defineClass(null,code,0,code.length);}}
            Class<?> fixture=new FixtureLoader().define(output);var generated=fixture.getMethod("generate",loader.loadClass("net.minecraft.world.World"),Random.class,int.class,int.class,int.class);
            assertEquals(true,generated.invoke(null,null,new Random(0),0,100,0));
            assertEquals(false,generated.invoke(null,null,new Random(0),0,-30,0)); // Unbound old world retains its own bounds.
            assertEquals(false,generated.invoke(null,null,new Random(0),256,100,0));
        }
    }
    @Test void sharedArrayLengthIsNotExpandedToHostHeight()throws Exception {
        try(var loader=loader()){
            var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_6,Opcodes.ACC_PUBLIC,"fixture/SharedHeight",null,"java/lang/Object",null);
            var m=writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"generate","(Lnet/minecraft/world/World;Ljava/util/Random;III)Z",null,null);m.visitCode();Label invalid=new Label();
            m.visitIntInsn(Opcodes.SIPUSH,256);m.visitVarInsn(Opcodes.ISTORE,5);
            m.visitVarInsn(Opcodes.ILOAD,3);m.visitVarInsn(Opcodes.ILOAD,5);m.visitJumpInsn(Opcodes.IF_ICMPGE,invalid);
            m.visitVarInsn(Opcodes.ILOAD,5);m.visitIntInsn(Opcodes.NEWARRAY,Opcodes.T_INT);m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.IRETURN);
            m.visitLabel(invalid);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();writer.visitEnd();
            byte[] original=writer.toByteArray();assertArrayEquals(original,transform(loader,"fixture.SharedHeight",original));
        }
    }
    @Test void originalVanillaAndThaumcraftGeneratorsUseTheSameBridge()throws Exception {
        try(var loader=loader()){
            for(String name:List.of("net.minecraft.world.gen.feature.WorldGenTrees","net.minecraft.world.gen.feature.WorldGenTaiga2")){
                byte[] before;try(var input=loader.getResourceAsStream(name.replace('.','/')+".class")){assertNotNull(input);before=input.readAllBytes();}
                var parsed=new ClassNode();new ClassReader(transform(loader,name,before)).accept(parsed,0);
                assertTrue(calls(parsed,"minBuildHeight")>0,name);assertTrue(calls(parsed,"maxBuildHeight")>0,name);
            }
            String tc=System.getProperty("mfcl.test.thaumcraftJar");assumeTrue(tc!=null,"Requires original TC4");
            try(var jar=new JarFile(tc)){
                String name="thaumcraft.common.lib.world.WorldGenSilverwoodTrees";byte[] before;
                try(var input=jar.getInputStream(jar.getJarEntry(name.replace('.','/')+".class"))){before=input.readAllBytes();}
                var parsed=new ClassNode();new ClassReader(transform(loader,name,before)).accept(parsed,0);
                assertTrue(calls(parsed,"minBuildHeight")>=2);assertTrue(calls(parsed,"maxBuildHeight")>=3);
                for(String item:List.of("thaumcraft.common.blocks.BlockArcaneDoor","thaumcraft.common.blocks.ItemJarNode")){
                    byte[] input;try(var stream=jar.getInputStream(jar.getJarEntry(item.replace('.','/')+".class"))){input=stream.readAllBytes();}
                    var block=new ClassNode();new ClassReader(transform(loader,item,input)).accept(block,0);
                    assertTrue(calls(block,"maxBuildHeight")>0,item+" retained a 255 ceiling");
                }
            }
        }
    }
    private long calls(ClassNode type,String name){return type.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).filter(i->i instanceof MethodInsnNode call&&call.name.equals(name)).count();}
}
