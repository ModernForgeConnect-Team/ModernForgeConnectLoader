package github.com.gengyoubo.mfcl.legacy;

import org.objectweb.asm.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Original FML @API packages belong to their installed owner mod, rather than bundled consumer copies. */
public final class LegacySharedApis {
    private LegacySharedApis() {}
    public static Map<String,LegacyArchive> classOwners(List<LegacyArchive> archives) {
        var packages=new LinkedHashMap<String,LegacyArchive>();var ambiguous=new HashSet<String>();
        for(var archive:archives) {
            Path file=Path.of(archive.file());if(!Files.isRegularFile(file))continue;
            try(var jar=new JarFile(file.toFile(),false)) {
                for(String name:archive.classes())if(name.endsWith(".package-info")) {
                    var entry=jar.getJarEntry(name.replace('.','/')+".class");if(entry==null)continue;
                    String[] owner={null};
                    try(var input=jar.getInputStream(entry)) {
                        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                            @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){
                                if(!descriptor.equals("Lcpw/mods/fml/common/API;"))return null;
                                return new AnnotationVisitor(Opcodes.ASM9){@Override public void visit(String key,Object value){if(key.equals("owner")&&value instanceof String id)owner[0]=id;}};
                            }
                        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    }
                    if(owner[0]==null||archive.mods().stream().noneMatch(mod->mod.id().equals(owner[0])))continue;
                    String pkg=name.substring(0,name.lastIndexOf('.'));var previous=packages.putIfAbsent(pkg,archive);
                    if(previous!=null&&!previous.file().equals(archive.file()))ambiguous.add(pkg);
                }
            }catch(IOException|IllegalArgumentException unreadable){/* Discovery/runtime hash validation handles invalid or changed archives. */}
        }
        var result=new LinkedHashMap<String,LegacyArchive>();
        packages.forEach((pkg,archive)->{if(!ambiguous.contains(pkg))for(String name:archive.classes())if(name.lastIndexOf('.')>=0&&name.substring(0,name.lastIndexOf('.')).equals(pkg))result.put(name,archive);});
        return Collections.unmodifiableMap(result);
    }
}
