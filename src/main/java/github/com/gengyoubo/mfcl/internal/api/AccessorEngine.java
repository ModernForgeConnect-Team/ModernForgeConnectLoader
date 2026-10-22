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
            for (int i = 0; i < binding.parameters.length; i++) compatibleParameter(api.getParameterTypes()[i], binding.parameters[i]);
            compatibleReturn(api.getReturnType(), binding.result);
            bindings.put(api, binding);
        }
        return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract},
                new Handler(owner, receiver, bindings)));
    }

    final class Handler implements InvocationHandler {
        private final Class<?> owner;
        private final LegacyObject receiver;
        private final Map<Method, Binding> bindings;
        Handler(Class<?> owner, LegacyObject receiver, Map<Method, Binding> bindings) {
            this.owner = owner; this.receiver = receiver; this.bindings = bindings;
        }
        LegacyObject reference() { return receiver; }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
        }
    }

    private Binding resolve(Class<?> owner, Object target, Method api) throws Exception {
        var method = api.getAnnotation(LegacyMethod.class);
        var field = api.getAnnotation(LegacyField.class);
        var constructor = api.getAnnotation(LegacyConstructor.class);
        if ((method == null ? 0 : 1) + (field == null ? 0 : 1) + (constructor == null ? 0 : 1) != 1)
            throw new IllegalArgumentException("Expected exactly one MLA member annotation: " + api);
        if (method != null) {
            String descriptor = method.descriptor().isEmpty() ? inferMethodDescriptor(api) : method.descriptor();
            var type = MethodType.fromMethodDescriptorString(descriptor, runtime.loader());
            Method member = findMethod(owner, method.name(), type);
            checkStatic(member.getModifiers(), method.isStatic(), target);
            if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
            return new Binding(type.parameterArray(), type.returnType(), args -> member.invoke(target, args));
        }
        if (constructor != null) {
            String descriptor = constructor.descriptor().isEmpty() ? inferConstructorDescriptor(api) : constructor.descriptor();
            var type = MethodType.fromMethodDescriptorString(descriptor, runtime.loader());
            if (type.returnType() != void.class) throw new IllegalArgumentException("Constructor descriptor must end in V");
            var member = owner.getDeclaredConstructor(type.parameterArray());
            if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
            return new Binding(type.parameterArray(), owner, member::newInstance);
        }
        String descriptor = field.descriptor().isEmpty() ? inferFieldDescriptor(api) : field.descriptor();
        Class<?> type = MethodType.fromMethodDescriptorString("()" + descriptor, runtime.loader()).returnType();
        Field member = findField(owner, field.name());
        if (member.getType() != type) throw new NoSuchFieldException("Descriptor differs: " + member);
        checkStatic(member.getModifiers(), field.isStatic(), target);
        if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
        if (api.getParameterCount() == 0) return new Binding(new Class<?>[0], type, args -> member.get(target));
        if (api.getParameterCount() != 1 || api.getReturnType() != void.class || Modifier.isFinal(member.getModifiers()))
            throw new IllegalArgumentException("Field setter must be (value)void and field must not be final: " + member);
        return new Binding(new Class<?>[]{type}, void.class, args -> { member.set(target, args[0]); return null; });
    }

    private static String inferMethodDescriptor(Method api) {
        var descriptor = new StringBuilder("(");
        for (Class<?> parameter : api.getParameterTypes()) descriptor.append(infer(parameter, api));
        return descriptor.append(')').append(infer(api.getReturnType(), api)).toString();
    }

    private static String inferConstructorDescriptor(Method api) {
        var descriptor = new StringBuilder("(");
        for (Class<?> parameter : api.getParameterTypes()) descriptor.append(infer(parameter, api));
        return descriptor.append(")V").toString();
    }

    private static String inferFieldDescriptor(Method api) {
        if (api.getParameterCount() == 0) return infer(api.getReturnType(), api);
        if (api.getParameterCount() == 1 && api.getReturnType() == void.class) return infer(api.getParameterTypes()[0], api);
        throw new IllegalArgumentException("Field contract must be a getter or setter: " + api);
    }

    private static String infer(Class<?> type, Method api) {
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == String.class || type == Object.class || boxed(type)) return "L" + type.getName().replace('.', '/') + ";";
        if (type.isArray()) return "[" + infer(type.getComponentType(), api);
        var contract = type.getAnnotation(LegacyType.class);
        if (type.isInterface() && contract != null) return "L" + contract.value().replace('.', '/') + ";";
        if (type == LegacyObject.class)
            throw new IllegalArgumentException("Descriptor is required for LegacyObject in " + api);
        throw new IllegalArgumentException("Descriptor is required for " + type.getTypeName() + " in " + api);
    }

    private static boolean boxed(Class<?> type) {
        return type == Boolean.class || type == Character.class || type == Byte.class || type == Short.class || type == Integer.class
                || type == Long.class || type == Float.class || type == Double.class;
    }
    private void compatibleParameter(Class<?> api, Class<?> old) {
        if (api.isArray() && old.isArray()) { compatibleParameter(api.getComponentType(), old.getComponentType()); return; }
        Class<?> target = contractTarget(api);
        if (target != null) {
            if (old.isPrimitive() || !old.isAssignableFrom(target))
                throw new IllegalArgumentException("Unsafe legacy contract parameter " + api.getTypeName() + "; descriptor type is " + old.getTypeName());
            return;
        }
        // A String or boxed parameter may feed a wider old parameter (Object, Comparable...): the value crosses as itself.
        if ((api == String.class || boxed(api)) && old.isAssignableFrom(api)) return;
        compatible(api, old);
    }

    private void compatibleReturn(Class<?> api, Class<?> old) {
        if (api.isArray() && old.isArray()) { compatibleReturn(api.getComponentType(), old.getComponentType()); return; }
        Class<?> target = contractTarget(api);
        if (target != null) {
            if (old.isPrimitive() || !target.isAssignableFrom(old))
                throw new IllegalArgumentException("Unsafe legacy contract return " + api.getTypeName() + "; descriptor type is " + old.getTypeName());
            return;
        }
        compatible(api, old);
    }

    private Class<?> contractTarget(Class<?> type) {
        if (!type.isInterface()) return null;
        var annotation = type.getAnnotation(LegacyType.class);
        if (annotation == null) return null;
        try {
            Class<?> target = runtime.loader().loadClass(annotation.value());
            if (!runtime.owns(target)) throw new IllegalArgumentException("Contract target must belong to the isolated runtime: " + annotation.value());
            return target;
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("Unknown legacy contract target: " + annotation.value(), failure);
        }
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
        if (api == old && (old.isPrimitive() || old == String.class || boxed(old))) return;
        // Object: Strings and boxed values cross as themselves, every other old value as a LegacyObject.
        if ((api == LegacyObject.class || api == Object.class) && !old.isPrimitive()) return;
        throw new IllegalArgumentException("Unsafe MLA type " + api.getTypeName() + "; descriptor type is " + old.getTypeName()
                + ". Use LegacyObject, Object or a @LegacyType contract for legacy references.");
    }
}
