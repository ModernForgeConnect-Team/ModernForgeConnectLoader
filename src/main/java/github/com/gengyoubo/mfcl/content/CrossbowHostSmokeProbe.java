package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/**
 * Headless acceptance for TC4 primal arrows on the host crossbow: a water arrow loads, fires as the original hosted
 * EntityPrimalArrow (no host arrow), and its original impact applies slowness to a host pig.
 */
final class CrossbowHostSmokeProbe {
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private net.minecraft.world.entity.player.Player player;
    private UUID pig;
    private int stage, wait, ticks;
    private boolean done, sawHosted;
    private CrossbowHostSmokeProbe() {}
    static void install(ModernContentBridge bridge) { MinecraftForge.EVENT_BUS.addListener(new CrossbowHostSmokeProbe()::tick); }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        MinecraftServer server = event.getServer(); ServerLevel level = server.overworld();
        try {
            if (wait > 0) { wait--; return; }
            switch (stage) {
                case 0 -> { setup(level); stage = 1; wait = 5; }
                case 1 -> { load(level); stage = 2; wait = 2; }
                case 2 -> { fire(level); stage = 3; }
                case 3 -> {
                    sawHosted |= hosted(level) > 0;
                    boolean slowed = level.getEntity(pig) instanceof LivingEntity target && target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    if (slowed || ++ticks > 60) verify(level, slowed);
                }
                default -> { }
            }
        } catch (Throwable failure) { proof.put("status", "FAIL"); proof.put("failure", failure.toString()); finish(server, failure); }
    }
    private void setup(ServerLevel level) {
        var spawn = level.getSharedSpawnPos(); double y = level.getMaxBuildHeight() - 10;
        player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(spawn.getX() + .5, y, spawn.getZ() + .5, 0, 0);
        // The probe world is saved between runs; earlier probe pigs would otherwise stand in the arrow's path.
        for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, new net.minecraft.world.phys.AABB(spawn).inflate(16, 400, 16))) old.discard();
        var target = EntityType.PIG.create(level);
        target.moveTo(spawn.getX() + .5, y + 1.1, spawn.getZ() + 4.5, 180, 0); target.setNoAi(true); target.setNoGravity(true);
        level.addFreshEntity(target); pig = target.getUUID();
        var inventory = player.getInventory(); inventory.clearContent(); inventory.selected = 0;
        inventory.setItem(0, new ItemStack(Items.CROSSBOW));
        // Water arrows (metadata 2) slow what they hit; vanilla arrows next to them must not be preferred.
        inventory.setItem(1, ModernContentBridge.active().stack("Thaumcraft:PrimalArrow", 2, 4, ""));
        inventory.setItem(2, new ItemStack(Items.ARROW, 4));
    }
    private void load(ServerLevel level) {
        var crossbow = player.getItemInHand(InteractionHand.MAIN_HAND);
        var started = crossbow.use(level, player, InteractionHand.MAIN_HAND);
        if (!player.isUsingItem()) throw new IllegalStateException("Crossbow did not start loading: " + started.getResult());
        // A fully drawn crossbow, exactly as releaseUsingItem would report it.
        crossbow.releaseUsing(level, player, crossbow.getUseDuration() - CrossbowItem.getChargeDuration(crossbow) - 1);
        player.stopUsingItem();
        if (!CrossbowItem.isCharged(crossbow)) throw new IllegalStateException("Crossbow did not load a primal arrow");
        proof.put("primalLeftAfterLoad", count(level, true)); proof.put("vanillaLeftAfterLoad", count(level, false));
    }
    private void fire(ServerLevel level) throws Exception {
        // The pig as original code sees it, and whether damage on that mirror is forwarded to the host pig.
        var services = ModernContentBridge.active().services(); var target = level.getEntity(pig);
        services.items().inLegacy(() -> {
                var loader = services.loader(); Object world = services.worlds().bound(level);
                Object box = loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a", double.class, double.class, double.class, double.class, double.class, double.class)
                        .invoke(null, target.getX() - 1, target.getY() - 1, target.getZ() - 1, target.getX() + 1, target.getY() + 2, target.getZ() + 1);
                var mirrors = github.com.gengyoubo.mfcl.bridge.LegacyWorlds.entities(world, loader.loadClass("net.minecraft.entity.Entity"), box);
                var seen = new ArrayList<String>();
                for (Object mirror : mirrors) seen.add(mirror.getClass().getName() + " intercepted=" + github.com.gengyoubo.mfcl.bridge.LegacyEntityHooks.intercepts(mirror));
                proof.put("pigMirrors", seen);
                if (!seen.contains("net.minecraft.entity.passive.EntityPig intercepted=true")) throw new IllegalStateException("The pig mirror does not forward damage " + seen);
                return null;
        });
        int arrowsBefore = level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(64)).size();
        player.getItemInHand(InteractionHand.MAIN_HAND).use(level, player, InteractionHand.MAIN_HAND);
        proof.put("hostArrows", level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(64)).size() - arrowsBefore);
        sawHosted = hosted(level) > 0;
    }
    private int hosted(ServerLevel level) {
        int count = 0;
        for (var entity : level.getEntitiesOfClass(LegacyHostedEntity.class, player.getBoundingBox().inflate(64)))
            if (entity.legacyOrNull() != null && entity.legacyOrNull().getClass().getName().equals("thaumcraft.common.entities.projectile.EntityPrimalArrow")) count++;
        return count;
    }
    private int count(ServerLevel level, boolean primal) {
        int total = 0; var inventory = player.getInventory(); var primalItem = ModernContentBridge.active().items().get("Thaumcraft:PrimalArrow");
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (primal ? stack.is(primalItem) : stack.is(Items.ARROW)) total += stack.getCount();
        }
        return total;
    }
    private void verify(ServerLevel level, boolean slowed) {
        proof.put("hostedPrimalArrow", sawHosted); proof.put("pigSlowed", slowed);
        if (level.getEntity(pig) instanceof LivingEntity target) proof.put("pigHealth", target.getHealth());
        if (!proof.get("primalLeftAfterLoad").equals(3) || !proof.get("vanillaLeftAfterLoad").equals(4)) throw new IllegalStateException("Wrong ammo loaded " + proof);
        if (!proof.get("hostArrows").equals(0)) throw new IllegalStateException("The crossbow still created a host arrow " + proof);
        if (!sawHosted) throw new IllegalStateException("No original primal arrow was hosted " + proof);
        if (!slowed) throw new IllegalStateException("The water arrow never slowed the pig " + proof);
        proof.put("status", "PASS"); finish(level.getServer(), null);
    }
    private void finish(MinecraftServer server, Throwable failure) {
        done = true;
        // The probe world is shared with the bow probe, which fires along the same line.
        if (pig != null && server.overworld().getEntity(pig) != null) server.overworld().getEntity(pig).discard();
        try {
            var path = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/server-1.7.10-smoke-crossbow.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception ignored) { }
        if (failure == null) LogUtils.getLogger().info("MFCL_CROSSBOW_SMOKE_PASS: {}", proof);
        else LogUtils.getLogger().error("MFCL_CROSSBOW_SMOKE_FAIL: " + proof, failure);
        server.halt(false);
    }
}
