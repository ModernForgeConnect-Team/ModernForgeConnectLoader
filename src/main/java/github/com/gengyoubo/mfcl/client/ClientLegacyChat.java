package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.LegacyClientChat;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.lang.reflect.Field;
import java.util.*;

/** Keeps old optional-deletion IDs local to MFCL's own unsigned HUD messages. */
final class ClientLegacyChat {
    private final ClassLoader loader;
    private final Map<Integer,Component> numbered = new HashMap<>();
    private Field history;
    private ClientLegacyChat(LegacyServices services) { loader = services.loader(); }
    static void install(LegacyServices services) {
        var bridge = new ClientLegacyChat(services);
        MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    @SuppressWarnings("unchecked")
    private List<GuiMessage> history(ChatComponent chat) throws IllegalAccessException {
        if (history == null) history = ObfuscationReflectionHelper.findField(ChatComponent.class, "f_93760_");
        return (List<GuiMessage>)history.get(chat);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var pending = LegacyClientChat.drain(loader);
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { numbered.clear(); return; }
        if (pending.isEmpty()) return;
        display(mc.gui.getChat(), pending);
    }
    private void display(ChatComponent chat, List<LegacyClientChat.Message> pending) {
        try {
            for (var message : pending) {
                if (message.deletion()) {
                    Component previous = numbered.remove(message.lineId());
                    if (previous != null && history(chat).removeIf(line -> line.content() == previous)) chat.rescaleChat();
                    continue;
                }
                Component text = Component.literal(message.text());
                if (message.lineId() != 0) {
                    var messages = history(chat);
                    numbered.values().removeIf(previous -> messages.stream().noneMatch(line -> line.content() == previous));
                    Component previous = numbered.put(message.lineId(), text);
                    if (previous != null && messages.removeIf(line -> line.content() == previous)) chat.rescaleChat();
                }
                chat.addMessage(text);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot update original numbered chat messages", failure);
        }
    }
}
