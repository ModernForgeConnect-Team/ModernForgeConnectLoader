package github.com.gengyoubo.mfcl.internal.api;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Small operator diagnostic, independent of individual legacy mods.
 * {@code /mfclapi mappings|events|errors} list runtime findings; {@code /mfclapi dump} writes all of them to
 * {@code logs/mfcl/diagnostics-1.7.10.json}.
 */
public final class ApiDebugCommand {
    private static final int SHOWN = 8;
    private ApiDebugCommand() { }
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mfclapi").requires(source -> source.hasPermission(2)).executes(ApiDebugCommand::summary)
                .then(Commands.literal("mappings").executes(context -> findings(context, LegacyDiagnostics.Kind.MISSING_MAPPING)))
                .then(Commands.literal("errors").executes(context -> findings(context, LegacyDiagnostics.Kind.LEGACY_EXCEPTION)))
                .then(Commands.literal("events").executes(ApiDebugCommand::events))
                .then(Commands.literal("dump").executes(ApiDebugCommand::dump)));
    }

    private static int summary(CommandContext<CommandSourceStack> context) {
        var runtime = MfclApi.runtime();
        if (runtime.isEmpty()) { context.getSource().sendFailure(Component.literal("MFCL API: legacy runtime is unavailable")); return 0; }
        var api = runtime.get();
        send(context, "MFCL API v" + MfclApi.API_VERSION + ", source " + api.sourceVersion()
                + ", mods " + api.loadedMods() + ", mappings " + api.mappings().supported());
        send(context, "Legacy registries: " + api.registryKeys(LegacyRuntime.Registry.ITEM).size()
                + " items, " + api.registryKeys(LegacyRuntime.Registry.BLOCK).size() + " blocks");
        var failures = api.diagnostics();
        for (var diagnostic : failures.subList(Math.max(0, failures.size() - 5), failures.size()))
            send(context, diagnostic.code() + " " + diagnostic.operation() + ": " + diagnostic.detail());
        long undelivered = LegacyDiagnostics.subscriptions().stream().filter(subscription -> !subscription.delivered()).count();
        send(context, "Runtime findings: " + LegacyDiagnostics.findings(LegacyDiagnostics.Kind.MISSING_MAPPING).size() + " missing mappings, "
                + LegacyDiagnostics.findings(LegacyDiagnostics.Kind.LEGACY_EXCEPTION).size() + " legacy failure sources, "
                + undelivered + " undelivered event handlers (/mfclapi mappings|errors|events|dump)");
        return 1;
    }

    private static int findings(CommandContext<CommandSourceStack> context, LegacyDiagnostics.Kind kind) {
        var findings = LegacyDiagnostics.findings(kind);
        send(context, findings.size() + " " + kind.name().toLowerCase() + " finding(s)");
        for (var finding : findings.subList(0, Math.min(SHOWN, findings.size())))
            send(context, finding.count() + "x " + finding.key() + ": " + finding.detail());
        return findings.size();
    }

    private static int events(CommandContext<CommandSourceStack> context) {
        if (!LegacyDiagnostics.eventTracking()) send(context, "Old EventBus delivery tracking is not installed; delivery state is unknown");
        var undelivered = LegacyDiagnostics.subscriptions().stream().filter(subscription -> !subscription.delivered()).toList();
        send(context, undelivered.size() + " old event handler(s) have not received an event in this session");
        for (var subscription : undelivered.subList(0, Math.min(SHOWN, undelivered.size())))
            send(context, subscription.bus() + " " + subscription.event() + " -> " + subscription.listener() + " " + subscription.source());
        return undelivered.size();
    }

    private static int dump(CommandContext<CommandSourceStack> context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("eventTracking", LegacyDiagnostics.eventTracking());
        report.put("missingMappings", LegacyDiagnostics.findings(LegacyDiagnostics.Kind.MISSING_MAPPING));
        report.put("legacyFailures", LegacyDiagnostics.findings(LegacyDiagnostics.Kind.LEGACY_EXCEPTION));
        report.put("eventSubscriptions", LegacyDiagnostics.subscriptions());
        report.put("postedEvents", List.copyOf(new java.util.TreeSet<>(LegacyDiagnostics.postedEvents())));
        try {
            var path = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/diagnostics-1.7.10.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            send(context, "Wrote " + path);
            return 1;
        } catch (Exception failure) {
            context.getSource().sendFailure(Component.literal("Cannot write diagnostics: " + failure));
            return 0;
        }
    }

    private static void send(CommandContext<CommandSourceStack> context, String line) {
        String shown = line.substring(0, Math.min(512, line.length()));
        context.getSource().sendSuccess(() -> Component.literal(shown), false);
    }
}
