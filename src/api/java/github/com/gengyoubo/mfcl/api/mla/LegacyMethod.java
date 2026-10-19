package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyMethod {
    String name();
    /** Exact JVM descriptor in the legacy SRG runtime namespace. */
    String descriptor();
    boolean isStatic() default false;
}
