package github.com.gengyoubo.mfcl.client;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.Files;
import java.util.LinkedHashMap;

/** Opt-in complete host startup check, independent of any optional mod adapter. */
final class ClientStartupSmokeProbe {
    private final ModernContentBridge content;
    private boolean completed;
    private int frames;

    private ClientStartupSmokeProbe(ModernContentBridge content) { this.content = content; }
    static void install(ModernContentBridge content) {
        MinecraftForge.EVENT_BUS.addListener(new ClientStartupSmokeProbe(content)::tick);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (completed || event.phase != TickEvent.Phase.END || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        if (++frames < 10) return;
        completed = true;
        var report = new LinkedHashMap<String, Object>();
        try {
            if (!content.registered() || content.blocks().size() != content.declarations().blocks().size()
                    || content.items().size() != content.declarations().items().size())
                throw new IllegalStateException("Host registries did not receive all frozen legacy declarations");
            report.put("status", "PASS");
            report.put("blocks", content.blocks().size());
            report.put("items", content.items().size());
            report.put("catalogStacks", content.services().items().catalog().size());
            verifyVanillaMappings();
            report.put("vanillaRecipeMappingsVerified", true);
            report.put("parsedCraftingRecipes", parseRecipes(false));
            report.put("parsedSmeltingRecipes", parseRecipes(true));
            report.put("jvmUptimeMillis", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
            LogUtils.getLogger().info("MFCL_HOST_SMOKE_PASS: title screen reached, {} blocks and {} items registered", content.blocks().size(), content.items().size());
        } catch (Exception failure) {
            report.put("status", "FAIL"); report.put("failure", failure.toString());
            LogUtils.getLogger().error("MFCL_HOST_SMOKE_FAIL", failure);
        }
        try {
            var output = content.services().gameDirectory().resolve("logs/mfcl/host-startup-smoke.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { LogUtils.getLogger().error("MFCL_HOST_SMOKE_FAIL: cannot save startup report", failure); }
        mc.stop();
    }
    private void verifyVanillaMappings() throws Exception {
        for (String disc : java.util.List.of("13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait")) {
            var old = new github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack("minecraft:record_" + disc, 0, 1, "", "");
            var modern = content.gameplayStack(old);
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(modern.getItem()).toString().equals("minecraft:music_disc_" + disc)
                    || !content.gameplayStack(modern).key().equals(old.key()))
                throw new IllegalStateException("Music disc identity did not round trip: " + disc);
        }
        var modern = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SMOOTH_STONE, 7);
        var old = content.gameplayStack(modern);
        if (!old.key().equals("minecraft:double_stone_slab") || old.metadata() != 8 || old.count() != 7
                || !net.minecraft.world.item.ItemStack.matches(modern, content.gameplayStack(old)))
            throw new IllegalStateException("Seamless double slab did not round trip through smooth stone");
    }
    /** Uses the same native parser as CreateWorldScreen's datapack reload, for every exported recipe. */
    private int parseRecipes(boolean smelting) {
        int count = 0;
        var hashes = smelting ? content.crafting().smelting().stream().map(d -> d.hash()).toList()
                : content.crafting().definitions().stream().map(d -> d.hash()).toList();
        for (String hash : hashes) {
            var json = new com.google.gson.JsonObject();
            json.addProperty("type", smelting ? "mfcl:legacy_smelting" : "mfcl:legacy_crafting");
            json.addProperty("original", hash);
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mfcl", (smelting ? "legacy_smelting/" : "legacy_crafting/") + hash);
            net.minecraft.world.item.crafting.RecipeManager.fromJson(id, json);
            if (++count % 16384 == 0) LogUtils.getLogger().info("MFCL recipe parse smoke: {} {} recipes", count, smelting ? "smelting" : "crafting");
        }
        return count;
    }
}
