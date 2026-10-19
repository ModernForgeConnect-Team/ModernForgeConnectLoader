package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyConstructor { String descriptor(); }
