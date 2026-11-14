package github.com.gengyoubo.mfcl.api.mla;

import java.lang.annotation.*;

/** Marks an MLA contract of one original class. Give exactly one of {@link #value} and {@link #type}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LegacyType {
    /** The original binary class name, for example {@code "net.minecraft.entity.player.EntityPlayer"}. */
    String value() default "";
    /**
     * The original class itself ({@code @LegacyType(type = EntityPlayer.class)}), compiled against the member-less
     * class stubs that {@code gradle/mfcl-direct.gradle} adds to the host source set. Only the name is used: the host
     * never loads the class, and a host class of the same name (for example {@code net.minecraft.client.Minecraft})
     * names the original one too.
     */
    Class<?> type() default void.class;
}
