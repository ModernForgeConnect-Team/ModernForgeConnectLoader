package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyConstructor {
    /** Empty means the inferred descriptor when it exists exactly, otherwise the one constructor whose parameters fit. */
    String descriptor() default "";
}
