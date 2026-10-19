package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;

/** Resolves every member before returning a proxy. No ASM injection and no host/old class-name guessing. */
final class AccessorEngine {
    private interface Call { Object invoke(Object[] args) throws ReflectiveOperationException; }
    private record Binding(Class<?>[] parameters, Class<?> result, Call call) { }
    private final LegacyApiRuntime runtime;
    AccessorEngine(LegacyApiRuntime runtime) { this.runtime = runtime; }

    <T> T bind(Class<T> contract, LegacyObject receiver) throws Exception {
        var annotation = contract.getAnnotation(LegacyType.class);
        if (!contract.isInterface() || !Modifier.isPublic(contract.getModifiers()) || annotation == null)
            throw new IllegalArgumentException("Expected a public @LegacyType interface: " + contract.getName());
        Class<?> owner = runtime.loader().loadClass(annotation.value());
        if (!runtime.owns(owner)) throw new IllegalArgumentException("Target must belong to the isolated runtime: " + owner.getName());
        Object target = receiver == null ? null : runtime.raw(receiver);
        if (target != null && !owner.isInstance(target)) throw new IllegalArgumentException("Wrong receiver for " + owner.getName());
        Map<Method, Binding> bindings = new HashMap<>();
        for (Method api : contract.getMethods()) {
            if (Modifier.isStatic(api.getModifiers()) || api.isDefault() || api.getDeclaringClass() == Object.class) continue;
            Binding binding = resolve(owner, target, api);
            if (api.getParameterCount() != binding.parameters.length) throw new IllegalArgumentException("Parameter count: " + api);
            for (int i = 0; i < binding.parameters.length; i++) compatible(api.getParameterTypes()[i], binding.parameters[i]);
            compatible(api.getReturnType(), binding.result);
            bindings.put(api, binding);
        }
        return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "toString" -> "MLA[" + owner.getName() + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new IllegalStateException(method.toString());
            };
            return runtime.call("MLA_INVOKE", owner.getName() + "#" + method.getName(), () -> {
                if (receiver != null) runtime.raw(receiver);
                if (method.isDefault()) {
                    try { return InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args); }
                    catch (Error | RuntimeException failure) { throw failure; }
                    catch (Throwable failure) { throw new IllegalStateException("Accessor default method failed", failure); }
                }
                Binding binding = bindings.get(method);
                Object[] values = args == null ? new Object[0] : args;
                Object[] oldArgs = new Object[values.length];
                var guards = new ArrayList<LegacyApiRuntime.Guard>();
                runtime.collectGuards(receiver, guards);
                for (int i = 0; i < values.length; i++) {
                    runtime.collectGuards(values[i], guards);
                    oldArgs[i] = runtime.argument(values[i], binding.parameters[i]);
                }
                Object result = binding.call.invoke(oldArgs);
                return runtime.result(result, method.getReturnType(), guards);
            });
        }));
    }

    private Binding resolve(Class<?> owner, Object target, Method api) throws Exception {
        var method = api.getAnnotation(LegacyMethod.class);
        var field = api.getAnnotation(LegacyField.class);
        var constructor = api.getAnnotation(LegacyConstructor.class);
        if ((method == null ? 0 : 1) + (field == null ? 0 : 1) + (constructor == null ? 0 : 1) != 1)
            throw new IllegalArgumentException("Expected exactly one MLA member annotation: " + api);
        if (method != null) {
            var type = MethodType.fromMethodDescriptorString(method.descriptor(), runtime.loader());
            Method member = findMethod(owner, method.name(), type);
            checkStatic(member.getModifiers(), method.isStatic(), target);
            if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
            return new Binding(type.parameterArray(), type.returnType(), args -> member.invoke(target, args));
        }
        if (constructor != null) {
            var type = MethodType.fromMethodDescriptorString(constructor.descriptor(), runtime.loader());
            if (type.returnType() != void.class) throw new IllegalArgumentException("Constructor descriptor must end in V");
            var member = owner.getDeclaredConstructor(type.parameterArray());
            if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
            return new Binding(type.parameterArray(), owner, member::newInstance);
        }
        Class<?> type = MethodType.fromMethodDescriptorString("()" + field.descriptor(), runtime.loader()).returnType();
        Field member = findField(owner, field.name());
        if (member.getType() != type) throw new NoSuchFieldException("Descriptor differs: " + member);
        checkStatic(member.getModifiers(), field.isStatic(), target);
        if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
        if (api.getParameterCount() == 0) return new Binding(new Class<?>[0], type, args -> member.get(target));
        if (api.getParameterCount() != 1 || api.getReturnType() != void.class || Modifier.isFinal(member.getModifiers()))
            throw new IllegalArgumentException("Field setter must be (value)void and field must not be final: " + member);
        return new Binding(new Class<?>[]{type}, void.class, args -> { member.set(target, args[0]); return null; });
    }
    private static Method findMethod(Class<?> owner, String name, MethodType type) throws NoSuchMethodException {
        // The return descriptor matters too (covariant/bridge methods may share name and parameters).
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (var member : current.getDeclaredMethods()) if (member.getName().equals(name)
                    && member.getReturnType() == type.returnType() && Arrays.equals(member.getParameterTypes(), type.parameterArray())) return member;
            for (var face : current.getInterfaces()) {
                try { return findMethod(face, name, type); } catch (NoSuchMethodException absent) { /* next interface */ }
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name + type.descriptorString());
    }
    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException absent) { /* next superclass */ }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }
    private static void checkStatic(int modifiers, boolean expected, Object target) {
        if (Modifier.isStatic(modifiers) != expected) throw new IllegalArgumentException("Static/instance declaration differs");
        if (!expected && target == null) throw new IllegalArgumentException("Instance member requires a receiver");
    }
    private static void compatible(Class<?> api, Class<?> old) {
        if (api == old && (old.isPrimitive() || old == String.class)) return;
        if (api == LegacyObject.class && !old.isPrimitive()) return;
        if (api.isArray() && old.isArray()) { compatible(api.getComponentType(), old.getComponentType()); return; }
        throw new IllegalArgumentException("Unsafe MLA type " + api.getTypeName() + "; descriptor type is " + old.getTypeName()
                + ". Use LegacyObject for legacy references.");
    }
}
