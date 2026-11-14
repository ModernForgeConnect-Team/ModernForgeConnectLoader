package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LegacyMethod {
    /**
     * The SRG name ({@code func_82169_q}), the MCP name ({@code getCurrentArmor}, with
     * {@link github.com.gengyoubo.mfcl.api.LegacyFeatures#MLA_MCP_NAMES}) or, for unobfuscated mod code, its own name.
     */
    String name();
    /**
     * Exact JVM descriptor in the legacy SRG runtime namespace. Empty means: the descriptor inferred from the Java
     * signature when an original method has it exactly, otherwise the one method of that name, static-ness and
     * parameter count whose types fit the contract
     * ({@link github.com.gengyoubo.mfcl.api.LegacyFeatures#MLA_SIGNATURE_SEARCH}). Give it to pick among overloads.
     */
    String descriptor() default "";
    boolean isStatic() default false;
}
