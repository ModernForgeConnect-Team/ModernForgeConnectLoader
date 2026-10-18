package github.com.gengyoubo.mfcl.legacy;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoader;

import java.util.Optional;
import java.util.function.Consumer;

/** Adds the completed private runtime to Forge 47's startup notification, without changing ModList. */
public final class RuntimeModAccounting {
    private static volatile String completionMessage;

    private RuntimeModAccounting() { }

    public static void install(LegacyModCatalog catalog) {
        if (catalog.loadedCount() == 0) return;
        try {
            var loader = ModLoader.get();
            var status = ModLoader.class.getDeclaredField("statusConsumer");
            status.setAccessible(true);
            @SuppressWarnings("unchecked")
            var original = (Optional<Consumer<String>>) status.get(loader);
            status.set(loader, original.map(consumer -> (Consumer<String>) message -> {
                String combined = catalog.completionMessage(message);
                if (!combined.equals(message)) {
                    completionMessage = combined;
                    LogUtils.getLogger().info("MFCL: {}; {} loaded legacy mods for Minecraft {}",
                            combined, catalog.loadedCount(), catalog.sourceMinecraft());
                }
                consumer.accept(combined);
            }));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot integrate runtime counts with Forge 47 startup notifications", failure);
        }
    }

    public static String completionMessage() { return completionMessage; }
}
