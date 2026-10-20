package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Generic adapter hooks for state normally maintained by original player events. Run inside inLegacy. */
public final class LegacyPlayerState {
    public interface Hooks {
        String id();
        default AutoCloseable enter(Object world) throws Exception { return () -> { }; }
        default void initialize(Object player) throws Exception { }
        default void save(Object player, Object originalNbt) throws Exception { }
        default void load(Object player, Object originalNbt) throws Exception { }
        /** Sends the adapter's player state to that player's client (login, after a server-side change). */
        default void synchronize(Object player) throws Exception { }
    }
    private final Map<String, Hooks> hooks = new LinkedHashMap<>();
    public synchronized AutoCloseable register(Hooks hook) {
        if (hooks.putIfAbsent(hook.id(), hook) != null) throw new IllegalArgumentException("Player state hook already registered: " + hook.id());
        return () -> { synchronized (this) { hooks.remove(hook.id(), hook); } };
    }
    private synchronized List<Hooks> snapshot() { return List.copyOf(hooks.values()); }
    public AutoCloseable enter(Object world) throws Exception {
        var scopes = new ArrayList<AutoCloseable>();
        try { for (var hook : snapshot()) scopes.add(Objects.requireNonNull(hook.enter(world))); }
        catch (Exception | Error failure) { close(scopes, failure); throw failure; }
        return () -> close(scopes, null);
    }
    private static void close(List<AutoCloseable> scopes, Throwable prior) throws Exception {
        Throwable failure = prior;
        for (int index = scopes.size() - 1; index >= 0; index--) {
            try { scopes.get(index).close(); }
            catch (Exception | Error error) { if (failure == null) failure = error; else if (error != failure) failure.addSuppressed(error); }
        }
        if (prior == null) {
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
        }
    }
    public void initialize(Object player) throws Exception { for (var hook : snapshot()) hook.initialize(player); }
    public void save(Object player, Object nbt) throws Exception { for (var hook : snapshot()) hook.save(player, nbt); }
    public void load(Object player, Object nbt) throws Exception { for (var hook : snapshot()) hook.load(player, nbt); }
    public void synchronize(Object player) throws Exception { for (var hook : snapshot()) hook.synchronize(player); }
}
