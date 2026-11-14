package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

/** Zero parameters reads; one parameter and void return writes. Final fields cannot be written. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyField {
    /** SRG name ({@code field_77994_a}), MCP name ({@code stackSize}, with MLA_MCP_NAMES) or the unobfuscated name. */
    String name();
    /** Empty means the field's own type, checked against the getter return or setter parameter type. */
    String descriptor() default "";
    boolean isStatic() default false;
}
