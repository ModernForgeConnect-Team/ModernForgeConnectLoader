package github.com.gengyoubo.mfcl.legacy1710.boot;

/** Applied before definition, including classes which a coremod excludes from LaunchWrapper's normal pipeline. */
public interface LegacyBytecodeAdapter {
    boolean targets(String className);
    byte[] adapt(String className, byte[] bytes);
}
