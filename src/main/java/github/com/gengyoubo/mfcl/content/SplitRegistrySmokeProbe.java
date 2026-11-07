package github.com.gengyoubo.mfcl.content;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;
import java.nio.file.Files;
import java.util.*;

/** Opt-in real Forge snapshot-injection regression; no world is opened or changed. */
public final class SplitRegistrySmokeProbe {
    private SplitRegistrySmokeProbe() {}
    public static void install(ModernContentBridge bridge) {
        var done = new java.util.concurrent.atomic.AtomicBoolean();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                var mc = Minecraft.getInstance();
                if (done.get() || event.phase != TickEvent.Phase.END || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                done.set(true); var result = new LinkedHashMap<String, Object>();
                try {
                    var variants = bridge.splitVariants();
                    if (variants.isEmpty()) throw new IllegalStateException("Split provider is not installed");
                    var snapshot = RegistryManager.ACTIVE.takeSnapshot(true);
                    var itemSnapshot = snapshot.get(Registries.ITEM.location());
                    var unrelated = ResourceLocation.parse("mfcl:unrelated_test_alias");
                    var base = ResourceLocation.parse("minecraft:stick");
                    itemSnapshot.aliases.put(unrelated, base);
                    for (var id : variants.keySet()) {
                        if (!itemSnapshot.ids.containsKey(id)) throw new IllegalStateException("Missing initial split item: " + id);
                        itemSnapshot.ids.removeInt(id);
                        itemSnapshot.aliases.put(id, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().lastIndexOf('/'))));
                    }
                    var first = variants.keySet().iterator().next();
                    SplitRegistryAliases.beforeSnapshot(snapshot, false, true);
                    SplitRegistryAliases.beforeSnapshot(snapshot, true, false);
                    if (!itemSnapshot.aliases.containsKey(first)) throw new IllegalStateException("Remote or non-frozen snapshot was changed");
                    var old = new ItemStack(Items.STICK, 3); old.getOrCreateTag().putString("test", "preserved");
                    var splits = new LegacyItemSplits(null);
                    // Creating an Item normally after registry freeze is forbidden; allocate an unregistered identity for this guard.
                    var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
                    var unregistered = (Item)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(Item.class);
                    if (splits.migrate(old, unregistered) != old) throw new IllegalStateException("Unregistered target consumed a stack");
                    var migrated = splits.migrate(old, Items.DIAMOND);
                    if (!migrated.is(Items.DIAMOND) || migrated.getCount() != 3 || !"preserved".equals(migrated.getTag().getString("test"))) throw new IllegalStateException("Registered migration lost stack data");
                    GameData.injectSnapshot(snapshot, true, true);
                    for (var entry : variants.entrySet()) {
                        if (BuiltInRegistries.ITEM.getOptional(entry.getKey()).orElse(null) != entry.getValue()
                                || !BuiltInRegistries.ITEM.getResourceKey(entry.getValue()).map(key -> key.location().equals(entry.getKey())).orElse(false))
                            throw new IllegalStateException("Split registry item lost after snapshot injection: " + entry.getKey());
                    }
                    if (itemSnapshot.aliases.containsKey(variants.keySet().iterator().next()) || !base.equals(itemSnapshot.aliases.get(unrelated)))
                        throw new IllegalStateException("Alias filtering was not selective");
                    result.put("variants", variants.size()); result.put("unregisteredMigrationPreserved", true); result.put("unrelatedAliasPreserved", true); result.put("status", "PASS");
                } catch (Throwable failure) {
                    result.put("status", "FAIL"); result.put("failure", failure.toString());
                    com.mojang.logging.LogUtils.getLogger().error("MFCL split registry smoke failed", failure);
                } finally {
                    try { GameData.revertToFrozen(); Files.writeString(mc.gameDirectory.toPath().resolve("split-registry-smoke.json"), new Gson().toJson(result)); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                    mc.stop();
                }
        });
    }
}
