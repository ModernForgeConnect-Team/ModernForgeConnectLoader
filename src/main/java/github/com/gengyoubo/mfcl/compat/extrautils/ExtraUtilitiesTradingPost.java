package github.com.gengyoubo.mfcl.compat.extrautils;

import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import github.com.gengyoubo.mfcl.content.RemoteMerchants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * XU's trading post reaches host villagers: their trades are host merchant offers, which the original IMerchant view
 * cannot represent, so the post opens the native villager trading screen through {@link RemoteMerchants} instead.
 * The original range (TileEntityTradingPost.maxRange) and 6-block reach still apply.
 */
final class ExtraUtilitiesTradingPost {
    private static final ResourceLocation BLOCK = ResourceLocation.fromNamespaceAndPath("extrautilities", "trading_post");
    private final int range;
    private ExtraUtilitiesTradingPost(int range) { this.range = range; }
    static void install(LegacyServices services) throws Exception {
        var post = new ExtraUtilitiesTradingPost(services.loader().loadClass("com.rwtema.extrautils.tileentity.TileEntityTradingPost").getField("maxRange").getInt(null));
        MinecraftForge.EVENT_BUS.addListener(post::use);
    }
    private void use(PlayerInteractEvent.RightClickBlock event) {
        // The client keeps its normal use prediction, which sends the use packet that reaches this server handler.
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!BLOCK.equals(BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()))) return;
        RemoteMerchants.offer(player, event.getPos(), range);
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
