package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import java.util.*;

public final class ApiRuntimeFactory {
    private ApiRuntimeFactory() { }
    public static LegacyApiRuntime create(LegacyServices services, Set<String> mods) {
        var runtime = createRuntime(services, mods);
        runtime.installLanguages(new LegacyLanguages() {
            @Override public void supplement(String language, Map<String, String> entries) {
                runtime.call("LANGUAGE_SUPPLEMENT", language, () -> { services.items().supplement(language, entries); return null; });
            }
            @Override public void override(String language, Map<String, String> entries) {
                runtime.call("LANGUAGE_OVERRIDE", language, () -> { services.items().override(language, entries); return null; });
            }
            @Override public Set<String> missingKeys(String language) {
                return runtime.call("LANGUAGE_MISSING_KEYS", language, () -> services.items().missingKeys(language));
            }
        });
        return runtime;
    }
    private static LegacyApiRuntime createRuntime(LegacyServices services, Set<String> mods) {
        return new LegacyApiRuntime(services.loader(), services.items()::inLegacy, new LegacyApiRuntime.Values() {
            private Object registry(LegacyRuntime.Registry kind) throws Exception {
                String type = kind == LegacyRuntime.Registry.ITEM ? "net.minecraft.item.Item" : "net.minecraft.block.Block";
                String field = kind == LegacyRuntime.Registry.ITEM ? "field_150901_e" : "field_149771_c";
                return services.loader().loadClass(type).getField(field).get(null);
            }
            @Override public Set<String> keys(LegacyRuntime.Registry kind) throws Exception {
                Object registry = registry(kind);
                var keys = (Set<?>) registry.getClass().getMethod("func_148742_b").invoke(registry);
                var result = new TreeSet<String>(); keys.forEach(key -> result.add(key.toString())); return result;
            }
            @Override public Object find(LegacyRuntime.Registry kind, String key) throws Exception {
                // Old defaulted block registries return air for unknown keys. Never expose that as a successful lookup.
                if (!keys(kind).contains(key)) return null;
                Object registry = registry(kind);
                return registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, key);
            }
            @Override public Object stack(LegacyStack stack) throws Exception {
                return services.items().materialize(new LegacyClientData.Stack(stack.key(), stack.metadata(), stack.count(), "", stack.snbt()));
            }
            @Override public LegacyStack snapshot(Object stack) throws Exception {
                var value = services.items().snapshot(stack);
                return new LegacyStack(value.key(), value.metadata(), value.count(), value.tag());
            }
        }, mods);
    }
    public static void installHostMappings(LegacyApiRuntime runtime, ModernContentBridge content) {
        var services = content.services();
        var representations = content.hostRepresentations();
        runtime.installHostRepresentations(new LegacyHostRepresentations() {
            public LegacyStack item(String id) { return runtime.call("HOST_REPRESENTATION", id, () -> representations.item(id)); }
            public LegacyStack block(String id) { return runtime.call("HOST_REPRESENTATION", id, () -> representations.block(id)); }
            public String entity(String id) { return runtime.call("HOST_REPRESENTATION", id, () -> representations.entity(id)); }
        });
        runtime.installHostBlocks((key, metadata) -> {
            if (metadata < 0 || metadata > 15) throw new IllegalArgumentException("Legacy block metadata outside 0..15");
            try { return content.modernBlock(new github.com.gengyoubo.mfcl.bridge.HostWorldAccess.Block(key, metadata)); }
            catch (UnsupportedOperationException missing) { return null; }
        });
        runtime.installPlayerAccess(new LegacyApiRuntime.PlayerAccess() {
            @Override public <T> T run(Object host, java.util.function.Function<LegacyObject, T> operation) {
                if (!(host instanceof net.minecraft.server.level.ServerPlayer player) || !player.server.isSameThread())
                    throw new IllegalArgumentException("withPlayer requires a ServerPlayer on its owning server thread");
                var alive = new java.util.concurrent.atomic.AtomicBoolean(true);
                try {
                    return content.withApiPlayer(player, old -> operation.apply(runtime.borrowed(old, alive::get)));
                } finally { alive.set(false); }
            }
        });
        runtime.mappings().register(LegacyMappings.ITEM_STACK, net.minecraft.world.item.ItemStack.class, host ->
            runtime.call("MAPPING_IMPORT", LegacyMappings.ITEM_STACK, () -> {
                if (host.isEmpty()) return null;
                var value = content.gameplayStack(host.copy());
                return runtime.reference(services.items().materialize(value));
            }), old -> runtime.call("MAPPING_EXPORT", LegacyMappings.ITEM_STACK,
                () -> content.gameplayStack(services.items().snapshot(runtime.raw(old))).copy()));
        runtime.installMerge(LegacyMappings.ITEM_STACK, (existing, old) -> {
            try { return content.applyTo((net.minecraft.world.item.ItemStack) existing, old == null ? null : services.items().snapshot(runtime.raw(old))); }
            catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException("Cannot snapshot legacy stack", failure); }
        });
        var players = content.players();
        players.diagnostics(runtime::note);
        runtime.installEquipment(players);
        runtime.installEvents(players);
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            runtime.installClientPlayerAccess(new LegacyApiRuntime.PlayerAccess() {
                @Override public <T> T run(Object ignored, java.util.function.Function<LegacyObject, T> operation) {
                    var alive = new java.util.concurrent.atomic.AtomicBoolean(true);
                    try { return content.withApiClientPlayer(old -> operation.apply(runtime.borrowed(old, alive::get))); }
                    finally { alive.set(false); }
                }
            });
            runtime.addFeatures(LegacyFeatures.CLIENT_PLAYER);
        }
        runtime.addFeatures(LegacyFeatures.HOST_REPRESENTATIONS, LegacyFeatures.HOST_BLOCKS, LegacyFeatures.PLAYER, LegacyFeatures.PLAYER_IDENTITY,
                LegacyFeatures.PLAYER_SYNC, LegacyFeatures.EQUIPMENT, LegacyFeatures.EQUIPMENT_TICK, LegacyFeatures.EQUIPMENT_WRITE_BACK,
                LegacyFeatures.EVENTS, LegacyFeatures.MAPPINGS_APPLY, LegacyFeatures.REDIRECTS);
        if (players.baubles()) runtime.addFeatures(LegacyFeatures.EQUIPMENT_BAUBLES);
        runtime.mappings().register(LegacyMappings.WORLD, net.minecraft.world.level.Level.class, host -> {
            Object old = services.worlds().findBound(host);
            return runtime.borrowed(old, () -> services.worlds().findBound(host) == old);
        }, old -> (net.minecraft.world.level.Level) services.worlds().host(runtime.raw(old)));
    }
}
