package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/**
 * Call sites the legacy redirect transformer rewrites, shared by the host and the old class loader. Sites are fixed
 * before the old runtime boots. A rewritten call becomes {@code dispatch(id, callerClass, arguments)}, with the receiver of
 * an instance call as the first argument and primitives boxed; the result is cast or unboxed back at the call site.
 */
public final class LegacyRedirects {
    public interface Dispatcher { Object dispatch(int site, Class<?> caller, Object[] arguments) throws Throwable; }
    private static final List<String[]> SITES = new ArrayList<>();
    private static volatile boolean frozen;
    private static volatile Dispatcher dispatcher;
    private LegacyRedirects() {}
    /** Returns the site id. Same owner/name/descriptor/callers share one site. */
    public static synchronized int site(String callers, String owner, String name, String descriptor) {
        if (frozen) throw new IllegalStateException("Legacy redirects are fixed once the old runtime boots");
        for (int id = 0; id < SITES.size(); id++) {
            var site = SITES.get(id);
            if (site[0].equals(callers) && site[1].equals(owner) && site[2].equals(name) && site[3].equals(descriptor)) return id;
        }
        SITES.add(new String[]{callers, owner, name, descriptor});
        return SITES.size() - 1;
    }
    public static synchronized void freeze() { frozen = true; }
    /** For the transformer: {callers, owner (internal name), name, descriptor} per site id. */
    public static synchronized String[][] sites() { frozen = true; return SITES.toArray(new String[0][]); }
    public static void dispatcher(Dispatcher value) { dispatcher = Objects.requireNonNull(value); }
    public static Object dispatch(int site, Class<?> caller, Object[] arguments) throws Throwable {
        var current = dispatcher;
        if (current == null) throw new IllegalStateException("Legacy redirect dispatcher is not installed");
        return current.dispatch(site, caller, arguments);
    }
}
