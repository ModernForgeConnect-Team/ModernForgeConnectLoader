package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

/** Zero parameters reads; one parameter and void return writes. Final fields cannot be written. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyField {
    String name();
    String descriptor();
    boolean isStatic() default false;
}
