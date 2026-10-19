package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import java.util.*;

public final class ApiRuntimeFactory {
    private ApiRuntimeFactory() { }
    public static LegacyApiRuntime create(LegacyServices services, Set<String> mods) {
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
        runtime.mappings().register(LegacyMappings.WORLD, net.minecraft.world.level.Level.class, host -> {
            Object old = services.worlds().findBound(host);
            return runtime.borrowed(old, () -> services.worlds().findBound(host) == old);
        }, old -> (net.minecraft.world.level.Level) services.worlds().host(runtime.raw(old)));
    }
}
