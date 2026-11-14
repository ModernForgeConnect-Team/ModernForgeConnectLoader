package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.mla.LegacyType;

/** Reads the original class name of an MLA contract without loading the original class on the host. */
public final class LegacyContracts {
    private LegacyContracts() { }

    /** The original binary class name, or null when {@code contract} carries no {@link LegacyType}. */
    public static String target(Class<?> contract) {
        LegacyType annotation = contract.getAnnotation(LegacyType.class);
        if (annotation == null) return null;
        String typed;
        try {
            Class<?> type = annotation.type();
            typed = type == void.class ? "" : type.getName();
        } catch (TypeNotPresentException absent) {
            // The usual case: the host cannot load the original class. The exception carries its binary name.
            typed = absent.typeName();
            if (typed == null || typed.startsWith("["))
                throw new IllegalArgumentException("Cannot read @LegacyType(type) of " + contract.getName() + "; use @LegacyType(\"<class name>\")", absent);
        }
        String named = annotation.value();
        if (named.isEmpty() == typed.isEmpty())
            throw new IllegalArgumentException("@LegacyType on " + contract.getName() + " needs exactly one of value or type");
        return named.isEmpty() ? typed : named;
    }
}
