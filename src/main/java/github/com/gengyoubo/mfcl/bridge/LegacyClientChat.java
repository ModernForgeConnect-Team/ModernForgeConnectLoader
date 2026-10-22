package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Output from the private old chat HUD, delivered on the host client thread. */
public final class LegacyClientChat {
    public record Message(String text, int lineId, boolean deletion) {
        public Message(String text, int lineId) { this(text, lineId, false); }
    }
    private static final Map<ClassLoader,Deque<Message>> PENDING = new WeakHashMap<>();
    private LegacyClientChat() {}
    public static void message(Object component, int lineId) throws Exception {
        ClassLoader loader = component.getClass().getClassLoader();
        String text = (String)loader.loadClass("net.minecraft.util.IChatComponent")
                .getMethod("func_150254_d").invoke(component);
        enqueue(loader, new Message(text, lineId));
    }
    public static void delete(Object chat, int lineId) {
        enqueue(chat.getClass().getClassLoader(), new Message("", lineId, true));
    }
    private static void enqueue(ClassLoader loader, Message message) {
        synchronized (PENDING) {
            var queue = PENDING.computeIfAbsent(loader, ignored -> new ArrayDeque<>());
            if (queue.size() == 100) queue.removeFirst();
            queue.addLast(message);
        }
    }
    public static List<Message> drain(ClassLoader loader) {
        synchronized (PENDING) {
            var queue = PENDING.remove(loader);
            return queue == null ? List.of() : List.copyOf(queue);
        }
    }
}
