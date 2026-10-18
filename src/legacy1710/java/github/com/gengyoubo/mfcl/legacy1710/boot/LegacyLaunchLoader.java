package github.com.gengyoubo.mfcl.legacy1710.boot;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import java.lang.reflect.Field;
import java.net.URL;
import java.io.IOException;
import java.util.*;

/** LaunchWrapper semantics inside a private root. Never delegates to the modern game's loader. */
public final class LegacyLaunchLoader extends LaunchClassLoader {
    private final ClassLoader root;
    private final List<String> audit = new ArrayList<String>();
    private final Map<IClassTransformer, IClassTransformer> originals = new IdentityHashMap<IClassTransformer, IClassTransformer>();
    public LegacyLaunchLoader(URL[] urls, ClassLoader root) throws Exception {
        super(urls); this.root = root;
        Field exceptions = LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
        exceptions.setAccessible(true);
        @SuppressWarnings("unchecked") Set<String> set = (Set<String>) exceptions.get(this);
        set.clear();
        for (String prefix : Arrays.asList("java.", "jdk.", "sun.", "com.sun.",
                "net.minecraft.launchwrapper.", "cpw.mods.fml.relauncher.", "org.apache.logging.",
                "github.com.gengyoubo.mfcl.bridge.", "github.com.gengyoubo.mfcl.legacy1710.boot.")) set.add(prefix);
        addTransformerExclusion("github.com.gengyoubo.mfcl.legacy1710.");
        for (String prefix : Arrays.asList("cpw.mods.fml.repackage.", "cpw.mods.fml.relauncher.",
                "cpw.mods.fml.common.asm.transformers.", "cpw.mods.fml.common.patcher.",
                "net.minecraftforge.classloading.", "net.minecraftforge.transformers.")) addTransformerExclusion(prefix);
        registerTransformer("github.com.gengyoubo.mfcl.legacy1710.ModernJavaTransformer");
    }
    @Override public byte[] getClassBytes(String name) throws IOException {
        return SrgNamespaceAdapter.adapt(name, super.getClassBytes(name));
    }
    @Override public Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.equals("codechicken.lib.asm.ObfMapping") || name.equals("codechicken.core.asm.MCPDeobfuscationTransformer")
                || name.equals("codechicken.core.asm.DelegatedTransformer")) {
            try {
                byte[] bytes = SrgNamespaceAdapter.adapt(name, super.getClassBytes(name));
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) { throw new ClassNotFoundException(name, e); }
        }
        return super.findClass(name);
    }
    public Class<?> defineLegacyClass(String name, byte[] bytes, int offset, int length) {
        return defineClass(name, bytes, offset, length);
    }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (name.startsWith("java.") || name.startsWith("javax.") && findResource(name.replace('.', '/') + ".class") == null
                        || name.startsWith("jdk.") || name.startsWith("sun.") || name.startsWith("com.sun.")
                        || name.startsWith("net.minecraft.launchwrapper.") || name.startsWith("cpw.mods.fml.relauncher.")
                        || name.startsWith("org.apache.logging.") || name.startsWith("github.com.gengyoubo.mfcl.bridge.")
                        || name.startsWith("github.com.gengyoubo.mfcl.legacy1710.boot.")) c = root.loadClass(name);
                else c = findClass(name);
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }
    @Override public void registerTransformer(String name) {
        int before = getTransformers().size();
        super.registerTransformer(name);
        if (getTransformers().size() != before + 1) throw new IllegalStateException("Legacy transformer registration failed: " + name);
        // LaunchWrapper returns an unmodifiable view; retain its actual pipeline for audited wrappers.
        try {
            Field field = LaunchClassLoader.class.getDeclaredField("transformers"); field.setAccessible(true);
            @SuppressWarnings("unchecked") List<IClassTransformer> pipeline = (List<IClassTransformer>) field.get(this);
            final IClassTransformer original = pipeline.get(before);
            final String transformerName = name;
            IClassTransformer audited = new IClassTransformer() {
                @Override public byte[] transform(String originalName, String transformedName, byte[] bytes) {
                    byte[] result = original.transform(originalName, transformedName, bytes);
                    if (!Arrays.equals(bytes, result)) synchronized (audit) { audit.add(transformerName + " -> " + transformedName); }
                    return result;
                }
            };
            originals.put(audited, original); pipeline.set(before, audited);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @Override public List<IClassTransformer> getTransformers() {
        List<IClassTransformer> result = new ArrayList<IClassTransformer>();
        for (IClassTransformer transformer : super.getTransformers()) result.add(originals.containsKey(transformer) ? originals.get(transformer) : transformer);
        return Collections.unmodifiableList(result);
    }
    public List<String> transformationAudit() { synchronized (audit) { return new ArrayList<String>(audit); } }
}
