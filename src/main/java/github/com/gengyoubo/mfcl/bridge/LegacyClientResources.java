package github.com.gengyoubo.mfcl.bridge;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarFile;

/** Uses the actual old resource manager, metadata serializers and file packs. */
public final class LegacyClientResources {
    public record Context(Object manager, java.util.List<java.io.Closeable> packs) implements AutoCloseable {
        public Context { packs = java.util.List.copyOf(packs); }
        @Override public void close() throws java.io.IOException {
            java.io.IOException failure = null;
            for (var pack : packs) try { pack.close(); } catch (java.io.IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            if (failure != null) throw failure;
        }
    }
    private LegacyClientResources() { }
    public static Context create(ClassLoader loader) throws Exception { return create(loader, java.util.List.of()); }
    /** External packs (such as the asset-index languages absent from the 1.7.10 client JAR) load below the classpath packs. */
    public static Context create(ClassLoader loader, java.util.List<Path> externalPacks) throws Exception {
        Class<?> serializerType = loader.loadClass("net.minecraft.client.resources.data.IMetadataSerializer");
        Object serializer = serializerType.getConstructor().newInstance();
        var register = serializerType.getMethod("func_110504_a", loader.loadClass("net.minecraft.client.resources.data.IMetadataSectionSerializer"), Class.class);
        for (String section : new String[]{"Texture", "Font", "Animation", "Pack", "Language"}) {
            String prefix = "net.minecraft.client.resources.data." + section + "MetadataSection";
            register.invoke(serializer, loader.loadClass(prefix + "Serializer").getConstructor().newInstance(), loader.loadClass(prefix));
        }
        Class<?> managerType = loader.loadClass("net.minecraft.client.resources.SimpleReloadableResourceManager");
        Object manager = managerType.getConstructor(serializerType).newInstance(serializer);
        var packs = new ArrayList<Object>();
        var packConstructor = loader.loadClass("net.minecraft.client.resources.FileResourcePack").getConstructor(java.io.File.class);
        for (Path path : externalPacks) packs.add(packConstructor.newInstance(path.toFile()));
        if (loader instanceof URLClassLoader urls) for (URL url : urls.getURLs()) {
            if (!url.getProtocol().equals("file")) continue;
            Path path = Path.of(url.toURI());
            boolean assets = false;
            try (var jar = new JarFile(path.toFile())) {
                for (var entries = jar.entries(); entries.hasMoreElements();) {
                    if (entries.nextElement().getName().startsWith("assets/")) { assets = true; break; }
                }
            }
            if (assets) packs.add(packConstructor.newInstance(path.toFile()));
        }
        managerType.getMethod("func_110541_a", java.util.List.class).invoke(manager, packs);
        return new Context(manager, packs.stream().map(java.io.Closeable.class::cast).toList());
    }
}
