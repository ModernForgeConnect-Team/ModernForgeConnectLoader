package github.com.gengyoubo.mfcl.bridge;

import net.minecraft.locale.Language;

/** Host-language lookups for original text that the old mod hard-codes instead of translating. */
public final class LegacyText {
    private LegacyText(){}
    /** The original literal stays the fallback; its leading/trailing line breaks are layout and are kept around the translation. */
    public static String translate(String key,String original){
        String text;
        try{
            var language=Language.getInstance();
            if(!language.has(key))return original;
            text=language.getOrDefault(key);
        }catch(Throwable unavailable){return original;}
        int start=0,end=original.length();
        while(start<end&&Character.isWhitespace(original.charAt(start)))start++;
        while(end>start&&Character.isWhitespace(original.charAt(end-1)))end--;
        return original.substring(0,start)+text+original.substring(end);
    }
    /** Translation chosen by an index the original computes at runtime, such as a block's metadata. */
    public static String indexed(String original,String prefix,int index){return translate(prefix+index,original);}
}
