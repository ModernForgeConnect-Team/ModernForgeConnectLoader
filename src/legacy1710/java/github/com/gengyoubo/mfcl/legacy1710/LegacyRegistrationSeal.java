package github.com.gengyoubo.mfcl.legacy1710;

/** Enforced at the actual GameData registration methods, after the old lifecycle and before host registry events. */
public final class LegacyRegistrationSeal {
    private static boolean sealed;
    public static synchronized void seal() { sealed = true; }
    public static synchronized void check() {
        if (sealed) throw new IllegalStateException("Forge 1.7.10 registration barrier is closed; late item/block registration cannot enter modern registries");
    }
}
