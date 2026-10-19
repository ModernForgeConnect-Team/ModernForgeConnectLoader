package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Uses the real client world and player, so an old held renderer can query its current target and knowledge. */
final class LiveLegacyItemCapture {
    record Drawing(List<LegacyVertexStream.Primitive> primitives,List<LegacyWorldGraphics.Text> texts) {}
    static Drawing firstPerson(ModernContentBridge content,ItemStack stack) throws Exception {
        var services=content.services();var mc=Minecraft.getInstance();
        return services.items().inLegacy(()->{
            var loader=services.loader();var nativePlayer=mc.player;
            var placer=new LegacyTiles.Placer(nativePlayer.getUUID(),nativePlayer.getName().getString(),nativePlayer.getX(),nativePlayer.getY(),nativePlayer.getZ(),nativePlayer.getYRot(),nativePlayer.getXRot());
            Object actor=content.itemUse().clientActor(ClientWorldBridge.world(services),placer,content.renderSnapshot(stack));
            loader.loadClass("net.minecraft.entity.Entity").getField("field_70173_aa").setInt(actor,nativePlayer.tickCount);
            var clientType=loader.loadClass("net.minecraft.client.Minecraft");Object client=clientType.getMethod("func_71410_x").invoke(null);
            Object settings=clientType.getField("field_71474_y").get(client);settings.getClass().getField("field_74320_O").setInt(settings,0);
            Object game=clientType.getField("field_71460_t").get(client),held=game.getClass().getField("field_78516_c").get(game);
            var equip=held.getClass().getDeclaredField("field_78454_c");equip.setAccessible(true);var previous=held.getClass().getDeclaredField("field_78451_d");previous.setAccessible(true);
            float before=equip.getFloat(held),beforePrevious=previous.getFloat(held);equip.setFloat(held,1);previous.setFloat(held,1);
            try(var frame=LegacyWorldGraphics.begin()) {
                Object old=services.items().materialize(content.renderSnapshot(stack));var type=loader.loadClass("net.minecraftforge.client.IItemRenderer$ItemRenderType");Object context=type.getField("EQUIPPED_FIRST_PERSON").get(null);
                Object renderer=loader.loadClass("net.minecraftforge.client.MinecraftForgeClient").getMethod("getItemRenderer",loader.loadClass("net.minecraft.item.ItemStack"),type).invoke(null,old,context);
                new LegacyItemRendering(loader).render(context,renderer,old);
                return new Drawing(frame.primitives(),frame.texts());
            } finally {equip.setFloat(held,before);previous.setFloat(held,beforePrevious);}
        });
    }
}
