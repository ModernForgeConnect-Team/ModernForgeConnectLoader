package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LegacyType { String value(); }
