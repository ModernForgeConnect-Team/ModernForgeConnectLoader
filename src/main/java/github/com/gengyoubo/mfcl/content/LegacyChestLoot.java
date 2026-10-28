package github.com.gengyoubo.mfcl.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import github.com.gengyoubo.mfcl.bridge.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.registries.*;
import java.lang.reflect.Array;
import java.util.*;

/** Augments native chest tables with mod additions, using the original weighted selection and stack generator. */
public final class LegacyChestLoot extends LootModifier {
    public static final Codec<LegacyChestLoot> CODEC=RecordCodecBuilder.create(instance->codecStart(instance).apply(instance,LegacyChestLoot::new));
    private static final Map<String,String> TABLES=Map.ofEntries(
        Map.entry("simple_dungeon","dungeonChest"),Map.entry("abandoned_mineshaft","mineshaftCorridor"),
        Map.entry("desert_pyramid","pyramidDesertyChest"),Map.entry("jungle_temple","pyramidJungleChest"),Map.entry("jungle_temple_dispenser","pyramidJungleDispenser"),
        Map.entry("stronghold_corridor","strongholdCorridor"),Map.entry("stronghold_library","strongholdLibrary"),Map.entry("stronghold_crossing","strongholdCrossing"),
        Map.entry("spawn_bonus_chest","bonusChest"),Map.entry("village/village_weaponsmith","villageBlacksmith"));
    public LegacyChestLoot(LootItemCondition[] conditions){super(conditions);}
    public Codec<? extends IGlobalLootModifier> codec(){return CODEC;}
    static void register(RegisterEvent event){event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,registry->registry.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mfcl","legacy_chest_hooks"),CODEC));}
    static void generate(LegacyContentPack pack)throws Exception{
        pack.json("data/forge/loot_modifiers/global_loot_modifiers.json",Map.of("replace",false,"entries",List.of("mfcl:legacy_chest_hooks")));
        pack.json("data/mfcl/loot_modifiers/legacy_chest_hooks.json",Map.of("type","mfcl:legacy_chest_hooks","conditions",List.of()));
    }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context){
        var table=context.getQueriedLootTableId();if(!table.getNamespace().equals("minecraft")||!table.getPath().startsWith("chests/"))return loot;
        String category=TABLES.get(table.getPath().substring(7));var content=ModernContentBridge.active();if(category==null||content==null)return loot;
        try{return content.services().items().inLegacy(()->{
            var services=content.services();var loader=services.loader();var hooks=loader.loadClass("net.minecraftforge.common.ChestGenHooks");
            Object info=hooks.getMethod("getInfo",String.class).invoke(null,category);var random=new Random(context.getRandom().nextLong());
            var entry=loader.loadClass("net.minecraft.util.WeightedRandomChestContent");var additions=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            for(var registration:LegacyRegistrations.loot(loader))if(registration.category().equals(category))additions.add(registration.content());
            // Keep provenance when Item.getChestGenBase returns a replacement (notably enchanted books).
            var field=hooks.getDeclaredField("contents");field.setAccessible(true);var originals=List.copyOf((List<?>)field.get(info));var pool=new ArrayList<Object>();var preparedAdditions=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            var itemType=loader.loadClass("net.minecraft.item.Item");var stackType=loader.loadClass("net.minecraft.item.ItemStack");
            for(Object original:originals){Object stack=entry.getField("field_76297_b").get(original),item=stackType.getMethod("func_77973_b").invoke(stack);if(item==null)continue;Object prepared=itemType.getMethod("getChestGenBase",hooks,Random.class,entry).invoke(item,info,random,original);if(prepared!=null){pool.add(prepared);if(additions.contains(original))preparedAdditions.add(prepared);}}
            if(!preparedAdditions.isEmpty()&&!pool.isEmpty()){
                int rolls=(int)hooks.getMethod("getCount",Random.class).invoke(info,random);
                var inventory=loader.loadClass("net.minecraft.inventory.InventoryBasic");var iinventory=loader.loadClass("net.minecraft.inventory.IInventory");
                var choose=loader.loadClass("net.minecraft.util.WeightedRandom").getMethod("func_76271_a",Random.class,Collection.class);
                var generate=entry.getMethod("func_76293_a",Random.class,Array.newInstance(entry,0).getClass(),iinventory,int.class);
                for(int roll=0;roll<rolls;roll++){
                    Object selected=choose.invoke(null,random,pool);if(!preparedAdditions.contains(selected))continue;
                    Object single=Array.newInstance(entry,1);Array.set(single,0,selected);Object slots=inventory.getConstructor(String.class,boolean.class,int.class).newInstance("MFCL loot",true,54);
                    generate.invoke(null,random,single,slots,1);
                    for(int slot=0;slot<54;slot++){Object stack=iinventory.getMethod("func_70301_a",int.class).invoke(slots,slot);if(stack!=null){var mapped=content.gameplayStack(services.items().snapshot(stack));if(!mapped.isEmpty())loot.add(mapped);}}
                }
            }
            for(var removal:LegacyRegistrations.removedLoot(loader))if(removal.category().equals(category)){
                var removed=services.items().snapshot(removal.content());if(removed==null)continue;
                for(var iterator=loot.iterator();iterator.hasNext();){var stack=iterator.next();LegacyClientData.Stack original;
                    try{original=content.gameplayStack(stack);}catch(UnsupportedOperationException modernOnly){continue;}
                    if(original!=null&&original.key().equals(removed.key())&&(removed.metadata()==32767||original.metadata()==removed.metadata())){
                        boolean readded=false;for(Object current:originals){var present=services.items().snapshot(entry.getField("field_76297_b").get(current));if(present!=null&&present.key().equals(original.key())&&(present.metadata()==32767||present.metadata()==original.metadata())){readded=true;break;}}
                        if(!readded)iterator.remove();
                    }}
            }
            return loot;
        });}catch(Exception failure){throw new IllegalStateException("Cannot generate original chest loot "+category,failure);}
    }
}
