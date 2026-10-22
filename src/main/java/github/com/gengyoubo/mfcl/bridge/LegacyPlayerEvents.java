package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/**
 * Old Forge events built for a player actor and posted on the old EVENT_BUS, for host events forwarded through the
 * developer API. Callers hold the runtime scope and the actor's player-state scope; values cross as plain data.
 */
public final class LegacyPlayerEvents {
    /** {@code value} is the old event's mutable number (hurt amount, fall distance, break speed) after posting. */
    public record Result(boolean canceled, float value) {}
    /** A host damage source reduced to old DamageSource data; entity ids are host UUIDs, null when absent. */
    public record Damage(String type, boolean fire, boolean projectile, boolean magic, boolean explosion, boolean bypassArmor,
                         boolean absolute, boolean creative, UUID direct, UUID attacker) {}
    private final LegacyServices services;
    private final ClassLoader loader;
    private final Class<?> event, living, player, block, stack, source;
    private final Method post;
    private final Map<String, Object> staticSources = new HashMap<>();
    public LegacyPlayerEvents(LegacyServices services) throws Exception {
        this.services = services; this.loader = services.loader();
        event = loader.loadClass("cpw.mods.fml.common.eventhandler.Event");
        living = loader.loadClass("net.minecraft.entity.EntityLivingBase"); player = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        block = loader.loadClass("net.minecraft.block.Block"); stack = loader.loadClass("net.minecraft.item.ItemStack");
        source = loader.loadClass("net.minecraft.util.DamageSource");
        post = loader.loadClass("cpw.mods.fml.common.eventhandler.EventBus").getMethod("post", event);
        for (Field field : source.getFields()) if (Modifier.isStatic(field.getModifiers()) && field.getType() == source) {
            Object value = field.get(null);
            if (value != null) staticSources.putIfAbsent((String) source.getField("field_76373_n").get(value), value);
        }
    }
    /** Posts any old event object; true when it was canceled. */
    public boolean post(Object oldEvent) throws Exception {
        Object bus = loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
        return (boolean) post.invoke(bus, oldEvent);
    }
    public Result hurt(Object world, Object actor, Damage damage, float amount) throws Exception {
        var type = loader.loadClass("net.minecraftforge.event.entity.living.LivingHurtEvent");
        Object value = type.getConstructor(living, source, float.class).newInstance(actor, source(world, damage), amount);
        boolean canceled = post(value);
        return new Result(canceled, type.getField("ammount").getFloat(value));
    }
    public boolean update(Object actor) throws Exception {
        return post(loader.loadClass("net.minecraftforge.event.entity.living.LivingEvent$LivingUpdateEvent").getConstructor(living).newInstance(actor));
    }
    public Result fall(Object actor, float distance) throws Exception {
        var type = loader.loadClass("net.minecraftforge.event.entity.living.LivingFallEvent");
        Object value = type.getConstructor(living, float.class).newInstance(actor, distance);
        boolean canceled = post(value);
        return new Result(canceled, type.getField("distance").getFloat(value));
    }
    public Result breakSpeed(Object actor, String legacyBlock, int metadata, float original, int x, int y, int z) throws Exception {
        Object registry = block.getField("field_149771_c").get(null);
        Object target = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, legacyBlock);
        var type = loader.loadClass("net.minecraftforge.event.entity.player.PlayerEvent$BreakSpeed");
        Object value = type.getConstructor(player, block, int.class, float.class, int.class, int.class, int.class).newInstance(actor, target, metadata, original, x, y, z);
        boolean canceled = post(value);
        return new Result(canceled, type.getField("newSpeed").getFloat(value));
    }
    /** Lines old subscribers append to {@code initial} for this stack. */
    public List<String> tooltip(LegacyClientData.Stack value, Object actor, List<String> initial, boolean advanced) throws Exception {
        var lines = new ArrayList<String>(initial);
        Object oldEvent = loader.loadClass("net.minecraftforge.event.entity.player.ItemTooltipEvent").getConstructor(stack, player, List.class, boolean.class)
                .newInstance(services.items().materialize(value), actor, lines, advanced);
        post(oldEvent);
        return lines.size() <= initial.size() ? List.of() : List.copyOf(lines.subList(initial.size(), lines.size()));
    }
    private Object source(Object world, Damage damage) throws Exception {
        Object direct = damage.direct() == null ? null : LegacyWorlds.entity(world, damage.direct());
        Object attacker = damage.attacker() == null ? null : LegacyWorlds.entity(world, damage.attacker());
        if (direct == null && attacker == null) {
            Object known = staticSources.get(damage.type());
            if (known != null) return known;
        }
        Object result;
        if (direct == null && attacker == null) {
            var constructor = source.getDeclaredConstructor(String.class); constructor.setAccessible(true);
            result = constructor.newInstance(damage.type());
        } else if (attacker == null || attacker == direct || direct == null) {
            result = loader.loadClass("net.minecraft.util.EntityDamageSource").getConstructor(String.class, loader.loadClass("net.minecraft.entity.Entity"))
                    .newInstance(damage.type(), direct == null ? attacker : direct);
        } else {
            var entity = loader.loadClass("net.minecraft.entity.Entity");
            result = loader.loadClass("net.minecraft.util.EntityDamageSourceIndirect").getConstructor(String.class, entity, entity).newInstance(damage.type(), direct, attacker);
        }
        if (damage.fire()) flag(result, "func_76361_j");
        if (damage.projectile()) flag(result, "func_76349_b");
        if (damage.magic()) flag(result, "func_82726_p");
        if (damage.explosion()) flag(result, "func_94540_d");
        if (damage.bypassArmor()) flag(result, "func_76348_h");
        if (damage.absolute()) flag(result, "func_151518_m");
        if (damage.creative()) flag(result, "func_76359_i");
        return result;
    }
    private void flag(Object damageSource, String setter) throws Exception {
        var method = source.getDeclaredMethod(setter); method.setAccessible(true); method.invoke(damageSource);
    }
}
