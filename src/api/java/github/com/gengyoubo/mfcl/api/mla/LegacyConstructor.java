package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyConstructor {
    /** Empty means infer the parameter descriptor from the Java signature. */
    String descriptor() default "";
}
