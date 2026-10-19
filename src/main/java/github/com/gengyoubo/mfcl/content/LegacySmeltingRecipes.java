package github.com.gengyoubo.mfcl.content;

import com.google.gson.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Native furnace recipes whose ingredient matching and result come from the original 1.7.10
 * FurnaceRecipes table, so legacy items keep their old metadata/NBT semantics in a modern furnace.
 */
public final class LegacySmeltingRecipes {
    public static final ResourceLocation TYPE=ResourceLocation.fromNamespaceAndPath("mfcl","legacy_smelting");
    private static final Serializer SERIALIZER=new Serializer();
    private LegacySmeltingRecipes() {}
    public static void install(IEventBus bus) {
        bus.addListener((RegisterEvent event)->event.register(Registries.RECIPE_SERIALIZER,helper->helper.register(TYPE,SERIALIZER)));
    }
    private interface Original {String original();}
    private static ModernContentBridge bridge(){return java.util.Objects.requireNonNull(ModernContentBridge.active());}
    private static Smelting create(ResourceLocation id,String hash) {
        var content=bridge();var definition=content.crafting().smeltingDefinition(hash);
        try {
            Ingredient ingredient=LegacyCraftingRecipes.ingredient(definition.inputs(),false);
            ItemStack result=content.gameplayStack(definition.output());
            return new Smelting(id,hash,ingredient,result,definition.experience());
        }catch(Exception failure){throw new JsonParseException("Cannot publish original smelting recipe "+hash,failure);}
    }
    private static final class Smelting extends SmeltingRecipe implements Original {
        private final String hash;
        Smelting(ResourceLocation id,String hash,Ingredient ingredient,ItemStack result,float experience){
            super(id,"mfcl_legacy",CookingBookCategory.MISC,ingredient,result,experience,200);this.hash=hash;
        }
        public String original(){return hash;}
        @Override public RecipeSerializer<?> getSerializer(){return SERIALIZER;}
    }
    private static final class Serializer implements RecipeSerializer<Smelting> {
        public Smelting fromJson(ResourceLocation id,JsonObject json){return create(id,json.get("original").getAsString());}
        public Smelting fromNetwork(ResourceLocation id,FriendlyByteBuf data){return create(id,data.readUtf(64));}
        public void toNetwork(FriendlyByteBuf data,Smelting recipe){data.writeUtf(recipe.original(),64);}
    }
}
