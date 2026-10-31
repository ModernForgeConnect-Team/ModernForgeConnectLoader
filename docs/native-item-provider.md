# Splitting original items into host items

`github.com.gengyoubo.mfcl.api.spi.LegacyItemProvider` is an optional SPI in 0.2.11-dev5, the item counterpart of [`LegacyBlockProvider`](native-block-provider.md). It lets an addon give one original item several host items — one per metadata subtype, or per NBT rule such as a TC4 wand's rod + cap + sceptre — so host recipes, tags, models and recipe viewers can tell them apart. Old code still sees exactly one original item.

None of the other APIs can do this: `LegacyMappings.register` cannot replace `mfcl:item_stack`, `LegacyHostRepresentations.item` gives an addon's own host item a new old-side identity (TC4 would not treat it as a wand), `LegacyBlockProvider` only covers blocks and `LegacyRedirects` only rewrites calls inside original classes.

## Registration

Register with `LegacyItemProviders.register(provider)` from the addon's mod constructor, or package `META-INF/services/github.com.gengyoubo.mfcl.api.spi.LegacyItemProvider`. Providers are deduplicated by implementation class. MFCL asks every provider once per original item inside the host item registration window, before any original item registers. A provider returns null for keys it does not split; two providers splitting the same key fail registration.

`split(legacyKey, declaration)` receives the original's declared subtypes, its maximum damage and (on demand) its creative listing with NBT. It returns an `ItemSplit`:

- `variants()` – stable lowercase names (`[a-z0-9_.-]`). Each registers host item `<namespace>:<path>/<variant>` next to the unchanged original host ID, e.g. `thaumcraft:wand_casting/greatwood_gold_sceptre`. A taken ID fails registration.
- `select(stack)` – the variant for an original stack, or null to keep it on the original host item. It gets the metadata, the original 1.7.10 SNBT and `hostTag()`, a host `CompoundTag` copy of the original NBT (converted only when asked). It must be deterministic; MFCL caches results.

`LegacyItemProvider.byMetadata(declaration)` splits every declared subtype into `meta_<n>`; `byMetadata(Map<Integer,String>)` names them explicitly. Undeclared metadata stays on the original host item. Damageable items keep durability in metadata, so `byMetadata(declaration)` refuses them.

```java
public final class WandSplits implements LegacyItemProvider {
    private static final List<String> RODS = List.of("wood", "greatwood", "silverwood");
    private static final List<String> CAPS = List.of("iron", "gold", "thaumium");
    @Override public ItemSplit split(String key, ItemDeclaration declaration) {
        if (key.equals("Thaumcraft:ItemResource")) return LegacyItemProvider.byMetadata(declaration);
        if (!key.equals("Thaumcraft:WandCasting")) return null;
        var variants = new ArrayList<String>();
        for (String rod : RODS) for (String cap : CAPS) { variants.add(rod + "_" + cap); variants.add(rod + "_" + cap + "_sceptre"); }
        return new ItemSplit() {
            @Override public List<String> variants() { return variants; }
            @Override public String select(Stack stack) {
                if (!(stack.hostTag() instanceof CompoundTag tag)) return null;
                String name = tag.getString("rod") + "_" + tag.getString("cap") + (tag.getByte("sceptre") == 1 ? "_sceptre" : "");
                return variants.contains(name) ? name : null; // unknown combinations stay on the original host item
            }
        };
    }
}
```

## Conversion

Every split item is a mirrored original item bound to the same declaration: same name, tooltip, stack limit, durability, use, rendering, tint, armor and placement. Its stack keeps the original metadata and complete SNBT in `mfcl_legacy`, so host → old conversion restores the original item, metadata and NBT unchanged, whichever split item holds it.

Old → host conversion (`mfcl:item_stack`, inventories, drops, crafting results, creative tabs, pick-block) chooses the split item through `select`. When old code changes a held stack's NBT (a new cap), the written-back stack moves to the matching split item; `LegacyMappings.applyTo` keeps host-owned tags and capability data across that move.

Creative tabs list the original's own creative stacks, each on its selected split item; a variant no creative stack selects is not listed. A stack created directly on a split item (`/give thaumcraft:wand_casting/greatwood_gold`) carries no original metadata or NBT, so old code sees the plain original item and the stack migrates to whatever that selects.

A split block item does not claim its block, so `Block.asItem()` stays the original host item. Original recipes match any split item of the right original item (strict-NBT recipes still compare NBT). Host tags of an original item include all its split items, and an ore dictionary name that lists only some subtypes tags exactly the split items whose subtypes it fully covers (e.g. `forge:ingots/thaumium` → `thaumcraft:item_resource/meta_2`).

## Saved worlds

Stacks saved on the original host item, or on a split item the current rules no longer select, move to the selected item when they load: player inventories and ender chests, item entities, item frames, entity containers and equipment on level join, and host block entity containers (`Container` or a modifiable item handler) on chunk load. Original tile entities hold original stacks and convert on every access, so they need no migration. A stack the migration does not reach (nested in a shulker box, say) still works as the original item and moves the next time old code writes it. A removed split variant's saved ID is remapped to its original host item, then migrated. Renaming a variant therefore loses nothing but should still be treated as a breaking change for host recipes and tags that name it.
