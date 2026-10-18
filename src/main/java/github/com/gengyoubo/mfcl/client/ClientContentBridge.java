package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientContentBridge {
    private ClientContentBridge() {}
    public static void install(ModernContentBridge bridge, IEventBus bus) {
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            for (var entry : bridge.declarations().items()) ItemProperties.register(bridge.items().get(entry.key()), new ResourceLocation("mfcl", "metadata"),
                    (stack, level, entity, seed) -> ModernContentBridge.metadata(stack));
        }));
        bus.addListener((net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) -> {
            for (var entry : bridge.declarations().items()) event.register((stack, tint) -> entry.variants().stream()
                    .filter(variant -> variant.metadata() == ModernContentBridge.metadata(stack)).findFirst().orElse(entry.variants().get(0)).color(), bridge.items().get(entry.key()));
        });
        if (Boolean.getBoolean("mfcl.test.content")) ContentHostSmokeProbe.install(bridge);
    }
}
