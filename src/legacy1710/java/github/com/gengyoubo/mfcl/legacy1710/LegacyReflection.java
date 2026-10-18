package github.com.gengyoubo.mfcl.legacy1710;

import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

/** Narrow compatibility for the pre-module Field.modifiers idiom; other access checks stay unchanged. */
public final class LegacyReflection {
    private LegacyReflection() { }
    public static Field declaredField(Class<?> type, String name) throws NoSuchFieldException {
        try { return type.getDeclaredField(name); }
        catch (NoSuchFieldException missing) {
            if (type != Field.class || !name.equals("modifiers")) throw missing;
            try {
                Field[] fields = (Field[]) ModernEnumSupport.trustedLookup().findVirtual(Class.class, "getDeclaredFields0",
                        MethodType.methodType(Field[].class, boolean.class)).invokeWithArguments(type, false);
                for (Field field : fields) if (field.getName().equals(name)) return field;
            } catch (Throwable failure) { missing.initCause(failure); }
            throw missing;
        }
    }
    public static void accessible(AccessibleObject object, boolean value) {
        if (object instanceof Field && ((Field) object).getDeclaringClass() == Field.class
                && ((Field) object).getName().equals("modifiers")) {
            try {
                ModernEnumSupport.trustedLookup().findVirtual(AccessibleObject.class, "setAccessible0",
                        MethodType.methodType(boolean.class, boolean.class)).invokeWithArguments(object, value);
                return;
            } catch (Throwable failure) { throw new IllegalStateException("Cannot expose legacy Field.modifiers", failure); }
        }
        object.setAccessible(value);
    }
}
