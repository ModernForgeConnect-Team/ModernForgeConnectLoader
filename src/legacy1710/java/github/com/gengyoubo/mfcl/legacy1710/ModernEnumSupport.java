package github.com.gengyoubo.mfcl.legacy1710;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;

/** Replacement for removed sun.reflect constructor accessors used by the 1614 EnumHelper. */
public final class ModernEnumSupport {
    private static final Object UNSAFE;
    private static final Class<?> UNSAFE_TYPE;
    static {
        try {
            UNSAFE_TYPE = Class.forName("sun.misc.Unsafe"); Field f = UNSAFE_TYPE.getDeclaredField("theUnsafe"); f.setAccessible(true); UNSAFE = f.get(null);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static Object unsafe(String method, Class<?>[] types, Object... args) throws Exception {
        return UNSAFE_TYPE.getMethod(method, types).invoke(UNSAFE, args);
    }
    private static long offset(Field field) throws Exception {
        return ((Long) unsafe(Modifier.isStatic(field.getModifiers()) ? "staticFieldOffset" : "objectFieldOffset", new Class<?>[]{Field.class}, field)).longValue();
    }
    public static void setFailsafeFieldValue(Field field, Object target, Object value) throws Exception {
        Object base = Modifier.isStatic(field.getModifiers()) ? unsafe("staticFieldBase", new Class<?>[]{Field.class}, field) : target;
        String suffix = field.getType().isPrimitive() ? field.getType().getSimpleName() : "Object";
        suffix = Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
        unsafe("put" + suffix + "Volatile", new Class<?>[]{Object.class, long.class, field.getType().isPrimitive() ? field.getType() : Object.class}, base, offset(field), value);
    }
    public static void cleanEnumCache(Class<?> type) throws Exception {
        for (String name : Arrays.asList("enumConstants", "enumConstantDirectory")) setFailsafeFieldValue(Class.class.getDeclaredField(name), type, null);
    }
    public static Enum<?> makeEnum(Class<?> type, String name, int ordinal, Class<?>[] extraTypes, Object[] extraValues) throws Exception {
        Class<?>[] types = new Class<?>[extraTypes.length + 2]; types[0] = String.class; types[1] = int.class;
        System.arraycopy(extraTypes, 0, types, 2, extraTypes.length);
        List<Object> values = new ArrayList<Object>(); values.add(name); values.add(ordinal); values.addAll(Arrays.asList(extraValues));
        Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        Object base = unsafe("staticFieldBase", new Class<?>[]{Field.class}, field);
        MethodHandles.Lookup trusted = (MethodHandles.Lookup) unsafe("getObject", new Class<?>[]{Object.class, long.class}, base, offset(field));
        try { return (Enum<?>) trusted.unreflectConstructor(type.getDeclaredConstructor(types)).invokeWithArguments(values); }
        catch (Throwable t) { throw new Exception("Cannot construct legacy enum " + type.getName() + "." + name, t); }
    }
}
