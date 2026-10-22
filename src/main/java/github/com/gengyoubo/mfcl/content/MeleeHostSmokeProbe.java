package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.*;

/**
 * Headless acceptance for original melee weapons: the original attack modifier reaches the native attribute map, original
 * swords sweep and take sword enchantments, original tools take digger enchantments, and host enchantments both apply
 * and survive the original inventory/left-click/hit callbacks over two native attacks.
 */
final class MeleeHostSmokeProbe {
    /** kind: sword, pickaxe, axe or shovel; speed: the host counterpart's attack speed. */
    private record Weapon(String key, String kind, double speed) { boolean sword() { return kind.equals("sword"); } }
    private static final Weapon[] WEAPONS = {new Weapon("minecraft:iron_sword", "sword", 1.6), new Weapon("Thaumcraft:ItemSwordThaumium", "sword", 1.6),
            new Weapon("Thaumcraft:ItemSwordElemental", "sword", 1.6), new Weapon("Thaumcraft:ItemSwordVoid", "sword", 1.6),
            new Weapon("Thaumcraft:ItemPickThaumium", "pickaxe", 1.2), new Weapon("Thaumcraft:ItemAxeElemental", "axe", 1),
            new Weapon("Thaumcraft:ItemAxeThaumium", "axe", 1), new Weapon("Thaumcraft:ItemShovelThaumium", "shovel", 1)};
    private static final Map<String, Enchantment> PROBED = new LinkedHashMap<>();
    static {
        PROBED.put("sharpness", Enchantments.SHARPNESS); PROBED.put("sweeping", Enchantments.SWEEPING_EDGE); PROBED.put("looting", Enchantments.MOB_LOOTING);
        PROBED.put("efficiency", Enchantments.BLOCK_EFFICIENCY); PROBED.put("fortune", Enchantments.BLOCK_FORTUNE); PROBED.put("unbreaking", Enchantments.UNBREAKING);
    }
    private final ModernContentBridge bridge;
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private Map<String, Object> row;
    private net.minecraft.server.level.ServerPlayer player;
    private Pig target, bystander;
    private int index, step;
    private boolean done;
    private MeleeHostSmokeProbe(ModernContentBridge bridge) { this.bridge = bridge; }
    static void install(ModernContentBridge bridge) { MinecraftForge.EVENT_BUS.addListener(new MeleeHostSmokeProbe(bridge)::tick); }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        MinecraftServer server = event.getServer(); ServerLevel level = server.overworld();
        try {
            if (player == null) {
                var spawn = level.getSharedSpawnPos();
                player = FakePlayerFactory.getMinecraft(level);
                player.setPos(spawn.getX() + .5, level.getMaxBuildHeight() - 10, spawn.getZ() + .5);
                player.setOnGround(true);
            }
            var weapon = WEAPONS[index];
            switch (step++) {
                case 0 -> setup(level, weapon);
                case 1 -> { row.put("damageAfterEquip", damage()); player.getMainHandItem().inventoryTick(level, player, 0, true); }
                case 2 -> { row.put("damageAfterInventoryTick", damage()); hit("hit1"); }
                case 3 -> { equipment(); row.put("damageAfterReequip", damage()); hit("hit2"); }
                default -> { verify(weapon); target.discard(); bystander.discard(); step = 0;
                    if (++index == WEAPONS.length) { proof.put("failures", failures); proof.put("status", failures.isEmpty() ? "PASS" : "FAIL"); finish(server, null); } }
            }
        } catch (Throwable failure) { proof.put("status", "FAIL"); proof.put("failure", failure.toString()); finish(server, failure); }
    }

    private void setup(ServerLevel level, Weapon weapon) throws Exception {
        var stack = weapon.key().startsWith("minecraft:") ? new ItemStack(Items.IRON_SWORD) : bridge.stack(weapon.key(), 0, 1, "");
        if (stack.isEmpty()) throw new IllegalStateException(weapon.key() + " is not registered");
        row = new LinkedHashMap<>(); proof.put(weapon.key(), row);
        row.put("sweepAction", stack.canPerformAction(ToolActions.SWORD_SWEEP));
        row.put("enchantability", stack.getEnchantmentValue());
        var applicable = new ArrayList<String>();
        PROBED.forEach((name, enchantment) -> { if (enchantment.canEnchant(stack)) applicable.add(name); });
        row.put("applicable", applicable);
        // What the enchanting table would actually roll at level 30.
        var rolled = new ArrayList<String>();
        for (var instance : net.minecraft.world.item.enchantment.EnchantmentHelper.selectEnchantment(level.random, stack, 30, false))
            rolled.add(instance.enchantment.getDescriptionId() + " " + instance.level);
        row.put("tableRoll", rolled);
        stack.enchant(Enchantments.SHARPNESS, 5); stack.enchant(Enchantments.UNBREAKING, 3);
        if (weapon.sword()) stack.enchant(Enchantments.SWEEPING_EDGE, 3);
        var inventory = player.getInventory(); inventory.clearContent(); inventory.selected = 0; inventory.setItem(0, stack);
        equipment();
        row.put("attackSpeed", player.getAttributeValue(Attributes.ATTACK_SPEED));
        target = pig(level, 1, 0); bystander = pig(level, 1, .6);
        row.put("disablesShield", stack.canDisableShield(new ItemStack(Items.SHIELD), target, player));
        var anvil = player.blockPosition().offset(4, 0, 0);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        row.put("creativeBreaksBlocks", stack.getItem().canAttackBlock(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), level, anvil, player));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        row.put("rightClickLog", useOn(level, anvil, net.minecraft.world.level.block.Blocks.OAK_LOG));
        row.put("rightClickGrass", useOn(level, anvil.above(2), net.minecraft.world.level.block.Blocks.GRASS_BLOCK));
    }

    /** Native right-click on a block's top face; reports the block afterwards and the durability it cost. */
    private String useOn(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState()); level.setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var stack = player.getMainHandItem(); int before = stack.getDamageValue();
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, .5, 0), net.minecraft.core.Direction.UP, pos, false);
        var result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        var after = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
        stack.setDamageValue(before); level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        return result + " " + after;
    }

    private Pig pig(ServerLevel level, double dx, double dz) {
        var pig = EntityType.PIG.create(level);
        pig.moveTo(player.getX() + dx, player.getY(), player.getZ() + dz);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); pig.setHealth(200); pig.setNoAi(true); pig.setNoGravity(true); pig.setOnGround(true);
        level.addFreshEntity(pig); return pig;
    }

    /** FakePlayer never ticks, so run the native equipment-change pass a real player runs every tick. */
    private void equipment() throws Exception {
        var detect = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); detect.setAccessible(true); detect.invoke(player);
    }

    private double damage() { return player.getAttributeValue(Attributes.ATTACK_DAMAGE); }

    private void hit(String name) throws Exception {
        var ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker"); ticker.setAccessible(true); ticker.setInt(player, 100);
        for (var pig : List.of(target, bystander)) { pig.invulnerableTime = 0; pig.hurtTime = 0; pig.setHealth(200); }
        // Sweeping needs a standing player whose movement speed exceeds this tick's walk distance; a FakePlayer never sets it.
        player.walkDist = player.walkDistO; player.setSpeed(.1f);
        player.attack(target);
        var result = new LinkedHashMap<String, Object>();
        result.put("target", 200 - target.getHealth()); result.put("sweep", 200 - bystander.getHealth());
        var held = player.getMainHandItem();
        result.put("sharpnessAfter", held.getEnchantmentLevel(Enchantments.SHARPNESS)); result.put("durabilityUsed", held.getDamageValue());
        row.put(name, result);
    }

    @SuppressWarnings("unchecked")
    private void verify(Weapon weapon) {
        String key = weapon.key();
        double expected = (double) row.get("damageAfterEquip") + 3; // Sharpness V adds 3 at full charge
        var applicable = (List<String>) row.get("applicable");
        if ((int) row.get("enchantability") <= 0) failures.add(key + ": no enchantability");
        if (!applicable.contains("unbreaking")) failures.add(key + ": unbreaking not applicable");
        if (((List<?>) row.get("tableRoll")).isEmpty()) failures.add(key + ": enchanting table rolled nothing");
        if ((boolean) row.get("sweepAction") != weapon.sword()) failures.add(key + ": sweep action " + row.get("sweepAction"));
        if (weapon.sword() && !(applicable.contains("sweeping") && applicable.contains("sharpness") && applicable.contains("looting"))) failures.add(key + ": sword enchantments missing " + applicable);
        if (!weapon.sword() && (!applicable.contains("efficiency") || applicable.contains("sweeping"))) failures.add(key + ": tool enchantments wrong " + applicable);
        if (Math.abs((double) row.get("attackSpeed") - weapon.speed()) > 1e-6) failures.add(key + ": attack speed " + row.get("attackSpeed"));
        if ((boolean) row.get("disablesShield") != weapon.kind().equals("axe")) failures.add(key + ": shield disabling " + row.get("disablesShield"));
        if ((boolean) row.get("creativeBreaksBlocks") == weapon.sword()) failures.add(key + ": creative block breaking " + row.get("creativeBreaksBlocks"));
        if (((String) row.get("rightClickLog")).endsWith("stripped_oak_log") != weapon.kind().equals("axe")) failures.add(key + ": log right-click " + row.get("rightClickLog"));
        if (((String) row.get("rightClickGrass")).endsWith("dirt_path") != weapon.kind().equals("shovel")) failures.add(key + ": grass right-click " + row.get("rightClickGrass"));
        for (String name : List.of("hit1", "hit2")) {
            var hit = (Map<String, Object>) row.get(name);
            if (Math.abs((float) hit.get("target") - expected) > 1e-3) failures.add(key + ": " + name + " dealt " + hit.get("target") + ", expected " + expected);
            if ((int) hit.get("sharpnessAfter") != 5) failures.add(key + ": " + name + " lost host enchantments");
            if (weapon.sword() != ((float) hit.get("sweep") > 0)) failures.add(key + ": " + name + " sweep damage " + hit.get("sweep"));
        }
    }

    private void finish(MinecraftServer server, Throwable failure) {
        done = true;
        try {
            var path = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/server-1.7.10-smoke-melee.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception ignored) { }
        if (failure == null) LogUtils.getLogger().info("MFCL_MELEE_SMOKE_DONE: {}", proof);
        else LogUtils.getLogger().error("MFCL_MELEE_SMOKE_FAIL: " + proof, failure);
        server.halt(false);
    }
}
