package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyMethod {
    String name();
    /**
     * Exact JVM descriptor in the legacy SRG runtime namespace. Empty means infer it from the Java
     * signature; inference is unavailable when a parameter or the return type is {@code LegacyObject}.
     */
    String descriptor() default "";
    boolean isStatic() default false;
}
