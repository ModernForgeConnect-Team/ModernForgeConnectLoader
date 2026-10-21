package github.com.gengyoubo.mfcl.legacy1710;

import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyLaunchLoader;

/** Private-domain support for old Field.modifiers access and generated classes, without opening JDK modules. */
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
        // Generated old helpers define classes only in their own private LaunchClassLoader.
        if (object instanceof Method && legacyDefineClass((Method)object)) return;
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
    private static boolean legacyDefineClass(Method method) {
        return method.getDeclaringClass()==ClassLoader.class && method.getName().equals("defineClass")
                && java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{String.class,byte[].class,int.class,int.class});
    }
    public static Object invoke(Method method,Object receiver,Object[] args) throws IllegalAccessException,InvocationTargetException {
        if(legacyDefineClass(method)&&receiver instanceof LegacyLaunchLoader) {
            try {return ((LegacyLaunchLoader)receiver).defineLegacyClass((String)args[0],(byte[])args[1],(Integer)args[2],(Integer)args[3]);}
            catch(Throwable failure){throw new InvocationTargetException(failure);}
        }
        return method.invoke(receiver,args);
    }
}
