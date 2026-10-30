import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.jar.*;
import java.nio.file.*;

/** Heuristic original-JAR inventory. Candidates require manual review; they are not proven height bugs.
 * Run with Java 17 and ASM 5 / LaunchWrapper / the built legacy runtime on the classpath.
 * Args: output JSON, then original input JARs. -Daudit.transform=true also inventories adapted methods.
 */
class HeightAudit {
    static String q(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    public static void main(String[] args)throws Exception {
        var findings=new ArrayList<String>();var adapted=new ArrayList<String>();int classes=0,changed=0;
        Object transformer=Boolean.getBoolean("audit.transform")?Class.forName("github.com.gengyoubo.mfcl.legacy1710.WorldHeightTransformer").getConstructor().newInstance():null;
        for(int a=1;a<args.length;a++)try(var jar=new JarFile(args[a])){
            for(var entry:Collections.list(jar.entries())){
                if(!entry.getName().endsWith(".class"))continue;ClassNode c=new ClassNode();
                byte[] original;try(var in=jar.getInputStream(entry)){original=in.readAllBytes();new ClassReader(original).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}classes++;
                if(transformer!=null){
                    String name=c.name.replace('/','.');byte[] output=(byte[])transformer.getClass().getMethod("transform",String.class,String.class,byte[].class).invoke(transformer,name,name,original);
                    if(!Arrays.equals(original,output)){
                        changed++;ClassNode after=new ClassNode();new ClassReader(output).accept(after,0);
                        for(MethodNode method:after.methods){long min=0,max=0;for(var i:method.instructions.toArray())if(i instanceof MethodInsnNode call&&call.owner.equals("github/com/gengyoubo/mfcl/bridge/LegacyWorlds")){if(call.name.equals("minBuildHeight"))min++;if(call.name.equals("maxBuildHeight"))max++;}
                            if(min+max>0)adapted.add("{\"class\":"+q(c.name)+",\"method\":"+q(method.name+method.desc)+",\"minChecks\":"+min+",\"maxChecks\":"+max+"}");
                        }
                    }
                }
                boolean packet=c.name.toLowerCase().contains("packet");
                boolean yField=c.fields.stream().anyMatch(f->f.name.matches("(?i)(y|ycoord|posy|blocky)")||f.name.equals("field_145848_d"));
                for(var f:c.fields)if(f.desc.equals("B")&&(f.name.matches("(?i)(y|ycoord|posy|blocky)")||packet&&yField))
                    findings.add("{\"jar\":"+q(Path.of(args[a]).getFileName().toString())+",\"class\":"+q(c.name)+",\"kind\":\"byte field\",\"field\":"+q(f.name)+"}");
                for(MethodNode m:c.methods){
                    boolean limit=false,coordinate=false,world=false;var serial=new TreeSet<String>();var constants=new TreeSet<Integer>();var positions=new TreeSet<String>();
                    for(var i:m.instructions.toArray()){
                        Integer n=i instanceof IntInsnNode v&&(v.getOpcode()==Opcodes.BIPUSH||v.getOpcode()==Opcodes.SIPUSH)?v.operand:i instanceof LdcInsnNode l&&l.cst instanceof Integer?(Integer)l.cst:null;
                        if(n!=null&&(n==255||n==256||n==127||n==128)){limit|=n==255||n==256;constants.add(n);}
                        if(i instanceof FieldInsnNode f&&(f.name.matches("(?i)(y|ycoord|posy|blocky)")||f.name.equals("field_145848_d")||f.name.equals("field_70163_u"))){coordinate=true;positions.add(f.name+":"+f.desc);}
                        if(i instanceof MethodInsnNode call){
                            if(call.owner.startsWith("net/minecraft/world/")||call.owner.equals("net/minecraft/world/IBlockAccess"))world=true;
                            if(call.name.matches("(readUnsignedByte|readByte|writeByte)"))serial.add(call.owner+"."+call.name+call.desc);
                        }
                    }
                    boolean generator=m.desc.startsWith("(Lnet/minecraft/world/World;Ljava/util/Random;III");
                    if(limit&&(coordinate||world||generator)||packet&&yField&&!serial.isEmpty()||coordinate&&m.instructions.size()>0&&Arrays.stream(m.instructions.toArray()).anyMatch(i->i instanceof JumpInsnNode j&&(j.getOpcode()==Opcodes.IFGT||j.getOpcode()==Opcodes.IFLE))){
                        String kind=generator?"generator bounds":packet&&!serial.isEmpty()?"packet byte IO":coordinate?"coordinate predicate/storage":"world code with 255/256";
                        findings.add("{\"jar\":"+q(Path.of(args[a]).getFileName().toString())+",\"class\":"+q(c.name)+",\"method\":"+q(m.name+m.desc)+",\"kind\":"+q(kind)+",\"constants\":"+q(constants.toString())+",\"yFields\":"+q(positions.toString())+",\"byteIO\":"+q(serial.toString())+"}");
                    }
                }
            }
        }
        Files.writeString(Path.of(args[0]),"{\"classesScanned\":"+classes+",\"transformedClasses\":"+changed+",\"adaptedMethods\":[\n"+String.join(",\n",adapted)+"\n],\"candidates\":[\n"+String.join(",\n",findings)+"\n]}");
        System.out.println("Scanned "+classes+" classes; "+findings.size()+" candidates. This is an audit inventory, not a list of proven bugs.");
    }
}
