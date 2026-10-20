package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffect;
import java.util.*;

/** Explicit old vanilla loot NBT conversion. The source tag also preserves old-only enchantment data for round trips. */
final class LegacyVanillaStacks {
    private static final String SOURCE="mfcl_legacy_vanilla";
    private static final Map<Integer,String> ENCHANTMENTS=Map.ofEntries(
            Map.entry(0,"protection"),Map.entry(1,"fire_protection"),Map.entry(2,"feather_falling"),Map.entry(3,"blast_protection"),
            Map.entry(4,"projectile_protection"),Map.entry(5,"respiration"),Map.entry(6,"aqua_affinity"),Map.entry(7,"thorns"),
            Map.entry(16,"sharpness"),Map.entry(17,"smite"),Map.entry(18,"bane_of_arthropods"),Map.entry(19,"knockback"),Map.entry(20,"fire_aspect"),Map.entry(21,"looting"),
            Map.entry(32,"efficiency"),Map.entry(33,"silk_touch"),Map.entry(34,"unbreaking"),Map.entry(35,"fortune"),
            Map.entry(48,"power"),Map.entry(49,"punch"),Map.entry(50,"flame"),Map.entry(51,"infinity"),Map.entry(61,"luck_of_the_sea"),Map.entry(62,"lure"));
    private LegacyVanillaStacks() {}
    static ItemStack toHost(LegacyServices services,LegacyClientData.Stack old) {
        if(!old.key().startsWith("minecraft:")||(old.tag().isEmpty()&&!old.key().equals("minecraft:potion")))return ItemStack.EMPTY;
        try {
            var tag=originalTag(services,old);
            boolean potion=old.key().equals("minecraft:potion");
            var result=potion?new ItemStack((old.metadata()&0x4000)!=0?Items.SPLASH_POTION:Items.POTION,old.count()):HostStackMapping.renderStack(old);
            if(result.isEmpty())return result;
            for(String name:tag.getAllKeys()) {
                switch(name) {
                    case "ench","StoredEnchantments" -> result.getOrCreateTag().put(name.equals("ench")?"Enchantments":name,enchantments(tag.getList(name,Tag.TAG_COMPOUND),true));
                    case "RepairCost","Unbreakable","HideFlags" -> result.getOrCreateTag().put(name,tag.get(name).copy());
                    case "display" -> {
                        var display=tag.getCompound(name).copy();
                        if(display.contains("Name",Tag.TAG_STRING))display.putString("Name",Component.Serializer.toJson(Component.literal(display.getString("Name"))));
                        if(display.contains("Lore",Tag.TAG_LIST)) {
                            var lore=new ListTag();for(Tag line:display.getList("Lore",Tag.TAG_STRING))lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line.getAsString()))));display.put("Lore",lore);
                        }
                        result.getOrCreateTag().put(name,display);
                    }
                    case "CustomPotionEffects" -> result.getOrCreateTag().put(name,tag.get(name).copy());
                    default -> throw new UnsupportedOperationException("Unmapped old vanilla loot NBT: "+old.key()+"."+name);
                }
            }
            if(potion&&!tag.contains("CustomPotionEffects")) {
                var effects=services.items().inLegacy(()->{
                    var loader=services.loader();Object stack=services.items().materialize(old);
                    Object item=loader.loadClass("net.minecraft.item.ItemStack").getMethod("func_77973_b").invoke(stack);
                    var list=(List<?>)item.getClass().getMethod("func_77832_l",loader.loadClass("net.minecraft.item.ItemStack")).invoke(item,stack);
                    var output=new ListTag();if(list==null)return output;
                    for(Object effect:list) {
                        var type=loader.loadClass("net.minecraft.potion.PotionEffect");int id=(int)type.getMethod("func_76456_a").invoke(effect);
                        if(id<1||id>23||MobEffect.byId(id)==null)throw new UnsupportedOperationException("Unmapped old potion effect: "+id);
                        var entry=new CompoundTag();entry.putByte("Id",(byte)id);entry.putInt("Duration",(int)type.getMethod("func_76459_b").invoke(effect));
                        entry.putByte("Amplifier",((Integer)type.getMethod("func_76458_c").invoke(effect)).byteValue());output.add(entry);
                    }
                    return output;
                });
                result.getOrCreateTag().put("CustomPotionEffects",effects);
            }
            var source=new CompoundTag();source.putString("key",old.key());source.putInt("metadata",old.metadata());source.put("tag",tag.copy());
            result.getOrCreateTag().put(SOURCE,source);
            return result;
        }catch(RuntimeException failure){throw failure;}
        catch(Exception failure){throw new IllegalStateException("Cannot convert original vanilla loot: "+old.key(),failure);}
    }
    static LegacyClientData.Stack toLegacy(ItemStack stack) throws Exception {
        var root=stack.getTag();if(root==null||!root.contains(SOURCE,Tag.TAG_COMPOUND))return null;
        var source=root.getCompound(SOURCE);String key=source.getString("key");
        if(!key.startsWith("minecraft:"))throw new IllegalArgumentException("Invalid original loot identity: "+key);
        var tag=source.getCompound("tag").copy();
        // Native enchanting/repairing can change supported enchantments after the item was awarded.
        for(String name:List.of("ench","StoredEnchantments")) {
            String modern=name.equals("ench")?"Enchantments":name;
            var pending=new LinkedHashMap<Short,CompoundTag>();
            for(Tag entry:enchantments(root.getList(modern,Tag.TAG_COMPOUND),false))pending.put(((CompoundTag)entry).getShort("id"),(CompoundTag)entry);
            var list=new ListTag();
            for(Tag entry:tag.getList(name,Tag.TAG_COMPOUND)) {
                short id=((CompoundTag)entry).getShort("id");
                if(!ENCHANTMENTS.containsKey((int)id))list.add(entry.copy());
                else if(pending.containsKey(id))list.add(pending.remove(id));
            }
            pending.values().forEach(list::add);
            if(list.isEmpty())tag.remove(name);else tag.put(name,list);
        }
        if(root.contains("RepairCost"))tag.putInt("RepairCost",root.getInt("RepairCost"));
        for(String name:List.of("Unbreakable","HideFlags"))if(root.contains(name))tag.put(name,root.get(name).copy());else tag.remove(name);
        if(root.contains("display",Tag.TAG_COMPOUND)) {
            var display=root.getCompound("display").copy();
            if(display.contains("Name",Tag.TAG_STRING)) {
                var name=Component.Serializer.fromJson(display.getString("Name"));display.putString("Name",name==null?"":name.getString());
            }
            if(display.contains("Lore",Tag.TAG_LIST)) {
                var lore=new ListTag();for(Tag line:display.getList("Lore",Tag.TAG_STRING)) {
                    var text=Component.Serializer.fromJson(line.getAsString());lore.add(StringTag.valueOf(text==null?"":text.getString()));
                }
                display.put("Lore",lore);
            }
            tag.put("display",display);
        }else tag.remove("display");
        return new LegacyClientData.Stack(key,stack.isDamageableItem()?stack.getDamageValue():source.getInt("metadata"),stack.getCount(),stack.getHoverName().getString(),tag.isEmpty()?"":tag.toString());
    }
    private static CompoundTag originalTag(LegacyServices services,LegacyClientData.Stack old) throws Exception {
        if(old.tag().isEmpty())return new CompoundTag();
        // Old list SNBT includes indexes (e.g. [0:{id:1s}]); binary NBT has the same tag types in both versions.
        byte[] bytes=services.items().inLegacy(()->{
            var loader=services.loader();Object stack=services.items().materialize(old);
            Object tag=loader.loadClass("net.minecraft.item.ItemStack").getField("field_77990_d").get(stack);
            var output=new java.io.ByteArrayOutputStream();
            loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74799_a",loader.loadClass("net.minecraft.nbt.NBTTagCompound"),java.io.OutputStream.class).invoke(null,tag,output);
            return output.toByteArray();
        });
        return NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes));
    }
    private static ListTag enchantments(ListTag input,boolean modern) {
        var output=new ListTag();
        for(Tag value:input) {
            var entry=(CompoundTag)value;var mapped=new CompoundTag();
            if(modern) {
                int id=entry.getShort("id");String name=ENCHANTMENTS.get(id);
                if(name==null){LegacyDiagnostics.missingMapping("enchantment "+id,"old-only enchantment retained in original loot NBT");continue;}
                mapped.putString("id","minecraft:"+name);
            }else {
                String id=entry.getString("id");var found=ENCHANTMENTS.entrySet().stream().filter(e->id.equals("minecraft:"+e.getValue())).findFirst();
                if(found.isEmpty())throw new UnsupportedOperationException("Host enchantment has no old mapping: "+id);
                mapped.putShort("id",found.get().getKey().shortValue());
            }
            mapped.putShort("lvl",entry.getShort("lvl"));output.add(mapped);
        }
        return output;
    }
}
