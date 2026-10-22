package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.LiteralTranslationAdapter;
import org.objectweb.asm.*;
import java.util.*;

/** XU builds generator GUI/tooltip text from English literals; look each one up as a host (.json) translation key instead. */
public final class ExtraUtilitiesTextAdapter extends LiteralTranslationAdapter {
    private static final String GENERATORS = "com.rwtema.extrautils.tileentity.generators.";
    private static final String TITLE_PREFIX = "gui.extrautils.generator.title.";
    private static final String CONTAINER = GENERATORS + "DynamicContainerGenerator";
    private final Set<String> titled = Collections.synchronizedSet(new HashSet<String>());
    private void generator(String type, String method, String literal, String key) { key(GENERATORS + type, method, literal, key); }
    public ExtraUtilitiesTextAdapter() {
        generator("TileEntityGenerator", "getBlurb", "PowerLevel:\n", "gui.extrautils.generator.power_level");
        generator("TileEntityGenerator", "getBlurb", "\nTime Remaining:\n", "gui.extrautils.generator.time_remaining");
        generator("TileEntityGenerator", "getCoolDownString", "s", "gui.extrautils.generator.unit.seconds");
        // Minutes and larger units are followed by the next component: "1m 30s".
        generator("TileEntityGenerator", "getCoolDownString", "m ", "gui.extrautils.generator.unit.minutes");
        generator("TileEntityGenerator", "getCoolDownString", "h ", "gui.extrautils.generator.unit.hours");
        generator("TileEntityGenerator", "getCoolDownString", "d ", "gui.extrautils.generator.unit.days");
        generator("TileEntityGeneratorSimpleSolar", "getBlurb", "PowerLevel:\n", "gui.extrautils.generator.power_level");
        generator("TileEntityGeneratorSolar", "getBlurb", "PowerLevel:\n", "gui.extrautils.generator.power_level");
        generator("TileEntityGeneratorSolar", "getBlurb", "Transmitting: Deactivate redstone signal to resume charging", "gui.extrautils.generator.solar.transmitting");
        generator("TileEntityGeneratorSolar", "getBlurb", "Time Remaining until transmittion starts:\n", "gui.extrautils.generator.solar.transmit_delay");
        generator("TileEntityGeneratorSolar", "getBlurb", "\n\n\nCharging: Apply redstone signal to transmit energy", "gui.extrautils.generator.solar.charging");
        generator("TileEntityGeneratorSolar", "getBlurb", "\n\nCharging: Apply redstone signal to transmit energy", "gui.extrautils.generator.solar.charging");
        generator("BlockGenerator", "addInformation", "Power Multiplier: x", "tooltip.extrautils.generator.multiplier");
    }
    @Override protected boolean extraTarget(String name) { return name.equals(CONTAINER); }
    @Override protected MethodVisitor extend(final String name, final String method, MethodVisitor visitor) {
        if (!name.equals(CONTAINER) || !method.equals("<init>")) return visitor;
        return new MethodVisitor(Opcodes.ASM5, visitor) {
            private boolean generatorSuffix;
            @Override public void visitLdcInsn(Object value) { super.visitLdcInsn(value); generatorSuffix = " Generator".equals(value); }
            @Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean itf) {
                super.visitMethodInsn(opcode, owner, called, desc, itf);
                if (generatorSuffix && owner.equals("java/lang/StringBuilder") && called.equals("toString")) {
                    // names[meta] + " Generator" -> indexed(that, prefix, gen.getBlockMetadata())
                    super.visitLdcInsn(TITLE_PREFIX); super.visitVarInsn(Opcodes.ALOAD, 2);
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/rwtema/extrautils/tileentity/generators/TileEntityGenerator", "func_145832_p", "()I", false);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, TEXT, "indexed", "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;", false);
                    generatorSuffix = false; titled.add(name);
                }
            }
        };
    }
    @Override protected void verify(String name) {
        if (name.equals(CONTAINER) && !titled.contains(name)) throw new IllegalStateException("Cannot find original XU text in " + name + ": generator title");
    }
}
