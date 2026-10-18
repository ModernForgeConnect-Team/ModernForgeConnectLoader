package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;

/** Runs the original Forge helper around each IItemRenderer, in the caller's documented coordinate frame. */
public final class LegacyItemRendering {
    public static final String PLAYER_SKIN = "mfcl:textures/bridge/player_skin.png";
    private static final ThreadLocal<Boolean> CAPTURING_PLAYER = new ThreadLocal<>();
    private final ClassLoader loader;
    private final Class<?> stackType, itemType, renderType, rendererType, blocksType, entityType, helperType;
    private final Object client, player, blocks, textures;
    private final Method helper, inventory, equipped, entity;

    public LegacyItemRendering(ClassLoader loader) throws Exception {
        this.loader = loader;
        stackType = loader.loadClass("net.minecraft.item.ItemStack"); itemType = loader.loadClass("net.minecraft.item.Item");
        renderType = loader.loadClass("net.minecraftforge.client.IItemRenderer$ItemRenderType");
        rendererType = loader.loadClass("net.minecraftforge.client.IItemRenderer");
        helperType = loader.loadClass("net.minecraftforge.client.IItemRenderer$ItemRendererHelper");
        blocksType = loader.loadClass("net.minecraft.client.renderer.RenderBlocks");
        entityType = loader.loadClass("net.minecraft.entity.EntityLivingBase");
        var mc = loader.loadClass("net.minecraft.client.Minecraft"); client = mc.getMethod("func_71410_x").invoke(null);
        player = mc.getField("field_71439_g").get(client); textures = mc.getField("field_71446_o").get(client);
        blocks = blocksType.getConstructor().newInstance();
        helper = rendererType.getMethod("shouldUseRenderHelper",renderType,stackType,helperType);
        var hooks = loader.loadClass("net.minecraftforge.client.ForgeHooksClient");
        inventory = hooks.getMethod("renderInventoryItem",blocksType,loader.loadClass("net.minecraft.client.renderer.texture.TextureManager"),stackType,boolean.class,float.class,float.class,float.class);
        equipped = hooks.getMethod("renderEquippedItem",renderType,rendererType,blocksType,entityType,stackType);
        entity = hooks.getMethod("renderEntityItem",loader.loadClass("net.minecraft.entity.item.EntityItem"),stackType,float.class,float.class,java.util.Random.class,loader.loadClass("net.minecraft.client.renderer.texture.TextureManager"),blocksType,int.class);
    }

    /** Called only by the private AbstractClientPlayer adapter; native replay supplies the current player's skin. */
    public static Object playerSkin(Object player) throws Exception {
        if (!Boolean.TRUE.equals(CAPTURING_PLAYER.get())) return null;
        return player.getClass().getClassLoader().loadClass("net.minecraft.util.ResourceLocation").getConstructor(String.class).newInstance(PLAYER_SKIN);
    }

    public void render(Object type, Object renderer, Object stack) throws Exception {
        String name = ((Enum<?>)type).name();
        switch (name) {
            case "INVENTORY" -> {
                // Forge uses GUI pixels with Y down. The modern item renderer uses centered, Y-up model units.
                LegacyWorldGraphics.translate(-.5,.5,3.0/16);
                LegacyWorldGraphics.scale(1.0/16,-1.0/16,1.0/16);
                inventory.invoke(null,blocks,textures,stack,true,0f,0f,0f);
            }
            case "EQUIPPED" -> {
                thirdPerson(type,renderer,stack);
                equipped.invoke(null,type,renderer,blocks,player,stack);
            }
            case "EQUIPPED_FIRST_PERSON" -> {
                // ItemRenderer.func_78440_a, idle pose: the host supplies the grip translation and swing.
                LegacyWorldGraphics.rotate(45,0,1,0); LegacyWorldGraphics.scale(.4,.4,.4);
                CAPTURING_PLAYER.set(true);
                try { equipped.invoke(null,type,renderer,blocks,player,stack); }
                finally { CAPTURING_PLAYER.remove(); }
            }
            case "ENTITY" -> {
                var world = loader.loadClass("net.minecraft.world.World");
                Object oldWorld = loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p").get(player);
                Object dropped = loader.loadClass("net.minecraft.entity.item.EntityItem").getConstructor(world,double.class,double.class,double.class,stackType).newInstance(oldWorld,0d,0d,0d,stack);
                entity.invoke(null,dropped,stack,0f,0f,new java.util.Random(187),textures,blocks,1);
            }
            default -> throw new IllegalArgumentException(name);
        }
    }

    private void thirdPerson(Object type, Object renderer, Object stack) throws Exception {
        // RenderPlayer.func_77029_c, starting immediately after rightArm.postRender(1/16).
        LegacyWorldGraphics.translate(-.0625,.4375,.0625);
        Object item = stackType.getMethod("func_77973_b").invoke(stack);
        boolean is3d = (boolean)helper.invoke(renderer,type,stack,helperType.getField("BLOCK_3D").get(null));
        if (!is3d && loader.loadClass("net.minecraft.item.ItemBlock").isInstance(item)) {
            var blockType = loader.loadClass("net.minecraft.block.Block");
            Object block = blockType.getMethod("func_149634_a",itemType).invoke(null,item);
            is3d = (boolean)blocksType.getMethod("func_147739_a",int.class).invoke(null,blockType.getMethod("func_149645_b").invoke(block));
        }
        if (is3d) {
            LegacyWorldGraphics.translate(0,.1875,-.3125);
            LegacyWorldGraphics.rotate(20,1,0,0); LegacyWorldGraphics.rotate(45,0,1,0);
            LegacyWorldGraphics.scale(-.375,-.375,.375);
        } else if ((boolean)itemType.getMethod("func_77662_d").invoke(item)) {
            if ((boolean)itemType.getMethod("func_77629_n_").invoke(item)) {
                LegacyWorldGraphics.rotate(180,0,0,1); LegacyWorldGraphics.translate(0,-.125,0);
            }
            LegacyWorldGraphics.translate(0,.1875,0); LegacyWorldGraphics.scale(.625,-.625,.625);
            LegacyWorldGraphics.rotate(-100,1,0,0); LegacyWorldGraphics.rotate(45,0,1,0);
        } else {
            LegacyWorldGraphics.translate(.25,.1875,-.1875); LegacyWorldGraphics.scale(.375,.375,.375);
            LegacyWorldGraphics.rotate(60,0,0,1); LegacyWorldGraphics.rotate(-90,1,0,0); LegacyWorldGraphics.rotate(20,0,0,1);
        }
    }
}
