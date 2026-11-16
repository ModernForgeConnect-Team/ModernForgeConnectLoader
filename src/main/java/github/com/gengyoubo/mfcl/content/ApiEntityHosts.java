package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.internal.api.LegacyApiRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-side registration and lifetime of addon-owned entity hosts. */
public final class ApiEntityHosts implements LegacyEntityHosts {
    private final ModernContentBridge content;
    private final LegacyApiRuntime runtime;
    private final Map<String,Factory> factories = new HashMap<>();
    private final Map<Entity,Object> originals = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    public ApiEntityHosts(ModernContentBridge content,LegacyApiRuntime runtime) { this.content=content;this.runtime=runtime; }

    @Override public AutoCloseable register(String legacyClass,Factory factory) {
        if(!runtime.isOpen())throw new IllegalStateException("Legacy runtime is closed");
        Objects.requireNonNull(legacyClass);Objects.requireNonNull(factory);
        if(legacyClass.isBlank())throw new IllegalArgumentException("Empty legacy entity class");
        synchronized(factories){if(factories.putIfAbsent(legacyClass,factory)!=null)throw new IllegalStateException("Entity host already registered: "+legacyClass);}
        return ()->{synchronized(factories){factories.remove(legacyClass,factory);}};
    }
    private ServerLevel level(Object value) {
        if(!(value instanceof Entity entity)||!(entity.level() instanceof ServerLevel level)||!level.getServer().isSameThread())
            throw new IllegalArgumentException("Expected an entity on its owning server thread");
        return level;
    }
    public Entity create(ServerLevel level,Object original) throws Exception {
        Factory factory;synchronized(factories){factory=factories.get(original.getClass().getName());}
        if(factory==null)return null;
        var alive=new AtomicBoolean(true);Object result;
        try{result=factory.create(level,runtime.borrowed(original,alive::get));}finally{alive.set(false);}
        if(result==null)return null;
        if(!(result instanceof Entity host)||host.level()!=level||host.isAddedToWorld()||host.isRemoved())
            throw new IllegalArgumentException("Entity host factory must return an unspawned entity in the requested world");
        var state=content.services().entityHosting().state(original);
        host.moveTo(state.x(),state.y(),state.z(),state.yaw(),state.pitch());
        host.setDeltaMovement(state.motionX(),state.motionY(),state.motionZ());
        bind(host,level,original);return host;
    }
    private void bind(Entity host,ServerLevel level,Object original) throws Exception {
        var hosting=content.services().entityHosting();
        hosting.adopt(original,content.services().worlds().bound(level),host.getUUID(),host.getId());
        hosting.nativeMirror(original);originals.put(host,original);
    }
    public Object raw(Entity host) { return host==null?null:originals.get(host); }
    @Override public Optional<LegacyObject> original(Object value) {
        level(value);Entity host=(Entity)value;Object original=originals.get(host);
        return Optional.ofNullable(original==null?null:runtime.borrowed(original,()->runtime.isOpen()&&!host.isRemoved()&&originals.get(host)==original));
    }
    @Override public byte[] save(Object value) {
        level(value);Object original=originals.get((Entity)value);
        if(original==null)throw new IllegalStateException("Host has no bound legacy entity");
        return runtime.call("ENTITY_HOST_SAVE",original.getClass().getName(),()->content.services().entityHosting().save(original));
    }
    @Override public void restore(Object value,String legacyClass,byte[] nbt) {
        ServerLevel level=level(value);Entity host=(Entity)value;Objects.requireNonNull(legacyClass);Objects.requireNonNull(nbt);
        if(originals.containsKey(host))throw new IllegalStateException("Host already has a legacy entity");
        runtime.call("ENTITY_HOST_RESTORE",legacyClass,()->{
            Object original=content.services().entityHosting().restore(content.services().worlds().bound(level),legacyClass,nbt.clone());
            bind(host,level,original);sync(host);return null;
        });
    }
    @Override public void sync(Object value) {
        level(value);Entity host=(Entity)value;Object original=originals.get(host);
        if(original==null)throw new IllegalStateException("Host has no bound legacy entity");
        runtime.call("ENTITY_HOST_SYNC",original.getClass().getName(),()->{
            var hosting=content.services().entityHosting();var movement=host.getDeltaMovement();
            if(host instanceof LivingEntity living&&content.services().loader().loadClass("net.minecraft.entity.EntityLivingBase").isInstance(original))
                hosting.nativeLivingState(original,host.getX(),host.getY(),host.getZ(),movement.x,movement.y,movement.z,host.getYRot(),host.getXRot(),living.getHealth());
            else hosting.nativeState(original,host.getX(),host.getY(),host.getZ(),movement.x,movement.y,movement.z,host.getYRot(),host.getXRot());
            return null;
        });
    }
}
