package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.LiteralTranslationAdapter;
import org.objectweb.asm.*;
import java.util.*;

/** XU builds tooltips, chat feedback and GUI text from English literals; look each one up as a host (.json) translation key instead. */
public final class ExtraUtilitiesTextAdapter extends LiteralTranslationAdapter {
    private static final String XU = "com.rwtema.extrautils.";
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
        // Item tooltips, chat feedback and GUI text that XU writes as English literals.
        key(XU + "item.ItemDivisionSigil", "func_77624_a", "STABLE", "tooltip.extrautils.division_sigil.stable");
        key(XU + "item.ItemDivisionSigil", "func_77624_a", "ACTIVE: Number of uses remaining - ", "tooltip.extrautils.division_sigil.active");
        key(XU + "item.ItemDivisionSigil", "func_77624_a", "INACTIVE: You must perform Activation Ritual.", "tooltip.extrautils.division_sigil.inactive");
        key(XU + "item.ItemDivisionSigil", "func_77624_a", "Sneak right-click on an enchanting table", "tooltip.extrautils.division_sigil.inactive.hint");
        key(XU + "item.ItemDivisionSigil", "func_77624_a", "for more details", "tooltip.extrautils.division_sigil.inactive.details");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Activation Ritual", "chat.extrautils.ritual.activation");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Altar has a redstone circle", "chat.extrautils.ritual.circle.ok");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Altar and Circle placed on dirt", "chat.extrautils.ritual.dirt.ok");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Altar and Circle not placed on dirt", "chat.extrautils.ritual.dirt.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Altar does not have a redstone circle", "chat.extrautils.ritual.circle.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Altar can see the moon", "chat.extrautils.ritual.moon.ok");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Altar cannot see the moon", "chat.extrautils.ritual.moon.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Altar has sufficient natural earth", "chat.extrautils.ritual.earth.ok");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Area lacks sufficient natural earth", "chat.extrautils.ritual.earth.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Altar is in darkness", "chat.extrautils.ritual.dark.ok");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Altar must not be lit by outside sources", "chat.extrautils.ritual.dark.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Too early, sacrifice must be made at midnight", "chat.extrautils.ritual.too_early");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Too late, sacrifice must be made at midnight", "chat.extrautils.ritual.too_late");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Time is right", "chat.extrautils.ritual.time.ok");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Perform the sacrifice", "chat.extrautils.ritual.perform");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Stabilization Ritual", "chat.extrautils.ritual.stabilization");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Too hot", "chat.extrautils.ritual.too_hot");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- Too much natural earth", "chat.extrautils.ritual.too_much_earth");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- To the north, Children of Fire: ", "chat.extrautils.ritual.north");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Northern chest not present", "chat.extrautils.ritual.north.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- To the south, Gifts of Earth: ", "chat.extrautils.ritual.south");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Southern chest not present", "chat.extrautils.ritual.south.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- To the east, Descendants of Water: ", "chat.extrautils.ritual.east");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Eastern chest not present", "chat.extrautils.ritual.east.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "- To the west, Spices of Air: ", "chat.extrautils.ritual.west");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "! Western chest not present", "chat.extrautils.ritual.west.missing");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Ritual Markings: No markings present", "chat.extrautils.ritual.markings.none");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Ritual Markings: Only 1 type of marking present", "chat.extrautils.ritual.markings.one");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Ritual Markings: Strength - ", "chat.extrautils.ritual.markings.strength");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Everything is prepared.", "chat.extrautils.ritual.prepared");
        key(XU + "item.ItemDivisionSigil", "func_77648_a", "Sacrifice one who would sacrifice himself.", "chat.extrautils.ritual.sacrifice");
        key(XU + "EventHandlerServer", "ActivationRitual", "Activation Ritual Failure: Altar cannot see the moon", "chat.extrautils.ritual.failure.moon");
        key(XU + "EventHandlerServer", "ActivationRitual", "Activation Ritual Failure: Altar and circle must be on natural earth", "chat.extrautils.ritual.failure.dirt");
        key(XU + "EventHandlerServer", "ActivationRitual", "Activation Ritual Failure: Altar is too brightly lit", "chat.extrautils.ritual.failure.light");
        key(XU + "EventHandlerServer", "ActivationRitual", "Activation Ritual Failure: Altar requires more natural earth", "chat.extrautils.ritual.failure.earth");
        key(XU + "EventHandlerServer", "ActivationRitual", "Activation Ritual Failure: Too early", "chat.extrautils.ritual.failure.too_early");
        key(XU + "EventHandlerServer", "ActivationRitual", "Activation Ritual Failure: Too late", "chat.extrautils.ritual.failure.too_late");
        key(XU + "EventHandlerSiege", "endSiege", "The Siege has ended in 'The End'", "chat.extrautils.siege.ended");
        key(XU + "EventHandlerSiege", "beginSiege", "The Siege has begun in 'The End'", "chat.extrautils.siege.begun");
        key(XU + "EventHandlerSiege", "SiegeDeath", "Your Sigil has stabilized", "chat.extrautils.siege.stabilized");
        key(XU + "EventHandlerSiege", "SiegeDeath", "Kills: ", "chat.extrautils.siege.kills");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "Stable", "tooltip.extrautils.unstable_ingot.stable");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "Explodes in ", "tooltip.extrautils.unstable_ingot.explodes_in");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", " seconds", "tooltip.extrautils.unstable_ingot.seconds");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "This ingot was created incorrectly", "tooltip.extrautils.unstable_ingot.bug.1");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "using getRecipeOutput() instead of getCraftingResult()", "tooltip.extrautils.unstable_ingot.bug.2");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "if this ingot was made legitimately please", "tooltip.extrautils.unstable_ingot.bug.3");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "report this to the mod developer.", "tooltip.extrautils.unstable_ingot.bug.4");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "(don't spam them though - check to see if", "tooltip.extrautils.unstable_ingot.bug.5");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "it hasn't already been reported)", "tooltip.extrautils.unstable_ingot.bug.6");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "ERROR: Divide by diamond", "tooltip.extrautils.unstable_ingot.divide_by_diamond");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "This ingot is highly unstable and will explode", "tooltip.extrautils.unstable_ingot.info.1");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "after 10 seconds.", "tooltip.extrautils.unstable_ingot.info.2");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "Will also explode if the crafting window is closed", "tooltip.extrautils.unstable_ingot.info.3");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "or the ingot is thrown on the ground.", "tooltip.extrautils.unstable_ingot.info.4");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "Additionally these ingots do not stack", "tooltip.extrautils.unstable_ingot.info.5");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", " - Do not craft unless ready -", "tooltip.extrautils.unstable_ingot.info.6");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "Must be crafted in a vanilla crafting table.", "tooltip.extrautils.unstable_ingot.info.7");
        key(XU + "item.ItemUnstableIngot", "func_77624_a", "Creative Spawned - Stable", "tooltip.extrautils.unstable_ingot.creative");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Inverted", "tooltip.extrautils.node_upgrade.inverted");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Fuzzy - Ignores Metadata", "tooltip.extrautils.node_upgrade.fuzzy_metadata");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Fuzzy - Ignores NBT", "tooltip.extrautils.node_upgrade.fuzzy_nbt");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Right click to select items to filter", "tooltip.extrautils.node_upgrade.filter.select");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Filters can be placed within other filters to create advanced behaviours", "tooltip.extrautils.node_upgrade.filter.nested");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Craft with", "tooltip.extrautils.node_upgrade.filter.craft_with");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Filter Program: ", "tooltip.extrautils.node_upgrade.advanced.program");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Right-click to change Filter Program", "tooltip.extrautils.node_upgrade.advanced.change");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Craft with a redstone torch to Invert", "tooltip.extrautils.node_upgrade.advanced.invert");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Can be placed in normal filters to create advanced behaviours", "tooltip.extrautils.node_upgrade.advanced.nested");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Unspecified Frequency: You must name this upgrade in an anvil to choose a frequency", "tooltip.extrautils.node_upgrade.ender.unnamed");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "You cannot use this upgrade until it has a frequency", "tooltip.extrautils.node_upgrade.ender.unusable");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Frequency: ", "tooltip.extrautils.node_upgrade.ender.frequency");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Public Spectrum", "tooltip.extrautils.node_upgrade.ender.public");
        key(XU + "item.ItemNodeUpgrade", "func_77624_a", "Private Spectrum - ", "tooltip.extrautils.node_upgrade.ender.private");
        key(XU + "item.ItemNodeUpgrade", "func_77659_a", "Spectrum set to private", "chat.extrautils.node_upgrade.private");
        key(XU + "item.ItemNodeUpgrade", "func_77659_a", "Spectrum set to public", "chat.extrautils.node_upgrade.public");
        key(XU + "item.ItemNodeUpgrade", "func_77659_a", "Filter Program: ", "tooltip.extrautils.node_upgrade.advanced.program");
        key(XU + "item.ItemSoul", "func_77659_a", "You feel strangely refreshed (+10% Max Health)", "chat.extrautils.soul.refreshed");
        key(XU + "item.ItemSoul", "func_77622_d", "You feel diminished (-10% Max Health)", "chat.extrautils.soul.diminished");
        key(XU + "item.ItemSoul", "func_77624_a", "Soul of a forgotten deity", "tooltip.extrautils.soul.deity");
        key(XU + "item.ItemSoul", "func_77624_a", "Soul is too weak and has been spread too thin", "tooltip.extrautils.soul.weak");
        key(XU + "item.ItemSoul", "func_77624_a", "Owner: ", "tooltip.extrautils.soul.owner");
        key(XU + "item.ItemGoldenBag", "func_77624_a", "Reincarnating I", "tooltip.extrautils.golden_bag.reincarnating");
        key(XU + "item.ItemGoldenLasso", "func_77624_a", "*this ", "tooltip.extrautils.golden_lasso.renamed.1");
        key(XU + "item.ItemGoldenLasso", "func_77624_a", " has chosen a new name*", "tooltip.extrautils.golden_lasso.renamed.2");
        key(XU + "item.ItemWateringCan", "func_77624_a", "It appears that mechanical hands are not delicate enough", "tooltip.extrautils.watering_can.busted.1");
        key(XU + "item.ItemWateringCan", "func_77624_a", "to use the watering can properly.", "tooltip.extrautils.watering_can.busted.2");
        key(XU + "item.ItemBlockDrum", "func_77624_a", "Empty: 0 / ", "tooltip.extrautils.drum.empty");
        key(XU + "helper.XUHelper", "getFluidName", "Unnamed liquid", "tooltip.extrautils.fluid.unnamed");
        key(XU + "block.BlockFilingCabinet", "addInformation", "contains ", "tooltip.extrautils.filing_cabinet.contains");
        key(XU + "block.BlockFilingCabinet", "addInformation", " item", "tooltip.extrautils.filing_cabinet.items");
        key(XU + "block.BlockFilingCabinet", "addInformation", " of ", "tooltip.extrautils.filing_cabinet.of");
        key(XU + "block.BlockFilingCabinet", "addInformation", " type", "tooltip.extrautils.filing_cabinet.types");
        key(XU + "helper.XUHelper", "s", "s", "tooltip.extrautils.plural");
        key(XU + "block.BlockColor", "func_149727_a", "Unable to change color at this location", "chat.extrautils.color.unavailable");
        key(XU + "block.BlockSoundMuffler", "func_149727_a", "You remove the magic wool from your ears", "chat.extrautils.rain_muffler.off");
        key(XU + "block.BlockSoundMuffler", "func_149727_a", "You place some magic wool in your ears", "chat.extrautils.rain_muffler.on");
        key(XU + "tileentity.enderquarry.BlockEnderQuarry", "func_149727_a", "Overclock Mode Activated", "chat.extrautils.ender_quarry.overclock");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "func_145845_h", "Problem registering chunk-preserving method", "chat.extrautils.ender_quarry.chunk_ticket_failed");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", "Quarry has finished", "chat.extrautils.ender_quarry.finished");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", "Mining at: (", "chat.extrautils.ender_quarry.mining_at");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", " blocks scanned.", "chat.extrautils.ender_quarry.scanned");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", "Searching fence boundary at: (", "chat.extrautils.ender_quarry.searching");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", "Analyzing Fence boundary", "chat.extrautils.ender_quarry.analyzing");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", "Quarry is connected to more than fences on more than 2 sides", "chat.extrautils.ender_quarry.fence.too_many_sides");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", "Unable to detect fence boundary", "chat.extrautils.ender_quarry.fence.none");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "startFencing", "Quarry is only connected to fence boundary on one side", "chat.extrautils.ender_quarry.fence.one_side");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "checkForMarkers", "Found attached ender-marker", "chat.extrautils.ender_quarry.marker.found");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "checkForMarkers", "Quarry marker square is ambiguous - multiple markers found at (", "chat.extrautils.ender_quarry.marker.ambiguous");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "checkForMarkers", ") and (", "chat.extrautils.ender_quarry.marker.and");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "checkForMarkers", "Quarry marker square is incomplete", "chat.extrautils.ender_quarry.marker.incomplete");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "checkForMarkers", "Region created by ender markers is too small", "chat.extrautils.ender_quarry.marker.too_small");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "checkForMarkers", "Sucessfully established boundary", "chat.extrautils.ender_quarry.boundary_established");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "checkForMarkers", "Note: Quarry is configured not to self-chunkload.", "chat.extrautils.ender_quarry.no_self_chunkload");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "advFence", "Fence boundary splits at", "chat.extrautils.ender_quarry.fence.splits");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "advFence", "Fence boundary stops at", "chat.extrautils.ender_quarry.fence.stops");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "advFence", "Fence boundary must be square", "chat.extrautils.ender_quarry.fence.not_square");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "advFence", "Region created by fence is too small", "chat.extrautils.ender_quarry.fence.too_small");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "advFence", "Sucessfully established boundary", "chat.extrautils.ender_quarry.boundary_established");
        key(XU + "tileentity.enderquarry.TileEntityEnderQuarry", "advFence", "Note: Quarry is configured not to self-chunkload.", "chat.extrautils.ender_quarry.no_self_chunkload");
        key(XU + "tileentity.TileEntityEnderThermicLavaPump", "func_145845_h", "Pump will not function in this dimension", "chat.extrautils.lava_pump.dimension");
        key(XU + "tileentity.TileEntityEnderThermicLavaPump", "func_145845_h", "Unable to assign Chunkloader, this pump will not work", "chat.extrautils.lava_pump.chunk_ticket_failed");
        key(XU + "gui.GuiTransferNode", "func_146979_b", "Holding: ", "gui.extrautils.transfer_node.holding");
        key(XU + "gui.GuiTransferNode", "func_146979_b", "Powering: ", "gui.extrautils.transfer_node.powering");
        key(XU + "gui.GuiTransferNode", "func_146979_b", " Connection", "gui.extrautils.transfer_node.connections");
        key(XU + "gui.GuiTransferNode", "func_146979_b", "mB of ", "gui.extrautils.transfer_node.fluid_of");
        key(XU + "tileentity.enderconstructor.DynamicContainerEnderConstructor$WidgetEFText", "getConstructedText", "Ender-Flux: ", "gui.extrautils.qed.ender_flux");
        key(XU + "tileentity.enderconstructor.InventoryKraft", "func_145825_b", "Ender Crafting", "gui.extrautils.qed.title");
        key(XU + "tileentity.TileEntityTrashCan", "func_145825_b", "TrashCan", "gui.extrautils.trash_can.title");
    }
    @Override protected boolean extraTarget(String name) { return name.equals(CONTAINER); }
    @Override protected MethodVisitor extend(final String name, final String method, MethodVisitor visitor) {
        if (name.equals(XU + "tileentity.enderconstructor.DynamicContainerEnderConstructor$WidgetEFText") && method.equals("getConstructedText")) {
            // XU serializes the working flag as Byte, then mistakenly checks Boolean before casting it to Byte.
            return new MethodVisitor(Opcodes.ASM5, visitor) {
                @Override public void visitTypeInsn(int opcode, String type) {
                    super.visitTypeInsn(opcode, opcode == Opcodes.INSTANCEOF && type.equals("java/lang/Boolean") ? "java/lang/Byte" : type);
                }
            };
        }
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
