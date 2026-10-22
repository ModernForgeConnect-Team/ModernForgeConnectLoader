package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import java.util.*;

/**
 * Old mods often build GUI/tooltip text from hard-coded English literals. Each declared literal is looked up as a host
 * (.json) translation key through LegacyText.translate, with the literal as fallback. Every declared literal must be
 * found, so a changed mod build fails explicitly instead of silently showing English.
 */
public abstract class LiteralTranslationAdapter implements LegacyBytecodeAdapter {
    protected static final String TEXT = "github/com/gengyoubo/mfcl/bridge/LegacyText";
    /** class -> "method literal" -> key; the literal's surrounding line breaks are kept by the bridge. */
    private final Map<String, Map<String, String>> keys = new HashMap<String, Map<String, String>>();
    protected final void key(String className, String method, String literal, String key) {
        Map<String, String> entries = keys.get(className);
        if (entries == null) keys.put(className, entries = new HashMap<String, String>());
        entries.put(method + " " + literal, key);
    }
    /** Classes patched only by {@link #extend}, without declared literals. */
    protected boolean extraTarget(String className) { return false; }
    /** Chains a mod-specific visitor behind the literal lookup, for computed text such as an indexed title. */
    protected MethodVisitor extend(String className, String method, MethodVisitor visitor) { return visitor; }
    /** Called after the class was visited; throw when a mod-specific rule did not find its layout. */
    protected void verify(String className) {}
    public boolean targets(String name) { return keys.containsKey(name) || extraTarget(name); }
    public byte[] adapt(final String name, byte[] bytes) {
        final Map<String, String> entries = keys.containsKey(name) ? keys.get(name) : Collections.<String, String>emptyMap();
        final Set<String> missing = new HashSet<String>(entries.keySet());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, final String method, String descriptor, String signature, String[] exceptions) {
                MethodVisitor out = extend(name, method, super.visitMethod(access, method, descriptor, signature, exceptions));
                return new MethodVisitor(Opcodes.ASM5, out) {
                    @Override public void visitLdcInsn(Object value) {
                        super.visitLdcInsn(value);
                        if (!(value instanceof String)) return;
                        String entry = method + " " + value, key = entries.get(entry);
                        if (key == null) return;
                        // Stack: original -> translate(key, original)
                        super.visitLdcInsn(key); super.visitInsn(Opcodes.SWAP);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, TEXT, "translate", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        missing.remove(entry);
                    }
                };
            }
        }, 0);
        if (!missing.isEmpty()) throw new IllegalStateException("Cannot find original text in " + name + ": " + missing);
        verify(name);
        return writer.toByteArray();
    }
}
