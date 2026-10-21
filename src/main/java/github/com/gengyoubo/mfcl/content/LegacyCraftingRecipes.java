package github.com.gengyoubo.mfcl.content;

import com.google.gson.*;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Native workbench/JEI recipes with matching, output and container items evaluated by the original recipe. */
public final class LegacyCraftingRecipes {
    public static final ResourceLocation TYPE=ResourceLocation.fromNamespaceAndPath("mfcl","legacy_crafting");
    private static final Serializer SERIALIZER=new Serializer();
    private LegacyCraftingRecipes() {}
    public static void install(IEventBus bus) {
        bus.addListener((RegisterEvent event)->event.register(Registries.RECIPE_SERIALIZER,helper->helper.register(TYPE,SERIALIZER)));
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(()->CraftingHelper.register(ResourceLocation.fromNamespaceAndPath("mfcl","legacy_stack"),StackIngredient.SERIALIZER)));
    }
    private interface Original {String original();}
    private static ModernContentBridge bridge(){return Objects.requireNonNull(ModernContentBridge.active());}
    private static CraftingRecipe create(ResourceLocation id,String hash) {
        var content=bridge();var definition=content.crafting().definition(hash);var ingredients=NonNullList.<Ingredient>create();
        if(definition.dynamic())return new Dynamic(id,hash);
        try {
            for(var input:definition.inputs()) ingredients.add(ingredient(input,definition.strictNbt()));
            ItemStack result=content.gameplayStack(definition.output());
            return definition.shaped()?new Shaped(id,hash,definition.width(),definition.height(),ingredients,result):new Shapeless(id,hash,ingredients,result);
        }catch(Exception failure){throw new JsonParseException("Cannot publish original ordinary recipe "+hash,failure);}
    }
    /** Host representation of one original ingredient; metadata 32767 becomes a wildcard option set. */
    static Ingredient ingredient(List<LegacyClientData.Stack> input,boolean strictNbt) throws Exception {
        var content=bridge();
        if(input.isEmpty())return Ingredient.EMPTY;
        var variants=new ArrayList<Option>();
        for(var old:input) {
            boolean wildcard=old.metadata()==32767;
            var candidates=wildcard?content.services().items().catalog(old.key()).stream().filter(s->s.metadata()!=32767).toList():List.of(old);
            if(candidates.isEmpty())candidates=List.of(new LegacyClientData.Stack(old.key(),0,1,"",old.tag()));
            for(var candidate:candidates) {
                ItemStack mapped=recipeStack(candidate);if(mapped.isEmpty())continue;
                if(variants.stream().noneMatch(o->o.wildcard()==wildcard&&ItemStack.isSameItemSameTags(o.stack(),mapped)))variants.add(new Option(mapped,wildcard,strictNbt));
            }
        }
        if(variants.isEmpty())throw new JsonParseException("No host ingredient representation for original stack: "+input);
        return new StackIngredient(variants);
    }
    /** Old recipes can explicitly request damage on a non-subtype item (stick@1, torch@1).
     * Keep that exact requirement in the projected ingredient; an ordinary metadata-zero item must not match it. */
    private static ItemStack recipeStack(LegacyClientData.Stack old) throws Exception {
        var content=bridge();ItemStack mapped=content.mapStack(old);
        if(!mapped.isEmpty()||old.metadata()<=0||old.metadata()==32767||!old.key().startsWith("minecraft:")||!old.tag().isEmpty())return mapped;
        Object item=content.services().items().legacyItem(old.key());
        Class<?> type=content.services().loader().loadClass("net.minecraft.item.Item");
        if(item==null||(boolean)type.getMethod("func_77614_k").invoke(item)||(boolean)type.getMethod("func_77645_m").invoke(item))return mapped;
        mapped=content.mapStack(new LegacyClientData.Stack(old.key(),0,old.count(),old.name(),""));
        if(!mapped.isEmpty())mapped.getOrCreateTagElement(ModernContentBridge.STACK_TAG).putInt("metadata",old.metadata());
        return mapped;
    }
    private static List<LegacyClientData.Stack> input(CraftingContainer grid) throws Exception {
        var result=new ArrayList<LegacyClientData.Stack>();var content=bridge();
        for(int slot=0;slot<grid.getContainerSize();slot++) {
            ItemStack stack=grid.getItem(slot);
            if(stack.isEmpty()){result.add(null);continue;}
            result.add(legacy(stack));
        }
        return result;
    }
    private static LegacyClientData.Stack legacy(ItemStack stack) throws Exception {
        var old=bridge().renderSnapshot(stack);if(old!=null)return old;
        var stored=stack.getTagElement(ModernContentBridge.STACK_TAG);
        if(stored!=null&&stored.contains("metadata")) {
            ItemStack base=stack.copy();base.removeTagKey(ModernContentBridge.STACK_TAG);var original=bridge().gameplayStack(base);
            return new LegacyClientData.Stack(original.key(),stored.getInt("metadata"),stack.getCount(),original.name(),original.tag());
        }
        ItemStack clean=stack.copy();clean.setTag(null);if(stack.getItem().canBeDepleted())clean.setDamageValue(stack.getDamageValue());return bridge().gameplayStack(clean);
    }
    private static boolean matches(String hash,CraftingContainer grid,Level level) {
        try{return bridge().crafting().matches(hash,grid.getWidth(),grid.getHeight(),input(grid),bridge().services().worlds().bound(level));}
        catch(UnsupportedOperationException unsupported){return false;}
        catch(Exception failure){throw new IllegalStateException("Cannot match original ordinary recipe "+hash,failure);}
    }
    private static ItemStack assemble(String hash,CraftingContainer grid) {
        try {var result=bridge().crafting().assemble(hash,grid.getWidth(),grid.getHeight(),input(grid));return result==null?ItemStack.EMPTY:bridge().gameplayStack(result);}
        catch(Exception failure){throw new IllegalStateException("Cannot assemble original ordinary recipe "+hash,failure);}
    }
    private static NonNullList<ItemStack> remaining(CraftingContainer grid) {
        var result=NonNullList.withSize(grid.getContainerSize(),ItemStack.EMPTY);
        try {var input=input(grid);for(int index=0;index<input.size();index++){var old=bridge().crafting().remainder(input.get(index));if(old!=null)result.set(index,bridge().gameplayStack(old));}}
        catch(Exception failure){throw new IllegalStateException("Cannot evaluate original crafting container items",failure);}return result;
    }
    private static final class Shaped extends ShapedRecipe implements Original {
        private final String hash;
        Shaped(ResourceLocation id,String hash,int width,int height,NonNullList<Ingredient> input,ItemStack result){super(id,"mfcl_legacy",CraftingBookCategory.MISC,width,height,input,result);this.hash=hash;}
        public String original(){return hash;}
        @Override public RecipeSerializer<?> getSerializer(){return SERIALIZER;}
        @Override public boolean matches(CraftingContainer grid,Level level){return LegacyCraftingRecipes.matches(hash,grid,level);}
        @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess access){return LegacyCraftingRecipes.assemble(hash,grid);}
        @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid){return remaining(grid);}
    }
    private static final class Shapeless extends ShapelessRecipe implements Original {
        private final String hash;
        Shapeless(ResourceLocation id,String hash,NonNullList<Ingredient> input,ItemStack result){super(id,"mfcl_legacy",CraftingBookCategory.MISC,result,input);this.hash=hash;}
        public String original(){return hash;}
        @Override public RecipeSerializer<?> getSerializer(){return SERIALIZER;}
        @Override public boolean matches(CraftingContainer grid,Level level){return LegacyCraftingRecipes.matches(hash,grid,level);}
        @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess access){return LegacyCraftingRecipes.assemble(hash,grid);}
        @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid){return remaining(grid);}
    }
    private static final class Dynamic extends CustomRecipe implements Original {
        private final String hash;
        Dynamic(ResourceLocation id,String hash){super(id,CraftingBookCategory.MISC);this.hash=hash;}
        public String original(){return hash;}
        @Override public RecipeSerializer<?> getSerializer(){return SERIALIZER;}
        @Override public boolean matches(CraftingContainer grid,Level level){return LegacyCraftingRecipes.matches(hash,grid,level);}
        @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess access){return LegacyCraftingRecipes.assemble(hash,grid);}
        @Override public boolean canCraftInDimensions(int width,int height){return width*height>=2;}
        @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid){return remaining(grid);}
    }
    private static final class Serializer implements RecipeSerializer<CraftingRecipe> {
        public CraftingRecipe fromJson(ResourceLocation id,JsonObject json){return create(id,json.get("original").getAsString());}
        public CraftingRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf data){return create(id,data.readUtf(64));}
        public void toNetwork(FriendlyByteBuf data,CraftingRecipe recipe){data.writeUtf(((Original)recipe).original(),64);}
    }
    record Option(ItemStack stack,boolean wildcard,boolean strictNbt) {}
    static final class StackIngredient extends AbstractIngredient {
        private final List<Option> options;
        StackIngredient(List<Option> options){this.options=List.copyOf(options);}
        public boolean isSimple(){return false;}
        public IIngredientSerializer<StackIngredient> getSerializer(){return SERIALIZER;}
        @Override public ItemStack[] getItems(){return options.stream().map(o->o.stack().copy()).toArray(ItemStack[]::new);}
        @Override public boolean isEmpty(){return options.isEmpty();}
        @Override public boolean test(ItemStack stack){if(stack==null||stack.isEmpty())return false;for(var option:options)if(ItemStack.isSameItem(stack,option.stack())&&(option.wildcard()||ModernContentBridge.metadata(stack)==ModernContentBridge.metadata(option.stack())))try{if(!option.strictNbt()||bridge().services().items().tagsEqual(legacy(stack),legacy(option.stack())))return true;}catch(Exception unsupported){return false;}return false;}
        public JsonElement toJson(){var json=new JsonObject();json.addProperty("type","mfcl:legacy_stack");var values=new JsonArray();for(var option:options){var value=new JsonObject();value.addProperty("item",BuiltInRegistries.ITEM.getKey(option.stack().getItem()).toString());value.addProperty("wildcard",option.wildcard());value.addProperty("strict_nbt",option.strictNbt());if(option.stack().hasTag())value.addProperty("nbt",option.stack().getTag().toString());values.add(value);}json.add("options",values);return json;}
        private static final IIngredientSerializer<StackIngredient> SERIALIZER=new IIngredientSerializer<>() {
            public StackIngredient parse(FriendlyByteBuf data){int size=data.readVarInt();if(size<1||size>4096)throw new IllegalArgumentException("Invalid old ingredient alternatives");var values=new ArrayList<Option>();for(int index=0;index<size;index++)values.add(new Option(data.readItem(),data.readBoolean(),data.readBoolean()));return new StackIngredient(values);}
            public StackIngredient parse(JsonObject json){var values=new ArrayList<Option>();for(var value:json.getAsJsonArray("options")){var option=value.getAsJsonObject();values.add(new Option(CraftingHelper.getItemStack(option,true),option.has("wildcard")&&option.get("wildcard").getAsBoolean(),option.has("strict_nbt")&&option.get("strict_nbt").getAsBoolean()));}return new StackIngredient(values);}
            public void write(FriendlyByteBuf data,StackIngredient ingredient){data.writeVarInt(ingredient.options.size());for(var option:ingredient.options){data.writeItem(option.stack());data.writeBoolean(option.wildcard());data.writeBoolean(option.strictNbt());}}
        };
    }
}
