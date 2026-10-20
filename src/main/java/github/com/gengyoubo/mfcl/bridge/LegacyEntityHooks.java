package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Called from original Entity code rewritten by EntityHostTransformer. A mirror is only a snapshot of a host entity,
 * so damage, burning and potion effects old code applies to it are forwarded to the authoritative host entity.
 * Client mirrors and every non-mirror entity keep running their original implementation.
 */
public final class LegacyEntityHooks {
    // Weak identity keys: old Entity.equals compares entity ids, and mirrors are short-lived query snapshots.
    private static final Map<Object, Boolean> MIRRORS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<ClassLoader, Access> ACCESS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> FAILURES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private LegacyEntityHooks() {}
    private record Access(Class<?> entity, Field world, Method id, Class<?> source, Field type, Method fire, Method projectile, Method magic, Method explosion,
                          Method bypassArmor, Method direct, Method attacker, Method potion, Method duration, Method amplifier, Method ambient) {
        static Access of(ClassLoader loader) {
            try {
                var entity = loader.loadClass("net.minecraft.entity.Entity"); var source = loader.loadClass("net.minecraft.util.DamageSource");
                var effect = loader.loadClass("net.minecraft.potion.PotionEffect");
                return new Access(entity, entity.getField("field_70170_p"), entity.getMethod("func_110124_au"), source, source.getField("field_76373_n"),
                        source.getMethod("func_76347_k"), source.getMethod("func_76352_a"), source.getMethod("func_82725_o"), source.getMethod("func_94541_c"),
                        source.getMethod("func_76363_c"), source.getMethod("func_76364_f"), source.getMethod("func_76346_g"),
                        effect.getMethod("func_76456_a"), effect.getMethod("func_76459_b"), effect.getMethod("func_76458_c"), effect.getMethod("func_82720_e"));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Old Entity API is incomplete", failure); }
        }
        UUID idOf(Object value) throws ReflectiveOperationException { return value != null && entity.isInstance(value) ? (UUID) id.invoke(value) : null; }
    }
    static void mirrored(Object entity) { MIRRORS.put(entity, Boolean.TRUE); }
    private static Access access(Object entity) { return ACCESS.computeIfAbsent(entity.getClass().getClassLoader(), Access::of); }
    private static HostWorldAccess host(Object entity) throws ReflectiveOperationException {
        return LegacyWorlds.hostOrNull(access(entity).world.get(entity));
    }
    public static boolean intercepts(Object entity) {
        if (MIRRORS.isEmpty() || !MIRRORS.containsKey(entity)) return false;
        try { var host = host(entity); return host != null && !host.clientSide(); }
        catch (Throwable failure) { report("mirror lookup", failure); return false; }
    }
    public static boolean attack(Object entity, Object source, float amount) {
        try {
            var access = access(entity); var host = host(entity);
            if (host == null || source == null) return false;
            return host.damage(new HostWorldAccess.Damage(access.idOf(entity), (String) access.type.get(source), amount,
                    (boolean) access.fire.invoke(source), (boolean) access.projectile.invoke(source), (boolean) access.magic.invoke(source),
                    (boolean) access.explosion.invoke(source), (boolean) access.bypassArmor.invoke(source),
                    access.idOf(access.direct.invoke(source)), access.idOf(access.attacker.invoke(source))));
        } catch (Throwable failure) { report("attackEntityFrom", failure); return false; }
    }
    public static void ignite(Object entity, int seconds) {
        try { var host = host(entity); if (host != null) host.ignite(access(entity).idOf(entity), seconds); }
        catch (Throwable failure) { report("setFire", failure); }
    }
    public static void effect(Object entity, Object effect) {
        try {
            var access = access(entity); var host = host(entity);
            if (host == null || effect == null) return;
            host.addEffect(access.idOf(entity), (int) access.potion.invoke(effect), (int) access.duration.invoke(effect), (int) access.amplifier.invoke(effect), (boolean) access.ambient.invoke(effect));
        } catch (Throwable failure) { report("addPotionEffect", failure); }
    }
    // Old callers cannot handle a bridge exception; a forwarding failure must not abort their logic.
    private static void report(String operation, Throwable failure) {
        while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
        if (FAILURES.add(operation + failure)) LogUtils.getLogger().error("MFCL cannot forward old {} to its host entity", operation, failure);
    }
}
