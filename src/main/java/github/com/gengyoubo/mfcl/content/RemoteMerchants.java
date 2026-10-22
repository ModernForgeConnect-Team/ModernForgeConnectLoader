package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import java.util.*;

/**
 * Trading with host villagers from a device (XU's trading post): the server lists the adult villagers in range, the
 * client picks one, and the villager's own native trading screen opens. Every pick is checked again on the server.
 */
public final class RemoteMerchants {
    private record Session(BlockPos origin, int range, Set<Integer> merchants) {}
    private static final Map<UUID,Session> SESSIONS = Collections.synchronizedMap(new HashMap<>());
    private static final double REACH = 6;
    private static final java.lang.reflect.Method MOB_INTERACT = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
            net.minecraft.world.entity.Mob.class, "m_6071_", net.minecraft.world.entity.player.Player.class, InteractionHand.class);
    private RemoteMerchants() {}
    private static List<AbstractVillager> inRange(ServerPlayer player, BlockPos origin, int range) {
        var level = player.serverLevel();
        var box = new AABB(origin.getX() + .5 - range, level.getMinBuildHeight(), origin.getZ() + .5 - range, origin.getX() + .5 + range, level.getMaxBuildHeight(), origin.getZ() + .5 + range);
        return level.getEntitiesOfClass(AbstractVillager.class, box, villager -> villager.isAlive() && !villager.isBaby());
    }
    /** Sends the selection list; returns false when there is no villager, after telling the player like the original. */
    public static boolean offer(ServerPlayer player, BlockPos origin, int range) {
        var merchants = inRange(player, origin, range);
        if (merchants.isEmpty()) { player.displayClientMessage(Component.translatable("mfcl.trading_post.none"), true); return false; }
        merchants.sort(Comparator.comparingDouble(villager -> villager.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5)));
        var entries = new ArrayList<ScanSyncChannel.MerchantEntry>();
        for (var villager : merchants) {
            var results = new ArrayList<ItemStack>();
            for (var offer : villager.getOffers()) if (!offer.isOutOfStock()) results.add(offer.getResult().copy());
            entries.add(new ScanSyncChannel.MerchantEntry(villager.getId(), villager.getDisplayName(), results));
        }
        SESSIONS.put(player.getUUID(), new Session(origin.immutable(), range, entries.stream().map(ScanSyncChannel.MerchantEntry::entityId).collect(java.util.stream.Collectors.toSet())));
        ScanSyncChannel.sendMerchants(player, new ScanSyncChannel.MerchantList(origin.immutable(), entries));
        return true;
    }
    static void pick(ServerPlayer player, ScanSyncChannel.MerchantPick pick) {
        var session = SESSIONS.remove(player.getUUID());
        if (session == null || !session.origin().equals(pick.origin()) || !session.merchants().contains(pick.entityId())) return;
        var origin = session.origin();
        if (player.distanceToSqr(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5) > REACH * REACH) return;
        if (!(player.serverLevel().getEntity(pick.entityId()) instanceof AbstractVillager villager) || !villager.isAlive()) {
            player.displayClientMessage(Component.translatable("mfcl.trading_post.gone"), true); return;
        }
        if (Math.abs(villager.getX() - origin.getX()) >= session.range() || Math.abs(villager.getZ() - origin.getZ()) >= session.range()) {
            player.displayClientMessage(Component.translatable("mfcl.trading_post.out_of_range"), true); return;
        }
        if (villager.isTrading()) { player.displayClientMessage(Component.translatable("mfcl.trading_post.busy"), true); return; }
        // The villager's own interaction: reputation prices, the "talked to villager" statistic and its trading screen.
        // Mob.interact would first apply a held name tag or lead to the villager, so skip to mobInteract.
        try { MOB_INTERACT.invoke(villager, player, InteractionHand.MAIN_HAND); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot open villager trading", failure); }
    }
}
