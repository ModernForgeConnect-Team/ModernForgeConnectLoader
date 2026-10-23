package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Actual TC4 container/validity/wear/persistence acceptance, optionally with real modern Tinkers tools. */
final class BoreHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private boolean done;
    private BoreHostSmokeProbe(ModernContentBridge content) { this.content = content; }
    static void install(ModernContentBridge content) { MinecraftForge.EVENT_BUS.addListener(new BoreHostSmokeProbe(content)::tick); }
    private void check(boolean result, String message) { if (!result) throw new IllegalStateException(message); proof.put(message, true); }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        done = true;
        try {
            var services = content.services();
            services.items().inLegacy(() -> {
                var level = event.getServer().overworld(); var world = services.worlds().bound(level);
                var loader = services.loader(); var type = loader.loadClass("net.minecraft.item.ItemStack");
                var boreType = loader.loadClass("thaumcraft.common.tiles.TileArcaneBore");
                var valid = boreType.getDeclaredMethod("areItemsValid"); valid.setAccessible(true);
                var wear = boreType.getDeclaredMethod("mfcl$damagePickaxe", int.class, type, loader.loadClass("net.minecraft.world.World")); wear.setAccessible(true);
                var pos = new BlockPos(0, 70, 0);
                level.setBlockAndUpdate(pos, content.blocks().get("Thaumcraft:blockWoodenDevice").defaultBlockState().setValue(ModernContentBridge.METADATA, 5));
                Object bore = ((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();
                Object focus = services.items().materialize(new LegacyClientData.Stack("Thaumcraft:FocusExcavation", 0, 1, "", ""));
                boreType.getMethod("func_70299_a", int.class, type).invoke(bore, 0, focus);
                var actor = services.tiles().actor(world, new LegacyTiles.Placer(UUID.randomUUID(), "BoreProbe", 0, 70, 0, 0, 0), null);
                Object inventory = loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(actor);
                Object container = loader.loadClass("thaumcraft.common.container.ContainerArcaneBore")
                        .getConstructor(loader.loadClass("net.minecraft.entity.player.InventoryPlayer"), boreType).newInstance(inventory, bore);
                Object slot = LegacyDeviceContainers.slots(container).get(1);
                var netherite = new ItemStack(Items.NETHERITE_PICKAXE);
                netherite.enchant(Enchantments.BLOCK_FORTUNE, 3); netherite.enchant(Enchantments.BLOCK_EFFICIENCY, 4);
                netherite.setHoverName(net.minecraft.network.chat.Component.literal("Bore native tool"));
                netherite.getOrCreateTag().putString("mod_data", "keep me");
                Object old = services.items().materialize(content.gameplayStack(netherite));
                check((boolean) slot.getClass().getMethod("func_75214_a", type).invoke(slot, old), "netheriteAcceptedByOriginalSlot");
                boreType.getMethod("func_70299_a", int.class, type).invoke(bore, 1, old);
                check((boolean)valid.invoke(bore), "netheriteEnablesOriginalBore");
                check(boreType.getField("fortune").getInt(bore)==3 && boreType.getField("speed").getInt(bore)==4, "effectiveEnchantmentsReachBore");
                old = wear.invoke(null, 1, old, world);
                var nativeResult = content.gameplayStack(services.items().snapshot(old));
                check(nativeResult.is(Items.NETHERITE_PICKAXE) && nativeResult.getDamageValue()==1 && nativeResult.getTag().getString("mod_data").equals("keep me") && nativeResult.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE)==3, "nativeWearAndNbtPreserved");
                check(content.mapStack(services.items().snapshot(old)).is(Items.NETHERITE_PICKAXE), "originalGuiMapsNativeTool");
                boreType.getMethod("func_70299_a", int.class, type).invoke(bore, 1, old);
                byte[] saved = services.tiles().save(bore);
                Object restored = services.tiles().snapshotInstance(null, saved); services.tiles().load(restored, saved);
                var reloaded = services.inventories().get(restored, 1);
                check(content.gameplayStack(reloaded).serializeNBT().equals(nativeResult.serializeNBT()), "originalTileSaveReloadPreservesTool");
                boreType.getMethod("func_70299_a", int.class, type).invoke(bore, 1, new Object[]{null});
                var playerSlots = new ArrayList<LegacyClientData.Stack>(Collections.nCopies(36, null));
                playerSlots.set(9, content.gameplayStack(new ItemStack(Items.NETHERITE_PICKAXE)));
                services.playerInventory().write(actor, playerSlots);
                var shift = container.getClass().getMethod("func_82846_b", loader.loadClass("net.minecraft.entity.player.EntityPlayer"), int.class);
                shift.invoke(container, actor, 2);
                check(content.gameplayStack(services.inventories().get(bore, 1)).is(Items.NETHERITE_PICKAXE), "pristineNetheriteShiftClickIn");
                shift.invoke(container, actor, 1);
                check(services.inventories().get(bore, 1)==null && services.playerInventory().read(actor).stream().filter(Objects::nonNull).map(content::gameplayStack).filter(s->s.is(Items.NETHERITE_PICKAXE)).mapToInt(ItemStack::getCount).sum()==1, "nativeShiftClickOutConservesTool");
                var last = netherite.copy(); last.setDamageValue(last.getMaxDamage()-1);
                old = services.items().materialize(content.gameplayStack(last));
                boreType.getMethod("func_70299_a", int.class, type).invoke(bore, 1, old);
                check((boolean)valid.invoke(bore), "lastNativeDurabilityIsUsable");
                check(type.getField("field_77994_a").getInt(wear.invoke(null, 1, old, world))==0, "ordinaryNativeToolBreaksNormally");
                var unbreakable = netherite.copy(); unbreakable.getOrCreateTag().putBoolean("Unbreakable", true);
                old = services.items().materialize(content.gameplayStack(unbreakable));
                check(content.gameplayStack(services.items().snapshot(wear.invoke(null, 3, old, world))).getDamageValue()==0, "unbreakableToolTakesNoWear");
                var vanilla = services.items().materialize(new LegacyClientData.Stack("minecraft:diamond_pickaxe", 0, 1, "", ""));
                check(services.items().snapshot(wear.invoke(null, 1, vanilla, world)).metadata()==1, "originalPickaxeWearUnchanged");
                check(!HostToolStacks.accepts(new ItemStack(Items.NETHERITE_AXE)), "axesRejected");
                var tinkerId = ResourceLocation.parse("tconstruct:pickaxe");
                if (BuiltInRegistries.ITEM.containsKey(tinkerId)) {
                    var build = Class.forName("slimeknights.tconstruct.library.tools.helper.ToolBuildHandler");
                    var modifiable = Class.forName("slimeknights.tconstruct.library.tools.item.IModifiable");
                    var material = Class.forName("slimeknights.tconstruct.library.materials.definition.MaterialVariant");
                    // Build a real, data-driven iron tool with no wear-reducing traits.
                    var create = Arrays.stream(build.getMethods()).filter(m->m.getName().equals("createSingleMaterial") && m.getParameterCount()==2).findFirst().orElseThrow();
                    var materialId = Class.forName("slimeknights.tconstruct.library.materials.definition.MaterialId").getConstructor(String.class).newInstance("tconstruct:iron");
                    Object variant = material.getMethod("of", Class.forName("slimeknights.tconstruct.library.materials.definition.MaterialId"), String.class).invoke(null, materialId, "");
                    Object tinkerItem = BuiltInRegistries.ITEM.get(tinkerId);
                    proof.put("tinkerItemClass", tinkerItem.getClass().getName());
                    check(modifiable.isInstance(tinkerItem), "realTinkersItemImplementsModifiable");
                    ItemStack tinker = (ItemStack)create.invoke(null, tinkerItem, variant);
                    check(modifiable.isInstance(tinker.getItem()) && HostToolStacks.accepts(tinker), "realTinkersPickaxeDetected");
                    tinker.setDamageValue(tinker.getMaxDamage()-1);
                    old = services.items().materialize(content.gameplayStack(tinker));
                    check((boolean)slot.getClass().getMethod("func_75214_a", type).invoke(slot,old), "realTinkersPickaxeAcceptedByOriginalSlot");
                    boreType.getMethod("func_70299_a",int.class,type).invoke(bore,1,old);
                    check((boolean)valid.invoke(bore), "lastTinkersDurabilityIsUsable");
                    old = wear.invoke(null,1,old,world);
                    ItemStack broken = content.gameplayStack(services.items().snapshot(old));
                    check(broken.getCount()==1 && broken.getTag().getBoolean("tic_broken"), "tinkersToolBecomesBrokenWithoutDeletion");
                    boreType.getMethod("func_70299_a",int.class,type).invoke(bore,1,old);
                    check(!(boolean)valid.invoke(bore), "brokenTinkersToolStopsOriginalBore");
                    byte[] brokenSaved = services.tiles().save(bore);
                    Object brokenReloaded = services.tiles().snapshotInstance(null, brokenSaved); services.tiles().load(brokenReloaded, brokenSaved);
                    check(content.gameplayStack(services.inventories().get(brokenReloaded, 1)).serializeNBT().equals(broken.serializeNBT()) && !(boolean)valid.invoke(brokenReloaded), "brokenTinkersStateSurvivesSaveReload");
                    var before = broken.serializeNBT();
                    check(content.gameplayStack(services.items().snapshot(wear.invoke(null,5,old,world))).serializeNBT().equals(before), "brokenTinkersToolTakesNoFurtherWear");
                    broken.setDamageValue(0);
                    old = services.items().materialize(content.gameplayStack(broken));
                    boreType.getMethod("func_70299_a",int.class,type).invoke(bore,1,old);
                    check((boolean)valid.invoke(bore), "repairedTinkersToolResumesBore");
                } else proof.put("tinkers", "SKIPPED: install -PtinkersTestVersion to validate real Tinkers");
                return null;
            });
            proof.put("status", "PASS");
        } catch (Throwable failure) {
            proof.put("status", "FAIL"); proof.put("failure", failure.toString());
            com.mojang.logging.LogUtils.getLogger().error("MFCL_BORE_SMOKE_FAIL", failure);
        }
        try {
            var path = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/bore-smoke.json"); Files.createDirectories(path.getParent());
            Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_BORE_SMOKE_DONE: {}", proof);
        event.getServer().halt(false);
    }
}
