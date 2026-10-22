package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider;
import github.com.gengyoubo.mfcl.bridge.LegacyRedirects.Dispatcher;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Host side of {@link LegacyRedirects}: collects providers before boot and runs their handlers for rewritten calls. */
public final class ApiRedirects implements Dispatcher {
    private record Site(String owner, String name, String descriptor, List<LegacyRedirects.Handler> handlers) {}
    private static final Map<Integer, Site> SITES = new ConcurrentHashMap<>();
    private static final Map<String, Method> TARGETS = new ConcurrentHashMap<>();
    private ApiRedirects() { }

    /** Called once from MFCL's constructor before the old runtime boots. */
    public static void collect() {
        github.com.gengyoubo.mfcl.bridge.LegacyRedirects.dispatcher(new ApiRedirects());
        LegacyRedirects registry = (callers, owner, name, descriptor, handler) -> {
            Objects.requireNonNull(callers); Objects.requireNonNull(owner); Objects.requireNonNull(name); Objects.requireNonNull(descriptor); Objects.requireNonNull(handler);
            if (callers.isEmpty() || callers.startsWith("github.com.gengyoubo.mfcl.") || callers.startsWith("java.") || callers.startsWith("net.minecraft.launchwrapper."))
                throw new IllegalArgumentException("Calls in " + callers + " cannot be redirected");
            if (name.startsWith("<")) throw new IllegalArgumentException("Constructors and initializers cannot be redirected");
            if (!descriptor.matches("\\((\\[*([ZBCSIJFD]|L[^;.]+;))*\\)(V|\\[*([ZBCSIJFD]|L[^;.]+;))"))
                throw new IllegalArgumentException("Expected a JVM method descriptor: " + descriptor);
            String internal = owner.replace('.', '/');
            int id = github.com.gengyoubo.mfcl.bridge.LegacyRedirects.site(callers, internal, name, descriptor);
            SITES.computeIfAbsent(id, ignored -> new Site(internal, name, descriptor, new java.util.concurrent.CopyOnWriteArrayList<>())).handlers().add(handler);
        };
        for (var provider : ServiceLoader.load(LegacyRedirectProvider.class, MfclApi.class.getClassLoader())) {
            try { provider.register(registry); }
            catch (RuntimeException | LinkageError failure) { com.mojang.logging.LogUtils.getLogger().error("MFCL legacy redirect provider {} failed; skipped", provider.getClass().getName(), failure); }
        }
        if (!SITES.isEmpty()) com.mojang.logging.LogUtils.getLogger().info("MFCL legacy redirects: {} call sites", SITES.size());
    }

    @Override public Object dispatch(int id, Class<?> caller, Object[] arguments) throws Throwable {
        var site = SITES.get(id);
        Method target = target(site, caller);
        boolean instance = !Modifier.isStatic(target.getModifiers());
        var runtime = ApiRuntimeProvider.active();
        if (runtime == null) return invoke(target, instance, arguments);
        return run(runtime, site, site.handlers().size() - 1, caller, target, instance, arguments);
    }
    private static Object run(LegacyApiRuntime runtime, Site site, int index, Class<?> caller, Method target, boolean instance, Object[] arguments) throws Throwable {
        if (index < 0) return invoke(target, instance, arguments);
        var handler = site.handlers().get(index);
        LegacyRedirects.Call call = new LegacyRedirects.Call() {
            @Override public String caller() { return caller.getName(); }
            @Override public LegacyObject receiver() { return instance ? runtime.reference(arguments[0]) : null; }
            @Override public Object[] arguments() {
                var result = new Object[arguments.length - (instance ? 1 : 0)];
                for (int i = 0; i < result.length; i++) result[i] = runtime.exposed(arguments[i + (instance ? 1 : 0)]);
                return result;
            }
            @Override public Object proceed(Object... values) throws Throwable {
                var next = arguments.clone();
                for (int i = 0; i < values.length && i + (instance ? 1 : 0) < next.length; i++) next[i + (instance ? 1 : 0)] = runtime.unexposed(values[i]);
                return run(runtime, site, index - 1, caller, target, instance, next);
            }
        };
        try { return runtime.unexposed(handler.handle(call)); }
        catch (VirtualMachineError fatal) { throw fatal; }
        catch (Throwable failure) { runtime.note("REDIRECT", site.owner() + "#" + site.name(), failure); throw failure; }
    }
    private static Object invoke(Method target, boolean instance, Object[] arguments) throws Throwable {
        try { return instance ? target.invoke(arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length)) : target.invoke(null, arguments); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private static Method target(Site site, Class<?> caller) throws ReflectiveOperationException {
        String key = System.identityHashCode(caller.getClassLoader()) + "|" + site.owner() + "#" + site.name() + site.descriptor();
        var known = TARGETS.get(key);
        if (known != null) return known;
        var loader = caller.getClassLoader();
        Class<?> owner = Class.forName(site.owner().replace('/', '.'), false, loader);
        var type = MethodType.fromMethodDescriptorString(site.descriptor(), loader);
        Method found = find(owner, site.name(), type);
        if (found == null) throw new NoSuchMethodException(site.owner() + "#" + site.name() + site.descriptor());
        found.setAccessible(true);
        TARGETS.put(key, found);
        return found;
    }
    private static Method find(Class<?> owner, String name, MethodType type) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (var method : current.getDeclaredMethods()) if (method.getName().equals(name) && method.getReturnType() == type.returnType()
                    && Arrays.equals(method.getParameterTypes(), type.parameterArray())) return method;
            for (var face : current.getInterfaces()) { var found = find(face, name, type); if (found != null) return found; }
        }
        return null;
    }
}
