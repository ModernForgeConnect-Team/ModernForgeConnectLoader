package github.com.gengyoubo.mfcl.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacySharedApisTest {
    @TempDir Path temporary;
    private LegacyArchive archive(String file,String mod,String owner,boolean implementation)throws Exception{
        var entries=new LinkedHashMap<String,byte[]>();entries.put(mod+"/Mod.class",TestArchives.classBytes(mod+".Mod",mod));
        entries.put("example/api/Shared.class",TestArchives.classBytes("example.api.Shared",null));
        if(implementation)entries.put("example/internal/Device.class",TestArchives.classBytes("example.internal.Device",null));
        if(owner!=null){var writer=new ClassWriter(0);writer.visit(Opcodes.V1_8,Opcodes.ACC_INTERFACE|Opcodes.ACC_ABSTRACT,"example/api/package-info",null,"java/lang/Object",null);var api=writer.visitAnnotation("Lcpw/mods/fml/common/API;",true);api.visit("owner",owner);api.visit("provides","Example|API");api.visit("apiVersion","1");api.visitEnd();writer.visitEnd();entries.put("example/api/package-info.class",writer.toByteArray());}
        return new LegacyDiscovery().inspect(TestArchives.jar(temporary.resolve(file),Map.of(),entries));
    }
    @Test void selectsDeclaredOwnerRegardlessOfConsumerOrderAndDoesNotExecuteClasses()throws Exception{
        System.clearProperty("mfcl.test.scanExecuted");var consumer=archive("a-consumer.jar","Consumer",null,false);var owner=archive("z-owner.jar","Owner","Owner",false);
        var archives=List.of(consumer,owner);assertEquals(owner,LegacySharedApis.classOwners(archives).get("example.api.Shared"));assertTrue(new LegacyDependencyValidator().validate(archives).isEmpty());assertNull(System.getProperty("mfcl.test.scanExecuted"));
    }
    @Test void apiAllowanceDoesNotHideImplementationConflicts()throws Exception{
        var consumer=archive("consumer.jar","Consumer",null,true);var owner=archive("owner.jar","Owner","Owner",true);
        var errors=new LegacyDependencyValidator().validate(List.of(consumer,owner));assertEquals(1,errors.stream().filter(d->d.code().equals("DUPLICATE_CLASS")).count());assertTrue(errors.get(0).message().contains("example.internal.Device"));
    }
    @Test void aConsumerCannotDeclareOwnershipForAnAbsentMod()throws Exception{
        var a=archive("a.jar","Consumer","Absent",false);var b=archive("b.jar","Another",null,false);
        assertTrue(LegacySharedApis.classOwners(List.of(a,b)).isEmpty());assertTrue(new LegacyDependencyValidator().validate(List.of(a,b)).stream().anyMatch(d->d.code().equals("DUPLICATE_CLASS")));
    }
}
