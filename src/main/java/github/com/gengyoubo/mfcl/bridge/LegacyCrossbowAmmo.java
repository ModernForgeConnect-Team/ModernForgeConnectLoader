package github.com.gengyoubo.mfcl.bridge;

import java.util.*;
import java.util.function.Consumer;

/** Original arrow creation for modern crossbows; adapters supply the item's projectile semantics. */
public final class LegacyCrossbowAmmo {
    @FunctionalInterface public interface Factory {
        Object create(Object world,Object shooter,LegacyClientData.Stack ammunition) throws Exception;
    }
    private final Map<String,Factory> factories=new LinkedHashMap<>();
    private final Consumer<String> arrowTag;
    LegacyCrossbowAmmo(Consumer<String> arrowTag){this.arrowTag=arrowTag;}
    public synchronized void register(String legacyItem,Factory factory) {
        Objects.requireNonNull(legacyItem);Objects.requireNonNull(factory);
        if(factories.putIfAbsent(legacyItem,factory)!=null)throw new IllegalArgumentException("Crossbow ammunition already registered: "+legacyItem);
        arrowTag.accept(legacyItem);
    }
    public synchronized Optional<Factory> factory(String legacyItem){return Optional.ofNullable(factories.get(legacyItem));}
}
