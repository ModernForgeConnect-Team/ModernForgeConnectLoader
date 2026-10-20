package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.brigadier.context.CommandContext;
import github.com.gengyoubo.mfcl.bridge.LegacyObjects;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import java.util.*;

/** {@code /debug thaumcraft resetresearch [player]}: original TC4 research reset, run against the bridged old player. */
final class ThaumcraftResearchCommand {
    private ThaumcraftResearchCommand() {}
    static void register(RegisterCommandsEvent event) {
        // Merges into vanilla /debug; its root keeps the vanilla permission requirement.
        event.getDispatcher().register(Commands.literal("debug").then(Commands.literal("thaumcraft").requires(source -> source.hasPermission(2))
                .then(Commands.literal("resetresearch").executes(context -> reset(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> reset(context, EntityArgument.getPlayer(context, "player")))))));
    }
    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        var content = ModernContentBridge.active();
        if (content == null) { context.getSource().sendFailure(Component.literal("MFCL legacy runtime is unavailable")); return 0; }
        var loader = content.services().loader();
        try {
            int restored = content.withApiPlayer(player, actor -> {
                try { return reset(loader, actor); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            context.getSource().sendSuccess(() -> Component.literal("Reset Thaumcraft research of " + player.getName().getString() + " (" + restored + " initial entries)"), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Thaumcraft research reset failed: " + failure));
            return 0;
        }
    }
    /** CommandThaumcraft.resetResearch, without its chat lines; withApiPlayer persists and resynchronizes afterwards. */
    private static int reset(ClassLoader loader, Object actor) throws Exception {
        Object proxy = ThaumcraftReflection.proxy(loader);
        Object knowledge = proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy);
        String name = LegacyObjects.playerName(actor);
        ((Map<?, ?>) knowledge.getClass().getField("researchCompleted").get(knowledge)).remove(name);
        Object manager = proxy.getClass().getMethod("getResearchManager").invoke(proxy);
        var complete = manager.getClass().getMethod("completeResearch", loader.loadClass("net.minecraft.entity.player.EntityPlayer"), String.class);
        var categories = (Map<?, ?>) loader.loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
        int restored = 0;
        for (Object category : categories.values()) for (Object research : ((Map<?, ?>) category.getClass().getField("research").get(category)).values())
            if ((boolean) research.getClass().getMethod("isAutoUnlock").invoke(research)) { complete.invoke(manager, actor, research.getClass().getField("key").get(research)); restored++; }
        // PacketSyncResearch only adds on the client, so clear its stale knowledge first, as TC does on login.
        Object wrapper = ThaumcraftReflection.packetHandler(loader);
        var send = wrapper.getClass().getMethod("sendTo", loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage"), loader.loadClass("net.minecraft.entity.player.EntityPlayerMP"));
        String packets = "thaumcraft.common.lib.network.playerdata.";
        send.invoke(wrapper, loader.loadClass(packets + "PacketSyncWipe").getConstructor().newInstance(), actor);
        var warp = loader.loadClass(packets + "PacketSyncWarp").getConstructor(loader.loadClass("net.minecraft.entity.player.EntityPlayer"), byte.class);
        for (byte type = 0; type < 3; type++) send.invoke(wrapper, warp.newInstance(actor, type), actor);
        return restored;
    }
}
