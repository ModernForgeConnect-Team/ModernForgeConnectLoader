package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Small operator diagnostic, independent of individual legacy mods. */
public final class ApiDebugCommand {
    private ApiDebugCommand() { }
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mfclapi").requires(source -> source.hasPermission(2)).executes(context -> {
            var runtime = MfclApi.runtime();
            if (runtime.isEmpty()) { context.getSource().sendFailure(Component.literal("MFCL API: legacy runtime is unavailable")); return 0; }
            var api = runtime.get();
            context.getSource().sendSuccess(() -> Component.literal("MFCL API v" + MfclApi.API_VERSION + ", source " + api.sourceVersion()
                    + ", mods " + api.loadedMods() + ", mappings " + api.mappings().supported()), false);
            context.getSource().sendSuccess(() -> Component.literal("Legacy registries: " + api.registryKeys(LegacyRuntime.Registry.ITEM).size()
                    + " items, " + api.registryKeys(LegacyRuntime.Registry.BLOCK).size() + " blocks"), false);
            var failures = api.diagnostics();
            for (var diagnostic : failures.subList(Math.max(0, failures.size() - 5), failures.size())) {
                String line = diagnostic.code() + " " + diagnostic.operation() + ": " + diagnostic.detail();
                context.getSource().sendSuccess(() -> Component.literal(line.substring(0, Math.min(512, line.length()))), false);
            }
            return 1;
        }));
    }
}
