package github.com.gengyoubo.mfcl.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.MissingMappingsEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Readable modern names, with migration from MFCL's original hexadecimal names. */
public final class LegacyRegistryIds {
    private LegacyRegistryIds() {}
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    /** Keys whose readable name collides with another key's (1.7.10 names are case-sensitive, modern ones are not). */
    private static final Map<String,ResourceLocation> DISAMBIGUATED=new java.util.concurrent.ConcurrentHashMap<>();
    public static ResourceLocation id(String key) {
        var assigned=DISAMBIGUATED.get(key);
        if(assigned!=null)return assigned;
        return baseId(key);
    }
    private static ResourceLocation baseId(String key) {
        var parsed=ResourceLocation.tryParse(key);
        if(parsed!=null)return parsed;
        int colon=key.indexOf(':');
        String namespace=colon<0?"minecraft":key.substring(0,colon);
        String path=colon<0?key:key.substring(colon+1);
        return ResourceLocation.fromNamespaceAndPath(normalize(namespace,false),normalize(path,true));
    }
    private static String normalize(String value,boolean path) {
        if(path)value=value.replaceAll("([A-Z]+)([A-Z][a-z])","$1_$2").replaceAll("([a-z0-9])([A-Z])","$1_$2");
        var result=new StringBuilder();
        value.toLowerCase(Locale.ROOT).codePoints().forEach(c->{
            if(c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_'||c=='-'||c=='.'||path&&c=='/')result.appendCodePoint(c);
            else result.append('_').append(Integer.toHexString(c)).append('_');
        });
        return result.toString();
    }
    /** Recipes live in their output's namespace so recipe viewers attribute them to the original mod, not MFCL. */
    public static String recipeNamespace(github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack output) {
        return output==null?"mfcl":id(output.key()).getNamespace();
    }
    static ResourceLocation previousId(String key) {
        var parsed=ResourceLocation.tryParse(key);
        return parsed!=null?parsed:ResourceLocation.fromNamespaceAndPath("mfcl","legacy/"+HexFormat.of().formatHex(key.getBytes(StandardCharsets.UTF_8)));
    }
    /**
     * Gives every key a distinct ID. Keys that only differ by case or separators (TFC+ registers both
     * {@code Branch2_X_Z} and {@code Branch2_X_z}) would share one readable name, so in sorted key order the first
     * keeps it and later ones get {@code _2}, {@code _3}, ... The order depends only on the keys, so the same pack
     * gets the same IDs every launch, and blocks and their item forms (same keys) get the same suffix.
     */
    static synchronized void validate(Collection<String> keys) {
        Map<ResourceLocation,String> names=new HashMap<>();
        for(String key:new TreeSet<>(keys)) {
            var id=id(key);
            String previous=names.putIfAbsent(id,key);
            if(previous==null)continue;
            int suffix=2;
            ResourceLocation candidate;
            do candidate=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),id.getPath()+"_"+suffix++);
            while(names.containsKey(candidate)||keys.contains(candidate.toString()));
            DISAMBIGUATED.put(key,candidate);
            names.put(candidate,key);
            LOGGER.warn("MFCL: legacy names {} and {} both map to {}; using {} for {}",previous,key,id,candidate,key);
        }
    }
    static <T> void remap(List<MissingMappingsEvent.Mapping<T>> missing,Map<String,T> entries) {
        Map<ResourceLocation,T> previous=new HashMap<>();
        entries.forEach((key,value)->{if(!previousId(key).equals(id(key)))previous.put(previousId(key),value);});
        for(var mapping:missing) {
            T target=previous.get(mapping.getKey());
            if(target!=null)mapping.remap(target);
            else if(mapping.getKey().getNamespace().equals("mfcl")||entries.keySet().stream().anyMatch(key->id(key).getNamespace().equals(mapping.getKey().getNamespace())))
                github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.missingMapping("saved "+mapping.getKey(),"saved world references an entry that is no longer loaded");
        }
    }
}
