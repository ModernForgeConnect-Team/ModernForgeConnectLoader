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
        ResourceLocation previous = null;
        for (var tab : content.declarations().tabs()) if (!tab.builtin()) {
            ResourceLocation id = id(tab);
            if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id) || customTabs.containsKey(tab.key())) throw new IllegalStateException("Creative tab ID collision: " + tab.key());
            var builder = CreativeModeTab.builder().title(Component.translatable(tab.nameKey())).icon(() -> icon(tab))
                    .displayItems((parameters, output) -> stacks(tab).forEach(output::accept));
            if (!tab.showTitle()) builder.hideTitle();
            if (!tab.scroll()) builder.noScrollBar();
            if (tab.searchBar()) builder.withSearchBar(tab.searchWidth());
            if (previous != null) builder.withTabsAfter(previous);
            var modern = builder.build(); registry.register(id, modern); customTabs.put(tab.key(), modern); previous = id;
        }
        if (!fallback.isEmpty()) registry.register(ResourceLocation.tryParse("mfcl:unclassified_1_7_10"), CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mfcl.unclassified_1_7_10")).icon(() -> icon(fallback.get(0)))
                .displayItems((parameters, output) -> { for (var tab : fallback) stacks(tab).forEach(output::accept); }).build());
    }
    void buildVanillaContents(BuildCreativeModeTabContentsEvent event) {
        for (var tab : content.declarations().tabs()) if (tab.builtin() && event.getTabKey().equals(VANILLA.get(tab.label())))
            stacks(tab).forEach(event::accept);
    }
    public CreativeModeTab target(LegacyCreativeTabs.Tab tab) {
        if (!tab.builtin()) return Objects.requireNonNull(customTabs.get(tab.key()));
        var key = VANILLA.get(tab.label());
        return BuiltInRegistries.CREATIVE_MODE_TAB.get(key == null ? ResourceLocation.tryParse("mfcl:unclassified_1_7_10") : key.location());
    }
    public Map<String, CreativeModeTab> customTabs() { return Collections.unmodifiableMap(customTabs); }
}
