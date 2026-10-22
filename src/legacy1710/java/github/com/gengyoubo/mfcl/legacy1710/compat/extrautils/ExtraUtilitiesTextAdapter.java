package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import java.util.*;

/** XU builds generator GUI/tooltip text from English literals; look each one up as a host (.json) translation key instead. */
public final class ExtraUtilitiesTextAdapter implements LegacyBytecodeAdapter {
    private static final String TEXT = "github/com/gengyoubo/mfcl/bridge/LegacyText";
    private static final String GENERATORS = "com.rwtema.extrautils.tileentity.generators.";
    private static final String TITLE_PREFIX = "gui.extrautils.generator.title.";
    /** class -> "method literal" -> key; the literal's surrounding line breaks are kept by the bridge. */
    private static final Map<String, Map<String, String>> KEYS = new HashMap<String, Map<String, String>>();
    private static void key(String type, String entry, String key) {
        Map<String, String> keys = KEYS.get(GENERATORS + type);
        if (keys == null) KEYS.put(GENERATORS + type, keys = new HashMap<String, String>());
        keys.put(entry, key);
    }
    static {
        key("TileEntityGenerator", "getBlurb PowerLevel:\n", "gui.extrautils.generator.power_level");
        key("TileEntityGenerator", "getBlurb \nTime Remaining:\n", "gui.extrautils.generator.time_remaining");
        key("TileEntityGenerator", "getCoolDownString s", "gui.extrautils.generator.unit.seconds");
        key("TileEntityGenerator", "getCoolDownString m", "gui.extrautils.generator.unit.minutes");
        key("TileEntityGenerator", "getCoolDownString h", "gui.extrautils.generator.unit.hours");
        key("TileEntityGenerator", "getCoolDownString d", "gui.extrautils.generator.unit.days");
        key("TileEntityGeneratorSimpleSolar", "getBlurb PowerLevel:\n", "gui.extrautils.generator.power_level");
        key("TileEntityGeneratorSolar", "getBlurb PowerLevel:\n", "gui.extrautils.generator.power_level");
        key("TileEntityGeneratorSolar", "getBlurb Transmitting: Deactivate redstone signal to resume charging", "gui.extrautils.generator.solar.transmitting");
        key("TileEntityGeneratorSolar", "getBlurb Time Remaining until transmittion starts:\n", "gui.extrautils.generator.solar.transmit_delay");
        key("TileEntityGeneratorSolar", "getBlurb \n\n\nCharging: Apply redstone signal to transmit energy", "gui.extrautils.generator.solar.charging");
        key("TileEntityGeneratorSolar", "getBlurb \n\nCharging: Apply redstone signal to transmit energy", "gui.extrautils.generator.solar.charging");
        key("BlockGenerator", "addInformation Power Multiplier: x", "tooltip.extrautils.generator.multiplier");
    }
    private static final String CONTAINER = GENERATORS + "DynamicContainerGenerator";
    public boolean targets(String name){return KEYS.containsKey(name)||name.equals(CONTAINER);}
    public byte[] adapt(final String name, byte[] bytes) {
        final Map<String, String> keys = KEYS.containsKey(name) ? KEYS.get(name) : Collections.<String, String>emptyMap();
        final Set<String> missing = new HashSet<String>(keys.keySet());
        final boolean[] title = {!name.equals(CONTAINER)};
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, final String method, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                    private boolean generatorSuffix;
                    @Override public void visitLdcInsn(Object value) {
                        super.visitLdcInsn(value);
                        if (!(value instanceof String)) return;
                        String entry = method + " " + value, key = keys.get(entry);
                        if (key != null) {
                            // Stack: original -> translate(key, original)
                            super.visitLdcInsn(key); super.visitInsn(Opcodes.SWAP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, TEXT, "translate", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                            missing.remove(entry);
                        }
                        generatorSuffix = name.equals(CONTAINER) && method.equals("<init>") && value.equals(" Generator");
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean itf) {
                        super.visitMethodInsn(opcode, owner, called, desc, itf);
                        if (generatorSuffix && owner.equals("java/lang/StringBuilder") && called.equals("toString")) {
                            // names[meta] + " Generator" -> indexed(that, prefix, gen.getBlockMetadata())
                            super.visitLdcInsn(TITLE_PREFIX); super.visitVarInsn(Opcodes.ALOAD, 2);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/rwtema/extrautils/tileentity/generators/TileEntityGenerator", "func_145832_p", "()I", false);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, TEXT, "indexed", "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;", false);
                            generatorSuffix = false; title[0] = true;
                        }
                    }
                };
            }
        }, 0);
        if (!missing.isEmpty() || !title[0]) throw new IllegalStateException("Cannot find original XU text in " + name + ": " + (missing.isEmpty() ? "generator title" : missing));
        return writer.toByteArray();
    }
}
