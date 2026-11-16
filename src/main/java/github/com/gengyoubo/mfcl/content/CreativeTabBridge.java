package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyCreativeTabs;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.RegisterEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Maps each original category; it does not replace mod categories with a loader-owned collection. */
public final class CreativeTabBridge {
    private final ModernContentBridge content;
    private final Map<String, CreativeModeTab> customTabs = new LinkedHashMap<>();
    private final Map<Item,Boolean> spawnEggs = new IdentityHashMap<>();
    private final List<LegacyCreativeTabs.Tab> fallback;
    private static final Map<String, ResourceKey<CreativeModeTab>> VANILLA = Map.ofEntries(
            Map.entry("buildingBlocks", CreativeModeTabs.BUILDING_BLOCKS), Map.entry("decorations", CreativeModeTabs.FUNCTIONAL_BLOCKS),
            Map.entry("redstone", CreativeModeTabs.REDSTONE_BLOCKS), Map.entry("transportation", CreativeModeTabs.TOOLS_AND_UTILITIES),
            Map.entry("misc", CreativeModeTabs.INGREDIENTS), Map.entry("food", CreativeModeTabs.FOOD_AND_DRINKS),
            Map.entry("tools", CreativeModeTabs.TOOLS_AND_UTILITIES), Map.entry("combat", CreativeModeTabs.COMBAT),
            Map.entry("brewing", CreativeModeTabs.INGREDIENTS), Map.entry("materials", CreativeModeTabs.INGREDIENTS));
    CreativeTabBridge(ModernContentBridge content) {
        this.content = content;
        fallback = content.declarations().tabs().stream().filter(tab -> tab.builtin() && !VANILLA.containsKey(tab.label()) && !tab.contents().isEmpty()).toList();
    }
    public static ResourceLocation id(LegacyCreativeTabs.Tab tab) {
        String[] key = tab.key().split(":", 2);
        String namespace = key[0].toLowerCase(Locale.ROOT);
        ResourceLocation parsed = ResourceLocation.tryParse(namespace + ":creative/" + key[1].toLowerCase(Locale.ROOT));
        if (parsed == null) parsed = ResourceLocation.tryParse(namespace + ":creative/" + HexFormat.of().formatHex(key[1].getBytes(StandardCharsets.UTF_8)));
        return parsed != null ? parsed : ModernContentBridge.id("legacy_tab:" + tab.key());
    }
    public List<ItemStack> stacks(LegacyCreativeTabs.Tab tab) {
        Set<ItemStack> unique = ItemStackLinkedSet.createTypeAndTagSet();
        for (var original : tab.contents()) {
            ItemStack stack = content.mapStack(original);
            if (stack.isEmpty()) throw new UnsupportedOperationException("Unmapped original creative entry in " + tab.key() + ": " + original);
            stack.setCount(1); unique.add(stack);
        }
        return List.copyOf(unique);
    }
    public ItemStack icon(LegacyCreativeTabs.Tab tab) {
        ItemStack icon = tab.icon() == null ? ItemStack.EMPTY : content.mapStack(tab.icon());
        if (icon.isEmpty() && !tab.contents().isEmpty()) icon = content.mapStack(tab.contents().get(0));
        if (!icon.isEmpty()) icon.setCount(1);
        return icon;
    }
    void register(RegisterEvent.RegisterHelper<CreativeModeTab> registry) {
        ResourceLocation previous = CreativeModeTabs.SPAWN_EGGS.location();
        for (var tab : content.declarations().tabs()) if (!tab.builtin()) {
            ResourceLocation id = id(tab);
            if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id) || customTabs.containsKey(tab.key())) throw new IllegalStateException("Creative tab ID collision: " + tab.key());
            var builder = CreativeModeTab.builder().title(Component.translatableWithFallback(tab.nameKey(),tab.label())).icon(() -> icon(tab))
                    .displayItems((parameters, output) -> stacks(tab).forEach(output::accept));
            if (!tab.showTitle()) builder.hideTitle();
            if (!tab.scroll()) builder.noScrollBar();
            if (tab.searchBar()) builder.withSearchBar(tab.searchWidth());
            // Forge's "tabsBefore" are predecessors of this tab, not tabs to place this one before.
            // Never name a Forge default tab (hotbar/search/op/inventory): putEdge would pull it into the sorted
            // pages, where it sorts by name ahead of redstone and pushes spawn eggs onto page two.
            builder.withTabsBefore(previous);
            var modern = builder.build(); registry.register(id, modern); customTabs.put(tab.key(), modern); previous = id;
        }
        if (!fallback.isEmpty()) registry.register(ResourceLocation.tryParse("mfcl:unclassified_1_7_10"), CreativeModeTab.builder()
                .withTabsBefore(previous)
                .title(Component.translatable("itemGroup.mfcl.unclassified_1_7_10")).icon(() -> icon(fallback.get(0)))
                .displayItems((parameters, output) -> { for (var tab : fallback) stacks(tab).forEach(output::accept); }).build());
    }
    void buildVanillaContents(BuildCreativeModeTabContentsEvent event) {
        Set<ItemStack> entries=ItemStackLinkedSet.createTypeAndTagSet();
        for (var tab : content.declarations().tabs()) if (tab.builtin()
                &&(event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)||event.getTabKey().equals(VANILLA.get(tab.label())))) for(var stack:stacks(tab)) {
            var key=isSpawnEgg(stack)?CreativeModeTabs.SPAWN_EGGS:VANILLA.get(tab.label());
            if(event.getTabKey().equals(key))entries.add(stack);
        }
        entries.forEach(event::accept);
    }
    private boolean isSpawnEgg(ItemStack stack) {
        return spawnEggs.computeIfAbsent(stack.getItem(),item->{
            try {var original=content.renderSnapshot(stack);return original!=null&&content.services().items().isSpawnEgg(original);}
            catch(Exception failure){throw new IllegalStateException("Cannot classify original creative item "+item,failure);}
        });
    }
    public CreativeModeTab target(LegacyCreativeTabs.Tab tab,ItemStack stack) {
        if(tab.builtin()&&isSpawnEgg(stack))return BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.SPAWN_EGGS.location());
        return target(tab);
    }
    public CreativeModeTab target(LegacyCreativeTabs.Tab tab) {
        if (!tab.builtin()) return Objects.requireNonNull(customTabs.get(tab.key()));
        var key = VANILLA.get(tab.label());
        return BuiltInRegistries.CREATIVE_MODE_TAB.get(key == null ? ResourceLocation.tryParse("mfcl:unclassified_1_7_10") : key.location());
    }
    public Map<String, CreativeModeTab> customTabs() { return Collections.unmodifiableMap(customTabs); }
}
