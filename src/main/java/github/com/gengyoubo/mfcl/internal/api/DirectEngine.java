package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import java.lang.reflect.*;
import java.util.*;

/**
 * Direct classes: addon code compiled against original classes, defined by a {@link DirectLegacyLoader} and reached
 * from host code only through a host interface. Every call through the returned proxy enters the runtime scope;
 * code inside the direct class then calls original code as plain Java.
 */
final class DirectEngine {
    private final LegacyApiRuntime runtime;
    /** One loader per addon module, for the runtime's lifetime: each JAR's direct classes see that addon's host types. */
    private final Map<Module, DirectLegacyLoader> loaders = new HashMap<>();

    DirectEngine(LegacyApiRuntime runtime) { this.runtime = runtime; }

    private synchronized DirectLegacyLoader loader(Class<?> contract) {
        return loaders.computeIfAbsent(contract.getModule(), module -> new DirectLegacyLoader(runtime.loader(), contract));
    }

    <T> T bind(Class<T> contract, String implementation) throws Exception {
        if (!contract.isInterface() || !Modifier.isPublic(contract.getModifiers()))
            throw new IllegalArgumentException("Expected a public host interface: " + contract.getName());
        if (contract.isAnnotationPresent(LegacyType.class))
            throw new IllegalArgumentException("Direct contracts are host interfaces, not @LegacyType accessors: " + contract.getName());
        if (contract.getClassLoader() == null) throw new IllegalArgumentException("Contract must belong to an addon: " + contract.getName());
        var loader = loader(contract);
        if (!loader.carries(implementation))
            throw new IllegalArgumentException("No direct class " + DirectLegacyLoader.entry(implementation) + " beside " + contract.getName());
        Class<?> type = Class.forName(implementation, false, loader);
        if (type.getClassLoader() != loader) throw new IllegalArgumentException("Not a direct class: " + implementation);
        if (!contract.isAssignableFrom(type))
            throw new IllegalArgumentException(implementation + " does not implement " + contract.getName()
                    + " (host interfaces may only use JDK, MFCL API and the addon's own types)");
        if (!Modifier.isPublic(type.getModifiers()) || Modifier.isAbstract(type.getModifiers()))
            throw new IllegalArgumentException("Direct class must be public and concrete: " + implementation);
        Object target = type.getConstructor().newInstance();
        return proxy(contract, target);
    }

    private <T> T proxy(Class<T> contract, Object target) {
        return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, new Handler(target)));
    }

    final class Handler implements InvocationHandler {
        private final Object target;
        Handler(Object target) { this.target = target; }
        Object target() { return target; }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "toString" -> "Direct[" + target.getClass().getName() + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new IllegalStateException(method.toString());
            };
            return runtime.call("DIRECT_INVOKE", target.getClass().getName() + "#" + method.getName(), () -> {
                Object[] values = args == null ? new Object[0] : args.clone();
                var guards = new ArrayList<LegacyApiRuntime.Guard>();
                for (int i = 0; i < values.length; i++) {
                    runtime.collectGuards(values[i], guards);
                    values[i] = argument(values[i]);
                }
                Object result = method.invoke(target, values);
                return result(result, method.getReturnType(), guards);
            });
        }
    }

    /** References and accessor proxies become their original object; direct proxies become their direct object. */
    private Object argument(Object value) {
        if (value != null && Proxy.isProxyClass(value.getClass()) && Proxy.getInvocationHandler(value) instanceof Handler direct)
            return direct.target();
        return runtime.unexposed(value);
    }

    private Object result(Object value, Class<?> apiType, List<LegacyApiRuntime.Guard> guards) {
        if (value == null || apiType == void.class) return null;
        if (runtime.owns(value.getClass())) {
            if (apiType == Object.class || apiType == LegacyObject.class
                    || apiType.isInterface() && apiType.isAnnotationPresent(LegacyType.class))
                return runtime.result(value, apiType, guards);
            throw new IllegalArgumentException("An original " + value.getClass().getName()
                    + " can only cross as Object, LegacyObject or a @LegacyType contract, not " + apiType.getName());
        }
        if (value.getClass().getClassLoader() instanceof DirectLegacyLoader && apiType.isInterface() && apiType.isInstance(value)
                && !Proxy.isProxyClass(value.getClass()))
            return proxy(apiType, value); // later calls on a returned direct object enter the scope too
        return value;
    }
}
