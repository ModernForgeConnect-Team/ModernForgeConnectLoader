package github.com.gengyoubo.mfcl.internal.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Defines one addon's direct classes beside the isolated runtime. A direct class is stored as
 * {@code META-INF/mfcl/direct/<binary name>.class} in the addon JAR, so the host loader never defines it; it is read
 * through the addon's {@link Module} because {@code META-INF} is not a package and is never encapsulated.
 * Names resolve in this order: the direct class itself, the MFCL API (always the host's), the original runtime
 * (so {@code net.minecraft.entity.player.EntityPlayer} is the very class old mods use), then the addon's host loader.
 * Original names therefore win over host names that collide with them (Minecraft, MinecraftServer, Guava...).
 */
final class DirectLegacyLoader extends ClassLoader {
    static final String ROOT = "META-INF/mfcl/direct/";
    static { registerAsParallelCapable(); }
    private final ClassLoader legacy;
    private final Module addon;
    private final ClassLoader host;
    private final ProtectionDomain domain;

    DirectLegacyLoader(ClassLoader legacy, Class<?> anchor) {
        super("mfcl-direct", null);
        this.legacy = Objects.requireNonNull(legacy);
        this.addon = anchor.getModule();
        this.host = Objects.requireNonNull(anchor.getClassLoader(), "Addon class loader");
        CodeSource source = anchor.getProtectionDomain().getCodeSource();
        this.domain = new ProtectionDomain(source, null, this, null);
    }

    static String entry(String binaryName) { return ROOT + binaryName.replace('.', '/') + ".class"; }

    /** The direct class bytes in the addon JAR, or null when the addon does not carry {@code binaryName}. */
    private byte[] bytes(String binaryName) {
        try (InputStream input = addon.getResourceAsStream(entry(binaryName))) { return input == null ? null : input.readAllBytes(); }
        catch (IOException failure) { throw new UncheckedIOException("Cannot read direct class " + binaryName, failure); }
    }

    boolean carries(String binaryName) {
        try (InputStream input = addon.getResourceAsStream(entry(binaryName))) { return input != null; }
        catch (IOException failure) { return false; }
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                if (name.startsWith("java.")) type = ClassLoader.getPlatformClassLoader().loadClass(name);
                else if (name.startsWith("github.com.gengyoubo.mfcl.api.")) type = host.loadClass(name);
                else {
                    byte[] direct = bytes(name);
                    if (direct != null) type = define(name, direct);
                    else {
                        try { type = legacy.loadClass(name); }
                        catch (ClassNotFoundException | LinkageError absent) { type = host.loadClass(name); }
                    }
                }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    private Class<?> define(String name, byte[] bytes) {
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String packageName = name.substring(0, dot);
            if (getDefinedPackage(packageName) == null) {
                try { definePackage(packageName, null, null, null, null, null, null, null); }
                catch (IllegalArgumentException concurrent) { if (getDefinedPackage(packageName) == null) throw concurrent; }
            }
        }
        return defineClass(name, bytes, 0, bytes.length, domain);
    }

    /** Resources: original archives first (old assets and lang files), then the addon. */
    @Override protected URL findResource(String name) {
        URL found = legacy.getResource(name);
        return found != null ? found : host.getResource(name);
    }

    @Override protected Enumeration<URL> findResources(String name) throws IOException {
        var all = new ArrayList<URL>();
        all.addAll(Collections.list(legacy.getResources(name)));
        all.addAll(Collections.list(host.getResources(name)));
        return Collections.enumeration(all);
    }

    @Override public String toString() { return "DirectLegacyLoader[" + addon + "]"; }
}
