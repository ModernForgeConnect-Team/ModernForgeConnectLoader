package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Native registry/rules and lossless enchantment NBT around the adapter's original numeric identities. */
public final class LegacyEnchantmentBridge {
    private static final String MAPPED="enchantments_mapped";
    private final ModernContentBridge content;
    private final LegacyServices services;
    private final Map<String,Enchantment> entries=new LinkedHashMap<>();
    private final Map<String,CompoundTag> tags=new LinkedHashMap<>(128,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,CompoundTag> entry){return size()>256;}};
    LegacyEnchantmentBridge(ModernContentBridge content){this.content=content;services=content.services();}
    public Map<String,Enchantment> entries(){return Collections.unmodifiableMap(entries);}
    void register(RegisterEvent event){event.register(Registries.ENCHANTMENT,registry->{
        for(var entry:services.enchantments().entries()){
            var enchantment=new Original(entry);registry.register(LegacyRegistryIds.id(entry.key()),enchantment);entries.put(entry.key(),enchantment);
        }
    });}
    private final class Original extends Enchantment {
        private final LegacyEnchantments.Entry entry;
        Original(LegacyEnchantments.Entry entry){
            super(rarity(entry.weight()),EnchantmentCategory.create("MFCL_"+entry.key().replace(':','_').replace('/','_'),item->applies(entry,new ItemStack(item),false)),EquipmentSlot.values());this.entry=entry;
        }
        @Override public int getMinLevel(){return entry.min();}
        @Override public int getMaxLevel(){return entry.max();}
        @Override public int getMinCost(int level){return entry.minCosts().get(Math.max(0,Math.min(level,entry.max())));}
        @Override public int getMaxCost(int level){return entry.maxCosts().get(Math.max(0,Math.min(level,entry.max())));}
        @Override public String getDescriptionId(){return entry.name();}
        @Override public boolean canEnchant(ItemStack stack){return applies(entry,stack,false);}
        @Override public boolean canApplyAtEnchantingTable(ItemStack stack){return applies(entry,stack,true);}
        @Override public boolean isAllowedOnBooks(){return entry.books();}
        @Override protected boolean checkCompatibility(Enchantment other){
            if(other==this)return false;
            try{return services.enchantments().compatible(entry,services.enchantments().id(BuiltInRegistries.ENCHANTMENT.getKey(other).toString()));}
            catch(Exception failure){throw new IllegalStateException("Cannot read original enchantment compatibility",failure);}
        }
    }
    private boolean applies(LegacyEnchantments.Entry entry,ItemStack stack,boolean table){
        try{return services.enchantments().applies(entry,content.gameplayStack(stack),table);}
        catch(Exception unsupported){return false;}
    }
    private static Enchantment.Rarity rarity(int weight){return weight>=10?Enchantment.Rarity.COMMON:weight>=5?Enchantment.Rarity.UNCOMMON:weight>=2?Enchantment.Rarity.RARE:Enchantment.Rarity.VERY_RARE;}
    private CompoundTag tag(LegacyClientData.Stack old)throws Exception {
        // Never hold the cache lock while entering the shared old runtime: callbacks enter in the opposite order.
        synchronized(tags){var cached=tags.get(old.tag());if(cached!=null)return cached.copy();}
        var parsed=LegacyVanillaStacks.originalTag(services,old);
        synchronized(tags){tags.put(old.tag(),parsed);}return parsed.copy();
    }
    /** A native item created from original code exposes registered enchantments to commands, anvils and tooltips. */
    void importOld(ItemStack stack,LegacyClientData.Stack old)throws Exception {
        if(!old.tag().contains("ench"))return;
        var levels=LegacyVanillaStacks.enchantments(services,tag(old).getList("ench",Tag.TAG_COMPOUND),true);
        if(!levels.isEmpty()){stack.getOrCreateTag().put("Enchantments",levels);stack.getOrCreateTagElement(ModernContentBridge.STACK_TAG).putBoolean(MAPPED,true);}
    }
    /** Native edits (including grindstone removal) replace mapped entries; unknown old IDs remain original data. */
    LegacyClientData.Stack exportNative(ItemStack stack,LegacyClientData.Stack old)throws Exception {
        migrate(stack);
        var root=stack.getTag();var stored=stack.getTagElement(ModernContentBridge.STACK_TAG);
        boolean authoritative=root!=null&&root.contains("Enchantments")||stored!=null&&stored.getBoolean(MAPPED);
        if(!authoritative)return old;
        var tag=tag(old);var before=tag.copy();var result=new ListTag();
        for(Tag value:tag.getList("ench",Tag.TAG_COMPOUND))if(services.enchantments().name(((CompoundTag)value).getShort("id"))==null)result.add(value.copy());
        if(root!=null)result.addAll(LegacyVanillaStacks.enchantments(services,root.getList("Enchantments",Tag.TAG_COMPOUND),false));
        if(result.isEmpty())tag.remove("ench");else tag.put("ench",result);
        if(tag.equals(before))return old;
        var bytes=new java.io.ByteArrayOutputStream();NbtIo.writeCompressed(tag,bytes);return services.items().withTag(old,bytes.toByteArray());
    }
    /** Upgrade saved numeric enchantments exactly once, so later native removal stays authoritative. */
    public void migrate(ItemStack stack)throws Exception {
        LegacyVanillaStacks.migrate(services,stack);
        var stored=stack.getTagElement(ModernContentBridge.STACK_TAG);
        if(stored==null||stored.getBoolean(MAPPED))return;
        var old=content.storedSnapshot(stack);if(old==null||!old.tag().contains("ench"))return;
        var levels=LegacyVanillaStacks.enchantments(services,tag(old).getList("ench",Tag.TAG_COMPOUND),true);
        var merged=new LinkedHashMap<String,Tag>();for(Tag value:levels)merged.put(((CompoundTag)value).getString("id"),value);
        for(Tag value:stack.getOrCreateTag().getList("Enchantments",Tag.TAG_COMPOUND))merged.put(((CompoundTag)value).getString("id"),value.copy());
        var result=new ListTag();merged.values().forEach(result::add);
        if(!result.isEmpty())stack.getOrCreateTag().put("Enchantments",result);
        stored.putBoolean(MAPPED,true);
    }
    /** Preserve native-only enchantments when original code changes an item; mapped entries follow original output. */
    void keepNative(ItemStack current,ItemStack updated){
        var root=current.getTag();if(root==null)return;
        var result=updated.getTag()==null?new ListTag():updated.getTag().getList("Enchantments",Tag.TAG_COMPOUND).copy();
        var existing=new HashSet<String>();for(Tag value:result)existing.add(((CompoundTag)value).getString("id"));
        for(Tag value:root.getList("Enchantments",Tag.TAG_COMPOUND)){
            String id=((CompoundTag)value).getString("id");
            if(services.enchantments().id(id)==null&&existing.add(id))result.add(value.copy());
        }
        if(!result.isEmpty())updated.getOrCreateTag().put("Enchantments",result);
    }
}
