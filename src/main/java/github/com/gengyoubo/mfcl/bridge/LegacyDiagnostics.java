package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, de-duplicated runtime findings for operators and developers: host/legacy objects without a mapping, legacy
 * event subscriptions that the host never delivers, and failures attributed to the original class that raised them.
 * Recording is cheap and never throws, so it is safe on gameplay paths.
 */
public final class LegacyDiagnostics {
    public enum Kind { MISSING_MAPPING, LEGACY_EXCEPTION }
    public record Finding(Kind kind, String key, String detail, String source, long count, long firstSeen) {}
    public record Subscription(String bus, String event, String listener, String source, boolean delivered) {}

    private static final int LIMIT = 512;
    private static final String[] HOST_PACKAGES = {"java.", "javax.", "jdk.", "sun.", "com.sun.", "github.com.gengyoubo.mfcl.",
            "net.minecraft.", "net.minecraftforge.", "cpw.mods.fml.", "com.google.", "com.mojang.", "org.apache.", "org.objectweb.", "io.netty.", "scala.", "kotlin."};
    private static final Map<String, Entry> FINDINGS = new ConcurrentHashMap<>();
    private static final Set<String> POSTED = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> POSTED_TYPES = ConcurrentHashMap.newKeySet();
    private static volatile ClassLoader legacyLoader;
    private static volatile boolean eventTracking;

    private static final class Entry {
        final Kind kind; final String key; final String detail; final String source; final long firstSeen = System.currentTimeMillis();
        long count;
        Entry(Kind kind, String key, String detail, String source) { this.kind = kind; this.key = key; this.detail = detail; this.source = source; }
    }

    private LegacyDiagnostics() {}

    public static void attach(ClassLoader loader) { legacyLoader = loader; }
    static ClassLoader legacyLoader() { return legacyLoader; }

    /** A host or legacy object that has no counterpart on the other side. The key should identify the object, not the call. */
    public static void missingMapping(String key, String detail) { record(Kind.MISSING_MAPPING, key, detail, null); }

    /** A failure during an original callback; the source is the first frame outside the host, Forge and MFCL. */
    public static void legacyFailure(String operation, Throwable failure) {
        if (failure == null) return;
        String source = source(failure);
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        record(Kind.LEGACY_EXCEPTION, operation + " @ " + source, root.toString(), source);
    }

    /** Called from the instrumented old EventBus.post; kept to a single set insertion. */
    public static void posted(Object event) {
        if (event == null || !POSTED_TYPES.add(event.getClass())) return;
        // Old buses also deliver subclasses, so a handler for a supertype has received this post too.
        for (Class<?> type = event.getClass(); type != null && type != Object.class; type = type.getSuperclass()) POSTED.add(type.getName());
    }

    public static List<Finding> findings(Kind kind) {
        var result = new ArrayList<Finding>();
        for (var entry : FINDINGS.values()) if (kind == null || entry.kind == kind) synchronized (entry) {
            result.add(new Finding(entry.kind, entry.key, entry.detail, entry.source, entry.count, entry.firstSeen));
        }
        result.sort(Comparator.comparingLong(Finding::count).reversed().thenComparing(Finding::key));
        return result;
    }

    /** Called by the legacy transformer once the old EventBus has been instrumented. */
    public static void eventTrackingInstalled() { eventTracking = true; }
    /** Without instrumentation, "undelivered" would be meaningless; callers should say so instead. */
    public static boolean eventTracking() { return eventTracking; }

    public static Set<String> postedEvents() { return Set.copyOf(POSTED); }

    /**
     * Lists every @SubscribeEvent handler registered on the old Forge/FML buses. An event type that was never posted
     * through an old bus in this session is reported as undelivered; the host does not forward it.
     */
    public static List<Subscription> subscriptions() {
        var loader = legacyLoader;
        if (loader == null) return List.of();
        var result = new ArrayList<Subscription>();
        try {
            Class<?> forge = Class.forName("net.minecraftforge.common.MinecraftForge", false, loader);
            for (String bus : List.of("EVENT_BUS", "TERRAIN_GEN_BUS", "ORE_GEN_BUS")) subscriptions(bus, forge.getField(bus).get(null), loader, result);
            Object handler = Class.forName("cpw.mods.fml.common.FMLCommonHandler", false, loader).getMethod("instance").invoke(null);
            subscriptions("FML", handler.getClass().getMethod("bus").invoke(handler), loader, result);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            result.add(new Subscription("?", "?", "old event buses unavailable: " + unavailable, "?", false));
        }
        result.sort(Comparator.comparing(Subscription::delivered).thenComparing(Subscription::event).thenComparing(Subscription::listener));
        return result;
    }

    public static void clear() { FINDINGS.clear(); POSTED.clear(); POSTED_TYPES.clear(); }

    static String source(Throwable failure) {
        // The root cause was thrown closest to the original code; outer wrappers only add host frames.
        var chain = new ArrayList<Throwable>();
        for (Throwable current = failure; current != null && !chain.contains(current); current = current.getCause()) chain.add(0, current);
        for (Throwable current : chain)
            for (var frame : current.getStackTrace()) if (!host(frame.getClassName()))
                return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber() + archive(frame.getClassName());
        return "host";
    }

    private static boolean host(String type) {
        for (String prefix : HOST_PACKAGES) if (type.startsWith(prefix)) return true;
        return false;
    }

    private static String archive(String type) {
        var loader = legacyLoader;
        if (loader == null) return "";
        try { return archive(Class.forName(type, false, loader)); } catch (ReflectiveOperationException | LinkageError absent) { return ""; }
    }

    private static String archive(Class<?> type) {
        try {
            var code = type.getProtectionDomain().getCodeSource();
            if (code == null || code.getLocation() == null) return "";
            String path = code.getLocation().getPath();
            int bang = path.indexOf('!');
            if (bang >= 0) path = path.substring(0, bang);
            return " [" + path.substring(path.lastIndexOf('/', path.length() - 2) + 1) + "]";
        } catch (SecurityException hidden) { return ""; }
    }

    private static void subscriptions(String bus, Object eventBus, ClassLoader loader, List<Subscription> result) throws ReflectiveOperationException {
        var field = eventBus.getClass().getDeclaredField("listeners");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var listeners = (Map<Object, ?>) field.get(eventBus);
        @SuppressWarnings("unchecked") var annotation = (Class<? extends java.lang.annotation.Annotation>)
                Class.forName("cpw.mods.fml.common.eventhandler.SubscribeEvent", false, loader);
        for (Object listener : List.copyOf(listeners.keySet())) {
            Class<?> owner = listener instanceof Class<?> type ? type : listener.getClass();
            for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass())
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(annotation) || method.getParameterCount() != 1) continue;
                    String event = method.getParameterTypes()[0].getName();
                    result.add(new Subscription(bus, event, owner.getName() + "." + method.getName(), archive(owner).trim(), POSTED.contains(event)));
                }
        }
    }

    private static void record(Kind kind, String key, String detail, String source) {
        try {
            var entry = FINDINGS.get(key);
            if (entry == null) {
                if (FINDINGS.size() >= LIMIT) return;
                entry = FINDINGS.computeIfAbsent(key, ignored -> new Entry(kind, key, detail == null ? "" : truncate(detail), source));
            }
            synchronized (entry) { entry.count++; }
        } catch (RuntimeException ignored) { }
    }

    private static String truncate(String value) { return value.length() <= 512 ? value : value.substring(0, 512); }
}
