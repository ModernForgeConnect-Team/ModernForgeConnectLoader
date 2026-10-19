package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

/** Zero parameters reads; one parameter and void return writes. Final fields cannot be written. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyField {
    String name();
    /** Empty means infer the field type from the getter return type or setter parameter type. */
    String descriptor() default "";
    boolean isStatic() default false;
}
