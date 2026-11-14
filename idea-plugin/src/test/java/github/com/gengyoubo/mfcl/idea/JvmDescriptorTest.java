package github.com.gengyoubo.mfcl.idea;

public final class JvmDescriptorTest {
    public static void main(String[] args) {
        check(true, "", true); check(true, "()V", true); check(true, "(I[Lnet/minecraft/item/ItemStack;)I", true);
        check(true, "Ljava/lang/String;", false); check(true, "[[I", false);
        check(false, "I", true); check(false, "(V)V", true); check(false, "([V)V", true);
        check(false, "()", true); check(false, "()VV", true); check(false, "V", false);
        check(false, "Ljava.lang.String;", false); check(false, "Ljava//lang/String;", false);
        check(false, "[".repeat(256) + "I", false);
    }
    private static void check(boolean expected, String descriptor, boolean method) {
        if (JvmDescriptor.valid(descriptor, method) != expected)
            throw new AssertionError("Unexpected validation for " + descriptor);
    }
}
