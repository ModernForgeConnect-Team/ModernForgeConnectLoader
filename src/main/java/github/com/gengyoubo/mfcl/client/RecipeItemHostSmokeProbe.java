package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ScreenEvent;
import java.util.*;

/** Opt-in native JEI recipe and pixel acceptance for original ingot models. */
final class RecipeItemHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private int stage,frames;
    private long deadline;
    private RecipeItemHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){var probe=new RecipeItemHostSmokeProbe(content);MinecraftForge.EVENT_BUS.addListener(probe::tick);MinecraftForge.EVENT_BUS.addListener(probe::render);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(deadline==0)deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);
            if(System.nanoTime()>deadline)throw new IllegalStateException("Recipe item acceptance timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                var sorted=net.minecraftforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs();
                int previous=sorted.indexOf(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS));
                if(previous!=9||sorted.contains(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.world.item.CreativeModeTabs.OP_BLOCKS)))throw new IllegalStateException("Vanilla creative tabs no longer fill page one: spawn eggs at "+previous);
                for(var tab:content.declarations().tabs())if(!tab.builtin()){
                    int position=sorted.indexOf(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(github.com.gengyoubo.mfcl.content.CreativeTabBridge.id(tab)));
                    if(position<=previous)throw new IllegalStateException("Legacy creative tab precedes vanilla/predecessor: "+tab.key());previous=position;
                }
                proof.put("creativeTabsAfterVanillaInOriginalOrder",true);
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-recipe-items-"+System.currentTimeMillis(),new net.minecraft.world.level.LevelSettings("MFCL recipe items",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.screen==null&&++frames>30){
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                Object runtime=Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
                Object helpers=Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getJeiHelpers").invoke(runtime);
                Object factory=Class.forName("mezz.jei.api.helpers.IJeiHelpers").getMethod("getFocusFactory").invoke(helpers);
                Class<?> role=Class.forName("mezz.jei.api.recipe.RecipeIngredientRole"),type=Class.forName("mezz.jei.api.ingredients.IIngredientType");
                Object ingredientType=Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
                var entry=content.declarations().items().stream().filter(item->item.key().toLowerCase(Locale.ROOT).contains("unstableingot")).findFirst().orElseThrow();
                var stack=content.stack(entry.key(),0,1,"");proof.put("recipeOutput",entry.key());
                Object focus=Class.forName("mezz.jei.api.recipe.IFocusFactory").getMethod("createFocus",role,type,Object.class).invoke(factory,role.getField("OUTPUT").get(null),ingredientType,stack);
                Object gui=Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getRecipesGui").invoke(runtime);
                Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("show",List.class).invoke(gui,List.of(focus));stage=2;frames=0;
            }
        }catch(Throwable failure){finish(failure);}
    }
    private void render(ScreenEvent.Render.Post event){
        var mc=Minecraft.getInstance();if(stage==99)return;
        try {
            if(stage==2&&++frames>30){
                proof.put("recipeScreen",mc.screen.getClass().getName());
                if(!mc.screen.getClass().getName().contains("RecipesGui"))throw new IllegalStateException("JEI did not open original ingot recipe");
                saveImage("recipe-ingots-jei.png");mc.setScreen(new Comparison());stage=3;frames=0;
            }else if(stage==3&&++frames>10){saveImage("recipe-ingots-comparison.png");((Comparison)mc.screen).verify(event.getGuiGraphics());finish(null);}
        }catch(Throwable failure){finish(failure);}
    }
    private void saveImage(String file)throws Exception{var mc=Minecraft.getInstance();var path=mc.gameDirectory.toPath().resolve("screenshots/"+file);java.nio.file.Files.createDirectories(path.getParent());try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}}
    private final class Comparison extends Screen {
        private final List<ItemStack> stacks=new ArrayList<>();
        private Object renderer;
        private java.lang.reflect.Method single,batch;
        private java.lang.reflect.Constructor<?> element;
        Comparison(){super(Component.literal("Native / JEI recipe / JEI list"));}
        @Override protected void init(){try{
            renderer=Class.forName("mezz.jei.library.render.ItemStackRenderer").getConstructor().newInstance();single=renderer.getClass().getMethod("render",GuiGraphics.class,ItemStack.class,int.class,int.class);batch=renderer.getClass().getMethod("renderBatch",GuiGraphics.class,List.class);element=Class.forName("mezz.jei.api.ingredients.rendering.BatchRenderElement").getConstructor(Object.class,int.class,int.class);
            stacks.add(new ItemStack(Items.IRON_INGOT));stacks.add(new ItemStack(Items.GOLD_INGOT));
            for(var item:content.declarations().items())if(item.key().toLowerCase(Locale.ROOT).contains("ingot"))for(var variant:item.variants())stacks.add(content.stack(item.key(),variant.metadata(),1,""));
            for(var old:content.services().items().catalog())if(old.key().toLowerCase(Locale.ROOT).contains("unstableingot")&&old.metadata()==0&&!old.tag().isEmpty())stacks.add(content.stack(old.key(),old.metadata(),1,old.tag()));
        }catch(Exception failure){throw new IllegalStateException(failure);}}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){try{
            graphics.flush();com.mojang.blaze3d.systems.RenderSystem.clear(256,Minecraft.ON_OSX);graphics.fill(0,0,width,height,0xff404040);graphics.flush();
            graphics.drawString(font,title,8,8,0xffffff);graphics.drawString(font,"Native        JEI single     JEI batch",8,23,0xffffff);
            for(int index=0;index<stacks.size();index++){
                var stack=stacks.get(index);int y=40+index*23;
                graphics.renderItem(stack,12,y);single.invoke(renderer,graphics,stack,82,y);batch.invoke(renderer,graphics,List.of(element.newInstance(stack,152,y)));
                graphics.drawString(font,stack.getHoverName(),180,y+3,0xffffff);
            }
            graphics.flush();
        }catch(Exception failure){finish(failure);}}
        void verify(GuiGraphics graphics)throws Exception{
            var results=new ArrayList<Map<String,Object>>();
            try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
                double sx=(double)image.getWidth()/graphics.guiWidth(),sy=(double)image.getHeight()/graphics.guiHeight();
                for(int row=0;row<stacks.size();row++){
                    var stack=stacks.get(row);var result=new LinkedHashMap<String,Object>();result.put("stack",stack.toString());var old=content.renderSnapshot(stack);if(old!=null){result.put("metadata",old.metadata());result.put("originalTag",old.tag());}
                    for(int mode=0;mode<3;mode++){
                        int visible=0,missing=0;
                        for(int py=(int)((40+row*23)*sy);py<(int)((56+row*23)*sy);py++)for(int px=(int)((12+mode*70)*sx);px<(int)((28+mode*70)*sx);px++){
                            int pixel=image.getPixelRGBA(px,py),r=pixel&255,g=pixel>>8&255,b=pixel>>16&255;
                            if(Math.abs(r-64)+Math.abs(g-64)+Math.abs(b-64)>30)visible++;
                            if(r>110&&b>110&&g<50)missing++;
                        }
                        String name=List.of("native","jeiSingle","jeiBatch").get(mode);result.put(name+"Pixels",visible);
                        if(visible<8||missing>0)throw new IllegalStateException("Missing ingot icon: "+name+" "+stack+" pixels="+visible+" missing="+missing);
                    }
                    results.add(result);
                }
            }
            proof.put("renderedIngots",results);
        }
    }
    private void finish(Throwable failure){if(stage==99)return;stage=99;var mc=Minecraft.getInstance();proof.put("status",failure==null?"PASS":"FAIL");if(failure!=null){proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_RECIPE_ITEMS_FAIL",failure);}try{var path=mc.gameDirectory.toPath().resolve("logs/mfcl/recipe-items-smoke.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception problem){throw new IllegalStateException(problem);}if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();}
}
