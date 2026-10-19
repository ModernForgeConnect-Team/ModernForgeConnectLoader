package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientContentBridge {
    private ClientContentBridge() {}
    public static void install(ModernContentBridge bridge, IEventBus bus) {
        LegacyScanNotifications.install(bridge);
        bus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(bridge.tileType(), context -> new LegacyBlockEntityRenderer(bridge)));
        bus.addListener((net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) -> {
            for (var declaration : bridge.declarations().blocks()) for (var state : bridge.blocks().get(declaration.key()).getStateDefinition().getPossibleStates()) {
                int metadata = state.getValue(ModernContentBridge.METADATA);
                if (!bridge.blockRendering().supports(declaration.key(), metadata)) continue;
                var location = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(ModernContentBridge.id(declaration.key()), state);
                event.getModels().computeIfPresent(location, (id, original) -> new LegacyBlockModel(original, bridge, declaration.key(), metadata));
            }
        });
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            for (var entry : bridge.declarations().items()) ItemProperties.register(bridge.items().get(entry.key()), ResourceLocation.fromNamespaceAndPath("mfcl", "metadata"),
                    (stack, level, entity, seed) -> ModernContentBridge.metadata(stack));
        }));
        bus.addListener((net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) -> {
            for (var entry : bridge.declarations().items()) event.register((stack, tint) -> {
                try { return bridge.services().items().itemColor(bridge.renderSnapshot(stack), tint); }
                catch (Exception failure) { throw new IllegalStateException("Cannot evaluate original item tint: " + entry.key(), failure); }
            }, bridge.items().get(entry.key()));
        });
        if (Boolean.getBoolean("mfcl.test.scanner")) ScannerHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.book")) BookHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.logic")) LogicHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.content")) ContentHostSmokeProbe.install(bridge);
    }
}
