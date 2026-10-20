package github.com.gengyoubo.mfcl.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.MissingMappingsEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Readable modern names, with migration from MFCL's original hexadecimal names. */
public final class LegacyRegistryIds {
    private LegacyRegistryIds() {}
    public static ResourceLocation id(String key) {
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
    static void validate(Collection<String> keys) {
        Map<ResourceLocation,String> names=new HashMap<>();
        for(String key:keys) {
            String previous=names.putIfAbsent(id(key),key);
            if(previous!=null)throw new IllegalArgumentException("Legacy registry ID collision: "+previous+" and "+key+" -> "+id(key));
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
