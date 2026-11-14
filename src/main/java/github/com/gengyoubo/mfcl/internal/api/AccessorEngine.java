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
        String targetName = LegacyContracts.target(contract);
        if (!contract.isInterface() || !Modifier.isPublic(contract.getModifiers()) || targetName == null)
            throw new IllegalArgumentException("Expected a public @LegacyType interface: " + contract.getName());
        Class<?> owner = runtime.loader().loadClass(targetName);
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
            var names = runtime.names().methodCandidates(method.name());
            Method member;
            if (!method.descriptor().isEmpty())
                member = findMethod(owner, names, MethodType.fromMethodDescriptorString(method.descriptor(), runtime.loader()));
            else {
                MethodType inferred = inferred(() -> inferMethodDescriptor(api));
                Method exact = null;
                if (inferred != null) try { exact = findMethod(owner, names, inferred); } catch (NoSuchMethodException absent) { /* search */ }
                member = exact != null ? exact : searchMethod(owner, names, method.isStatic(), api);
            }
            checkStatic(member.getModifiers(), method.isStatic(), target);
            if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
            return new Binding(member.getParameterTypes(), member.getReturnType(), args -> member.invoke(target, args));
        }
        if (constructor != null) {
            Constructor<?> member;
            if (!constructor.descriptor().isEmpty()) member = exactConstructor(owner, constructor.descriptor());
            else {
                MethodType inferred = inferred(() -> inferConstructorDescriptor(api));
                Constructor<?> exact = null;
                if (inferred != null) try { exact = owner.getDeclaredConstructor(inferred.parameterArray()); } catch (NoSuchMethodException absent) { /* search */ }
                member = exact != null ? exact : searchConstructor(owner, api);
            }
            if (!member.trySetAccessible()) throw new IllegalAccessException(member.toString());
            Constructor<?> bound = member;
            return new Binding(member.getParameterTypes(), owner, bound::newInstance);
        }
        Field member = findField(owner, runtime.names().fieldCandidates(field.name()));
        // Without a descriptor the field's own type is used; bind() still checks it against the contract.
        Class<?> type = field.descriptor().isEmpty() ? member.getType()
                : MethodType.fromMethodDescriptorString("()" + field.descriptor(), runtime.loader()).returnType();
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
        String contract = type.isInterface() ? LegacyContracts.target(type) : null;
        if (contract != null) return "L" + contract.replace('.', '/') + ";";
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
        String name = LegacyContracts.target(type);
        if (name == null) return null;
        try {
            Class<?> target = runtime.loader().loadClass(name);
            if (!runtime.owns(target)) throw new IllegalArgumentException("Contract target must belong to the isolated runtime: " + name);
            return target;
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("Unknown legacy contract target: " + name, failure);
        }
    }

    /** The inferred descriptor, or null when the contract uses LegacyObject/Object/unknown types that need a search. */
    private MethodType inferred(java.util.function.Supplier<String> descriptor) {
        try { return MethodType.fromMethodDescriptorString(descriptor.get(), runtime.loader()); }
        catch (IllegalArgumentException | TypeNotPresentException uninferable) { return null; }
    }

    /** {@code names}: the contract's name, then the SRG names it is the MCP name of. */
    private static Method findMethod(Class<?> owner, Set<String> names, MethodType type) throws NoSuchMethodException {
        Method found = exactMethod(owner, names, type);
        if (found == null) throw new NoSuchMethodException(owner.getName() + "#" + describe(names) + type.descriptorString());
        return found;
    }
    private static Method exactMethod(Class<?> owner, Set<String> names, MethodType type) {
        // The return descriptor matters too (covariant/bridge methods may share name and parameters).
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (var member : current.getDeclaredMethods()) if (names.contains(member.getName())
                    && member.getReturnType() == type.returnType() && Arrays.equals(member.getParameterTypes(), type.parameterArray())) return member;
            for (var face : current.getInterfaces()) {
                Method found = exactMethod(face, names, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Every non-synthetic method named in {@code names}, most derived first, one per name and parameter list. */
    private static Collection<Method> named(Class<?> owner, Set<String> names) {
        var result = new LinkedHashMap<String, Method>();
        var queue = new ArrayDeque<Class<?>>(List.of(owner));
        var seen = new HashSet<Class<?>>();
        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            if (!seen.add(current)) continue;
            for (var member : current.getDeclaredMethods()) {
                // Vanilla generic-parameter bridges are synthetic but lost ACC_BRIDGE; the real method is always present.
                if (member.isSynthetic() || !names.contains(member.getName())) continue;
                result.putIfAbsent(member.getName() + MethodType.methodType(void.class, member.getParameterTypes()).descriptorString(), member);
            }
            if (current.getSuperclass() != null) queue.add(current.getSuperclass());
            queue.addAll(List.of(current.getInterfaces()));
        }
        return result.values();
    }

    private Method searchMethod(Class<?> owner, Set<String> names, boolean isStatic, Method api) throws NoSuchMethodException {
        var all = named(owner, names);
        var fitting = new ArrayList<Method>();
        for (Method member : all) {
            if (Modifier.isStatic(member.getModifiers()) != isStatic || member.getParameterCount() != api.getParameterCount()) continue;
            if (fits(api.getParameterTypes(), member.getParameterTypes()) && fitsReturn(api.getReturnType(), member.getReturnType())) fitting.add(member);
        }
        if (fitting.size() == 1) return fitting.get(0);
        String target = owner.getName() + "#" + describe(names);
        if (fitting.isEmpty()) throw new NoSuchMethodException(target + " has no " + (isStatic ? "static" : "instance") + " method fitting " + api
                + (all.isEmpty() ? "" : "; candidates: " + descriptors(all)));
        throw new IllegalArgumentException(target + " is ambiguous for " + api + "; add descriptor = one of " + descriptors(fitting));
    }

    private Constructor<?> exactConstructor(Class<?> owner, String descriptor) throws NoSuchMethodException {
        var type = MethodType.fromMethodDescriptorString(descriptor, runtime.loader());
        if (type.returnType() != void.class) throw new IllegalArgumentException("Constructor descriptor must end in V");
        return owner.getDeclaredConstructor(type.parameterArray());
    }

    private Constructor<?> searchConstructor(Class<?> owner, Method api) throws NoSuchMethodException {
        var fitting = new ArrayList<Constructor<?>>();
        for (var member : owner.getDeclaredConstructors())
            if (!member.isSynthetic() && member.getParameterCount() == api.getParameterCount() && fits(api.getParameterTypes(), member.getParameterTypes()))
                fitting.add(member);
        if (fitting.size() == 1) return fitting.get(0);
        if (fitting.isEmpty()) throw new NoSuchMethodException(owner.getName() + " has no constructor fitting " + api);
        var descriptors = fitting.stream().map(c -> MethodType.methodType(void.class, c.getParameterTypes()).descriptorString()).sorted().toList();
        throw new IllegalArgumentException(owner.getName() + " constructors are ambiguous for " + api + "; add descriptor = one of " + descriptors);
    }

    private boolean fits(Class<?>[] api, Class<?>[] old) {
        try { for (int i = 0; i < api.length; i++) compatibleParameter(api[i], old[i]); return true; }
        catch (IllegalArgumentException unfit) { return false; }
    }
    private boolean fitsReturn(Class<?> api, Class<?> old) {
        if (api == void.class || old == void.class) return api == old;
        try { compatibleReturn(api, old); return true; }
        catch (IllegalArgumentException unfit) { return false; }
    }

    private static String describe(Set<String> names) {
        var all = new ArrayList<>(names);
        return all.size() == 1 ? all.get(0) : all.get(0) + " " + all.subList(1, all.size());
    }
    private static List<String> descriptors(Collection<Method> methods) {
        return methods.stream().map(m -> m.getName() + MethodType.methodType(m.getReturnType(), m.getParameterTypes()).descriptorString()).sorted().toList();
    }

    private static Field findField(Class<?> owner, Set<String> names) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (String name : names) {
                try { return current.getDeclaredField(name); } catch (NoSuchFieldException absent) { /* next name */ }
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + describe(names));
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
