package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless acceptance for the host vanilla bow with original bow events: TC4 primal arrows draw and fire without vanilla
 * ammo, take priority over vanilla arrows, and a bow with only vanilla arrows still behaves natively.
 */
final class BowHostSmokeProbe {
    private record Case(String name, int primal, int vanilla, int primalAfter, int vanillaAfter) {}
    private static final Case[] CASES = {
            new Case("primalOnly", 4, 0, 3, 0), new Case("primalAndVanilla", 4, 4, 3, 4), new Case("vanillaOnly", 0, 4, 0, 3)};
    private final ModernContentBridge bridge;
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private int index, wait = -1, arrowsBefore;
    private net.minecraft.world.entity.player.Player player;
    private boolean done;
    private BowHostSmokeProbe(ModernContentBridge bridge) { this.bridge = bridge; }
    static void install(ModernContentBridge bridge) { MinecraftForge.EVENT_BUS.addListener(new BowHostSmokeProbe(bridge)::tick); }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        MinecraftServer server = event.getServer(); ServerLevel level = server.overworld();
        try {
            if (player == null) {
                var spawn = level.getSharedSpawnPos();
                player = FakePlayerFactory.getMinecraft(level);
                player.setPos(spawn.getX() + .5, level.getMaxBuildHeight() - 10, spawn.getZ() + .5);
                player.setXRot(0); player.setYRot(0);
            }
            if (wait > 0) { wait--; return; }
            if (wait == 0) { verify(level, CASES[index]); wait = -1; if (++index == CASES.length) { proof.put("status", "PASS"); finish(server, null); } return; }
            shoot(level, CASES[index]); wait = 5;
        } catch (Throwable failure) { proof.put("status", "FAIL"); proof.put("failure", failure.toString()); finish(server, failure); }
    }

    private Item primal() {
        var stack = bridge.stack("Thaumcraft:PrimalArrow", 0, 1, "");
        if (stack.isEmpty()) throw new IllegalStateException("Thaumcraft:PrimalArrow is not registered");
        return stack.getItem();
    }

    private void shoot(ServerLevel level, Case scenario) {
        var inventory = player.getInventory(); inventory.clearContent(); inventory.selected = 0;
        inventory.setItem(0, new ItemStack(Items.BOW));
        if (scenario.primal() > 0) inventory.setItem(1, bridge.stack("Thaumcraft:PrimalArrow", 2, scenario.primal(), ""));
        if (scenario.vanilla() > 0) inventory.setItem(2, new ItemStack(Items.ARROW, scenario.vanilla()));
        arrowsBefore = arrows(level);
        var bow = player.getItemInHand(InteractionHand.MAIN_HAND);
        var drawn = bow.use(level, player, InteractionHand.MAIN_HAND);
        if (!drawn.getResult().consumesAction() || !player.isUsingItem()) throw new IllegalStateException(scenario.name() + ": bow did not draw: " + drawn.getResult());
        // Full draw (20 ticks), exactly as LivingEntity.releaseUsingItem would report it.
        bow.releaseUsing(level, player, bow.getUseDuration() - 20);
        player.stopUsingItem();
    }

    private void verify(ServerLevel level, Case scenario) {
        int primal = count(primal()), vanilla = count(Items.ARROW), fired = arrows(level) - arrowsBefore;
        var bow = player.getInventory().getItem(0);
        var result = new LinkedHashMap<String, Object>();
        result.put("primalLeft", primal); result.put("vanillaLeft", vanilla); result.put("fired", fired); result.put("bowDamage", bow.getDamageValue());
        proof.put(scenario.name(), result);
        if (primal != scenario.primalAfter() || vanilla != scenario.vanillaAfter()) throw new IllegalStateException(scenario.name() + ": wrong ammo use " + result);
        if (fired != 1) throw new IllegalStateException(scenario.name() + ": expected one projectile " + result);
        if (!bow.is(Items.BOW) || bow.getDamageValue() != 1) throw new IllegalStateException(scenario.name() + ": bow not worn once " + result);
    }

    private int count(Item item) {
        int total = 0; var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(item)) total += inventory.getItem(slot).getCount();
        return total;
    }

    /** Plain arrows become host arrows; original mod arrows (primal arrows) are hosted with their own logic. */
    private int arrows(ServerLevel level) {
        var area = player.getBoundingBox().inflate(256);
        return level.getEntitiesOfClass(AbstractArrow.class, area).size() + level.getEntitiesOfClass(LegacyHostedEntity.class, area).size();
    }

    private void finish(MinecraftServer server, Throwable failure) {
        done = true;
        try {
            var path = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/server-1.7.10-smoke-bow.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception ignored) { }
        if (failure == null) LogUtils.getLogger().info("MFCL_BOW_SMOKE_PASS: {}", proof);
        else LogUtils.getLogger().error("MFCL_BOW_SMOKE_FAIL: " + proof, failure);
        server.halt(false);
    }
}
