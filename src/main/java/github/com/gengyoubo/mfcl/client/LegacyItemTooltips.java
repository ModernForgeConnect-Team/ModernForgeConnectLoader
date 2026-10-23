package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.*;

/** One original item presentation for native inventories, device slots and JEI. */
final class LegacyItemTooltips {
    private final ModernContentBridge content;
    private final Set<String> failures=new HashSet<>();
    private LegacyItemTooltips(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){
        var bridge=new LegacyItemTooltips(content);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH,(ItemTooltipEvent event)->bridge.tooltip(event));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event)->{if(event.phase==TickEvent.Phase.END)bridge.language();});
    }
    private void language(){
        try{content.services().items().language(Minecraft.getInstance().options.languageCode);}
        catch(Exception failure){throw new IllegalStateException("Cannot update original item language",failure);}
    }
    private void tooltip(ItemTooltipEvent event){
        var services=content.services();var mc=Minecraft.getInstance();
        try {
            var source=content.renderSnapshot(event.getItemStack());if(source==null)return;
            language();
            var lines=services.items().inLegacy(()->{
                services.containers().preparePlayer();
                if(mc.player!=null&&mc.level!=null)content.itemUse().clientActor(ClientWorldBridge.world(services),new LegacyTiles.Placer(mc.player.getUUID(),mc.player.getName().getString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot(),mc.player.getAbilities().instabuild,mc.player.isShiftKeyDown()),null);
                var type=services.loader().loadClass("net.minecraft.client.Minecraft");Object client=type.getMethod("func_71410_x").invoke(null);
                return services.items().tooltip(source,type.getField("field_71439_g").get(client),event.getFlags().isAdvanced());
            });
            var warnings=event.getToolTip().stream().filter(line->line.getContents() instanceof TranslatableContents translated&&translated.getKey().startsWith("mfcl.content.")).toList();
            event.getToolTip().clear();lines.forEach(line->event.getToolTip().add(Component.literal(line)));event.getToolTip().addAll(warnings);
            // Enchantments added after 1.7.10 have no numeric identity for the original tooltip to print.
            net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(event.getItemStack()).forEach((enchantment,level)->{
                var id=net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
                if(id!=null&&services.enchantments().id(id.toString())==null)event.getToolTip().add(enchantment.getFullname(level));
            });
            if(event.getFlags().isAdvanced())event.getToolTip().add(Component.literal("1.7.10 · "+source.key()+" @ "+source.metadata()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }catch(Exception failure){if(failures.add(event.getItemStack().getItem().toString()))com.mojang.logging.LogUtils.getLogger().warn("Cannot evaluate original item tooltip",failure);}
    }
}
