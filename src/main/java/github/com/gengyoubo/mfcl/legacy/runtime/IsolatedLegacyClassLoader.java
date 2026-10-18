package github.com.gengyoubo.mfcl.legacy.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

/** An isolated tool root. No fallback to the host's Minecraft, Forge or libraries. */
public final class IsolatedLegacyClassLoader extends URLClassLoader {
    static { registerAsParallelCapable(); }
    private final ClassLoader bridgeOwner;

    public IsolatedLegacyClassLoader(URL[] inputs, ClassLoader bridgeOwner) {
        super("mfcl-legacy-1710-tools", inputs, ClassLoader.getPlatformClassLoader());
        this.bridgeOwner = Objects.requireNonNull(bridgeOwner);
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                if (name.startsWith("github.com.gengyoubo.mfcl.bridge.")) type = bridgeOwner.loadClass(name);
                else if (name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.")) type = getParent().loadClass(name);
                else {
                    try { type = findClass(name); }
                    catch (ClassNotFoundException absent) { type = getParent().loadClass(name); }
                }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }
}
