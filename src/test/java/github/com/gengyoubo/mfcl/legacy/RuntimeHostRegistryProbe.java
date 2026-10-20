package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Exercises the actual sealed 1.7.10 runtime, including vanilla numeric lookup methods. */
final class RuntimeHostRegistryProbe {
    static void verify(LegacyServices services, Map<String, Object> report) throws Exception {
        var loader = services.loader();
        var blockType = loader.loadClass("net.minecraft.block.Block");
        var itemType = loader.loadClass("net.minecraft.item.Item");
        Object itemRegistry = itemType.getField("field_150901_e").get(null);
        Object blockRegistry = blockType.getField("field_149771_c").get(null);
        var itemKeys = Set.copyOf((Set<?>)itemRegistry.getClass().getMethod("func_148742_b").invoke(itemRegistry));
        var blockKeys = Set.copyOf((Set<?>)blockRegistry.getClass().getMethod("func_148742_b").invoke(blockRegistry));
        Object diamond = services.items().legacyItem("minecraft:diamond");
        int diamondId = (int)itemType.getMethod("func_150891_b", itemType).invoke(null, diamond);
        Object item = itemType.getConstructor().newInstance();
        LegacyHostRegistry.registerItem(loader, "mfcl_host:test/item", item);
        var material = loader.loadClass("net.minecraft.block.material.Material");
        var constructor = blockType.getDeclaredConstructor(material); constructor.setAccessible(true);
        Object block = constructor.newInstance(material.getField("field_151576_e").get(null));
        Object blockItem = loader.loadClass("net.minecraft.item.ItemBlock").getConstructor(blockType).newInstance(block);
        LegacyHostRegistry.registerBlock(loader, "mfcl_host:test/block", block, blockItem);
        var gameRegistry = loader.loadClass("cpw.mods.fml.common.registry.GameRegistry");
        if (!gameRegistry.getMethod("findUniqueIdentifierFor", itemType).invoke(null, item).toString().equals("mfcl_host:test/item")
                || !gameRegistry.getMethod("findUniqueIdentifierFor", blockType).invoke(null, block).toString().equals("mfcl_host:test/block"))
            throw new AssertionError("Original stable-name query did not resolve host identities");
        blockType.getMethod("func_149663_c", String.class).invoke(block, "mfcl_host:test/block");
        var translated = new java.util.concurrent.atomic.AtomicReference<>("深板岩");
        LegacyHostRegistry.label(loader, "tile.mfcl_host:test/block.name", translated::get);
        services.items().language("zh_cn");
        var displayStack = new LegacyClientData.Stack("mfcl_host:test/block", 0, 1, "", "");
        if (!services.items().displayName(displayStack).equals("深板岩")) throw new AssertionError("Original item label shows an internal translation key");
        report.put("vanillaZhCnDiamondName", services.items().displayName(new LegacyClientData.Stack("minecraft:diamond", 0, 1, "", "")));
        var translate = loader.loadClass("net.minecraft.util.StatCollector").getMethod("func_74838_a", String.class);
        boolean zhOriginal = !services.items().missingKeys("zh_cn").contains("tile.oreDiamond.name");
        String originalOre = (String) translate.invoke(null, "tile.oreDiamond.name");
        services.items().supplement("zh_cn", Map.of("tile.oreDiamond.name", "MFCL supplement", "mfcl.test.supplement", "补充"));
        services.items().language("zh_cn");
        if (!translate.invoke(null, "mfcl.test.supplement").equals("补充")) throw new AssertionError("Language supplement was not applied");
        if (!translate.invoke(null, "tile.oreDiamond.name").equals(zhOriginal ? originalOre : "MFCL supplement"))
            throw new AssertionError("Supplement must fill only keys the original language leaves undefined");
        services.items().override("zh_CN", Map.of("tile.oreDiamond.name", "MFCL override"));
        services.items().language("zh_cn");
        if (!translate.invoke(null, "tile.oreDiamond.name").equals("MFCL override")) throw new AssertionError("Language override did not replace the original translation");
        report.put("genericLanguageSupplementVerified", true);
        translated.set("Deepslate"); services.items().language("en_us");
        if (!services.items().displayName(displayStack).equals("Deepslate")) throw new AssertionError("Host labels did not follow language changes");
        int itemId = (int)itemType.getMethod("func_150891_b", itemType).invoke(null, item);
        int blockId = (int)blockType.getMethod("func_149682_b", blockType).invoke(null, block);
        if (itemId <= 0 || blockId <= 0 || itemId == blockId || itemId == diamondId) throw new AssertionError("Host identities collided");
        if (itemType.getMethod("func_150899_d", int.class).invoke(null, itemId) != item
                || blockType.getMethod("func_149729_e", int.class).invoke(null, blockId) != block
                || itemType.getMethod("func_150898_a", blockType).invoke(null, block) != blockItem
                || (int)itemType.getMethod("func_150891_b", itemType).invoke(null, blockItem) != blockId)
            throw new AssertionError("Original numeric lookup did not resolve host identities");
        var snapshot = new LegacyClientData.Stack("mfcl_host:test/block", 0, 3, "", "");
        if (!services.items().snapshot(services.items().materialize(snapshot)).key().equals(snapshot.key()))
            throw new AssertionError("Host identity was lost in an original stack round trip");
        if (!itemKeys.equals(itemRegistry.getClass().getMethod("func_148742_b").invoke(itemRegistry))
                || !blockKeys.equals(blockRegistry.getClass().getMethod("func_148742_b").invoke(blockRegistry)))
            throw new AssertionError("Host representations changed sealed FML registries");
        if (itemType.getMethod("func_150899_d", int.class).invoke(null, diamondId) != diamond)
            throw new AssertionError("Original item lookup changed");
        try {
            itemRegistry.getClass().getMethod("func_148756_a", int.class, String.class, Object.class).invoke(itemRegistry, -1, "test:forbidden", itemType.getConstructor().newInstance());
            throw new AssertionError("Late mod registration was allowed");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IllegalStateException)) throw expected;
        }
        report.put("genericReadOnlyHostRegistryVerified", true);
    }
}
