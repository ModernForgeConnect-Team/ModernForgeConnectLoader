package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real native crafting menu, recipe sync/JEI, metadata validation and /reload acceptance. */
final class CraftingHostSmokeProbe {
    private final ModernContentBridge content;private final Map<String,Object> proof=new LinkedHashMap<>();
    private int stage,frames,count,staticCount;private CompletableFuture<?> pending;private LegacyCrafting.Definition phial;
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private CraftingHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new CraftingHostSmokeProbe(content)::tick);}
    private static boolean ours(Recipe<?> recipe){return recipe.getId().getPath().startsWith("legacy_crafting/")&&net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).equals(github.com.gengyoubo.mfcl.content.LegacyCraftingRecipes.TYPE);}
    private ResourceLocation id(LegacyCrafting.Definition recipe){return ResourceLocation.fromNamespaceAndPath(github.com.gengyoubo.mfcl.content.LegacyRegistryIds.recipeNamespace(recipe.output()),"legacy_crafting/"+recipe.hash());}
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new IllegalStateException("Crafting acceptance timed out at stage "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-crafting-smoke-"+System.currentTimeMillis(),new LevelSettings("MFCL ordinary crafting",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null) {
                count=content.crafting().definitions().size();staticCount=(int)content.crafting().definitions().stream().filter(d->!d.dynamic()).count();if(count==0)throw new IllegalStateException("No old ordinary recipes captured");
                phial=content.crafting().definitions().stream().filter(d->d.output()!=null&&d.output().key().equals("Thaumcraft:ItemEssence")&&d.output().metadata()==0&&d.output().count()==8).findFirst().orElseThrow();
                pending=server(mc,()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();var manager=level.getRecipeManager();
                    long exported=manager.getRecipes().stream().filter(CraftingHostSmokeProbe::ours).count();if(exported!=count)throw new IllegalStateException("Recipes missing on server: "+exported+"/"+count);
                    int shaped=0,shapeless=0;
                    for(var definition:content.crafting().definitions()) {
                        if(definition.dynamic())continue;
                        var recipe=(CraftingRecipe)manager.byKey(id(definition)).orElseThrow();var grid=grid(recipe);
                        if(!recipe.matches(grid,level)||!ItemStack.matches(recipe.assemble(grid,level.registryAccess()),content.gameplayStack(definition.output())))throw new IllegalStateException("Native recipe did not preserve its original output: "+definition);
                        if(definition.shaped())shaped++;else shapeless++;
                        // The output must be a copy: caller mutations must not poison later crafts.
                        var output=recipe.assemble(grid,level.registryAccess());output.setCount(0);if(recipe.assemble(grid,level.registryAccess()).isEmpty())throw new IllegalStateException("Crafted output aliases recipe state");
                    }
                    var slab=content.crafting().definitions().stream().filter(d->d.output()!=null&&d.output().key().equals("Thaumcraft:blockCosmeticOpaque")&&d.output().metadata()==0).findFirst().orElseThrow();
                    var recipe=(CraftingRecipe)manager.byKey(id(slab)).orElseThrow();var wrong=grid(recipe);
                    for(int slot=0;slot<wrong.getContainerSize();slot++)if(!wrong.getItem(slot).isEmpty())wrong.setItem(slot,content.stack("Thaumcraft:ItemResource",7,1,""));
                    if(recipe.matches(wrong,level))throw new IllegalStateException("Wrong legacy metadata crafted successfully");
                    int nbtRecipes=0;
                    for(var definition:content.crafting().definitions())if(definition.strictNbt()) {
                        var nbtRecipe=(CraftingRecipe)manager.byKey(id(definition)).orElseThrow();var invalid=grid(nbtRecipe);boolean changed=false;
                        for(int slot=0;slot<invalid.getContainerSize();slot++){var old=content.renderSnapshot(invalid.getItem(slot));if(old!=null&&!old.tag().isEmpty()){invalid.setItem(slot,content.stack(old.key(),old.metadata(),1,""));changed=true;break;}}
                        if(changed&&nbtRecipe.matches(invalid,level))throw new IllegalStateException("Missing aspect NBT was accepted");nbtRecipes++;
                    }
                    var dye=grid((CraftingRecipe)manager.byKey(id(phial)).orElseThrow());dye.clearContent();dye.setItem(0,content.stack("Thaumcraft:ItemChestplateRobe",0,1,""));dye.setItem(8,new ItemStack(Items.RED_DYE));
                    var dyed=manager.getRecipeFor(RecipeType.CRAFTING,dye,level).orElseThrow().assemble(dye,level.registryAccess());
                    var dyedOld=content.renderSnapshot(dyed);if(dyedOld==null||!dyedOld.tag().contains("color"))throw new IllegalStateException("Ordinary robe dye crafting lost dynamic color NBT");
                    proof.put("nbtSensitiveRecipesVerified",nbtRecipes);proof.put("dynamicRobeDyeRecipeVerified",true);proof.put("dynamicWorkbenchRecipes",count-staticCount);
                    proof.put("serverOrdinaryRecipes",exported);proof.put("shapedRecipesVerified",shaped);proof.put("shapelessRecipesVerified",shapeless);proof.put("wrongMetadataRejected",true);
                    var table=new BlockPos(0,-60,0);level.setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());player.teleportTo(.5,-59,.5);
                    player.openMenu(level.getBlockState(table).getMenuProvider(level,table));fill((CraftingMenu)player.containerMenu,(CraftingRecipe)manager.byKey(id(phial)).orElseThrow());return true;
                });stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&mc.player.containerMenu instanceof CraftingMenu&&++frames>15) {
                pending.join();if(!mc.player.containerMenu.getSlot(0).getItem().is(content.items().get("Thaumcraft:ItemEssence")))return;
                if(mc.level.getRecipeManager().getRecipes().stream().filter(CraftingHostSmokeProbe::ours).count()!=count)throw new IllegalStateException("Old recipes did not synchronize to client");
                verifyJei();mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);stage=3;frames=0;
            }else if(stage==3&&++frames>10) {
                pending=server(mc,()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var menu=player.containerMenu;
                    if(!menu.getCarried().is(content.items().get("Thaumcraft:ItemEssence"))||menu.getCarried().getCount()!=8)throw new IllegalStateException("Native result pickup did not deliver 8 phials");
                    for(int slot=1;slot<=9;slot++)if(!menu.getSlot(slot).getItem().isEmpty()&&menu.getSlot(slot).getItem().getCount()!=1)throw new IllegalStateException("Crafting did not consume one ingredient per occupied slot");
                    proof.put("nativeWorkbenchPickupAndConsumption",true);player.closeContainer();return true;
                });stage=4;
            }else if(stage==4&&pending.isDone()) {
                pending.join();pending=mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().reloadResources(mc.getSingleplayerServer().getPackRepository().getSelectedIds())).thenCompose(future->future);stage=5;frames=0;
            }else if(stage==5&&pending.isDone()&&++frames>20) {
                pending.join();if(mc.level.getRecipeManager().getRecipes().stream().filter(CraftingHostSmokeProbe::ours).count()!=count)return;
                verifyJei();pending=server(mc,()->{
                    var manager=mc.getSingleplayerServer().getRecipeManager();if(manager.getRecipes().stream().filter(CraftingHostSmokeProbe::ours).count()!=count)throw new IllegalStateException("Reload lost or duplicated old recipes");
                    var recipe=(CraftingRecipe)manager.byKey(id(phial)).orElseThrow();if(!recipe.matches(grid(recipe),mc.getSingleplayerServer().overworld()))throw new IllegalStateException("Reload invalidated original recipe references");return true;
                });stage=6;
            }else if(stage==6&&pending.isDone()) {
                pending.join();proof.put("reloadPreservedRecipesWithoutDuplication",true);proof.put("clientRecipeSyncVerified",true);proof.put("unsupportedCustomRecipes",content.crafting().diagnostics());proof.put("status","PASS");finish(mc,null);
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private TransientCraftingContainer grid(CraftingRecipe recipe) {
        var menu=new AbstractContainerMenu(null,-1){public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int index){return ItemStack.EMPTY;}public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}};
        var grid=new TransientCraftingContainer(menu,3,3);var input=recipe.getIngredients();int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
        for(int index=0;index<input.size();index++)if(!input.get(index).isEmpty()) {
            var options=input.get(index).getItems();if(options.length==0)throw new IllegalStateException("Empty recipe alternatives");
            grid.setItem(index/width*3+index%width,options[0].copy());
        }
        return grid;
    }
    private void fill(CraftingMenu menu,CraftingRecipe recipe) {var grid=grid(recipe);for(int slot=0;slot<9;slot++){var input=grid.getItem(slot).copy();if(!input.isEmpty())input.setCount(2);menu.getSlot(slot+1).set(input);}menu.broadcastChanges();}
    private void verifyJei() throws Exception {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("jei")){proof.put("jei","not installed");return;}
        Object runtime=Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
        Object manager=Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getRecipeManager").invoke(runtime);
        Object type=Class.forName("mezz.jei.api.constants.RecipeTypes").getField("CRAFTING").get(null);
        Object lookup=Class.forName("mezz.jei.api.recipe.IRecipeManager").getMethod("createRecipeLookup",Class.forName("mezz.jei.api.recipe.RecipeType")).invoke(manager,type);
        try(var recipes=(java.util.stream.Stream<?>)Class.forName("mezz.jei.api.recipe.IRecipeLookup").getMethod("get").invoke(lookup)) {
            long visible=recipes.filter(value->value instanceof Recipe<?> recipe&&ours(recipe)).count();
            if(visible!=staticCount)throw new IllegalStateException("JEI is missing ordinary recipes: "+visible+"/"+staticCount);proof.put("jeiOrdinaryRecipes",visible);
        }
    }
    private <T> CompletableFuture<T> server(Minecraft mc,Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private void finish(Minecraft mc,Throwable failure) {
        stage=99;if(failure!=null){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_CRAFTING_SMOKE_FAIL",failure);}
        try{var file=mc.gameDirectory.toPath().resolve("logs/mfcl/crafting-smoke.json");java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_CRAFTING_SMOKE_RESULT: {}",proof);if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
