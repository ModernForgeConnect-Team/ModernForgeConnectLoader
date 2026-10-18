import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;

/** Small JVM/dependency probes, not a Minecraft or complete FML bootstrap test. */
public final class FmlJvmProbe {
    static class Provider {
        public int dimensionId;
        Provider(int dimensionId) { this.dimensionId = dimensionId; }
    }

    static class OldWorldShape {
        public final Provider provider = new Provider(0);
    }

    static final class ShadowWorld extends OldWorldShape {
        public final Provider provider = new Provider(42);
    }

    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 17) throw new AssertionError("Run with Java 17");
        ClassLoader systemBefore = ClassLoader.getSystemClassLoader();
        System.out.println("JAVA=" + System.getProperty("java.runtime.version"));
        System.out.println("SYSTEM_IS_URL_CLASSLOADER=" + (systemBefore instanceof URLClassLoader));

        Class<?> realFmlLoader = Class.forName("cpw.mods.fml.common.ModClassLoader");
        System.out.println("FML_SOURCE=" + realFmlLoader.getProtectionDomain().getCodeSource().getLocation());
        try {
            realFmlLoader.getConstructor(ClassLoader.class).newInstance(systemBefore);
            throw new AssertionError("Expected the original FML LaunchClassLoader cast to reject the system loader");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof ClassCastException)) throw expected;
            System.out.println("REAL_FML_SYSTEM_PARENT=REJECTED_ClassCastException");
        }

        ClassReader oldClass = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        System.out.println("ASM5_RELEASE8=ACCEPTED_" + oldClass.getClassName());
        try {
            new ClassReader(Files.readAllBytes(Path.of(args[1])));
            throw new AssertionError("Expected ASM 5.0.3 to reject class version 61");
        } catch (IllegalArgumentException expected) {
            System.out.println("ASM5_RELEASE17=REJECTED_IllegalArgumentException");
        }

        ShadowWorld facade = new ShadowWorld();
        OldWorldShape oldReference = facade;
        if (facade.provider.dimensionId != 42 || oldReference.provider.dimensionId != 0)
            throw new AssertionError("Unexpected field resolution");
        System.out.println("FIELD_SHADOWING=BASE_FIELD_0_SUBCLASS_FIELD_42");
        oldReference.provider.dimensionId = 7;
        if (oldReference.provider.dimensionId != 7 || facade.provider.dimensionId != 42)
            throw new AssertionError("Unexpected field aliasing");
        System.out.println("BASE_PROVIDER_MUTATION=VISIBLE_THROUGH_OLD_REFERENCE");
        if (systemBefore != ClassLoader.getSystemClassLoader()) throw new AssertionError("System loader changed");
        System.out.println("SYSTEM_CLASSLOADER=UNCHANGED");
        System.out.println("RESULT=PASS_5_NARROW_CHECKS_NOT_FULL_FML_BOOT");
    }
}
