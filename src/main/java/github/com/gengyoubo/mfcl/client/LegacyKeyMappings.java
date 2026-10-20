package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import java.util.*;

/** Original ClientRegistry bindings as rebindable host key mappings; host input drives the original KeyBinding objects. */
public final class LegacyKeyMappings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, KeyMapping> BY_DESCRIPTION = new LinkedHashMap<>();
    private record Pair(LegacyKeyBindings.Binding legacy, KeyMapping host) {}
    private final LegacyServices services;
    private final LegacyKeyBindings bindings;
    private final List<Pair> pairs = new ArrayList<>();
    private final Set<String> reported = new HashSet<>();
    private LegacyKeyMappings(LegacyServices services, LegacyKeyBindings bindings) { this.services = services; this.bindings = bindings; }
    public static void install(LegacyServices services, IEventBus bus) {
        LegacyKeyMappings bridge;
        try {
            bridge = new LegacyKeyMappings(services, new LegacyKeyBindings(services));
            for (var legacy : bridge.bindings.modBindings()) {
                // Old descriptions and categories stay the mapping's names, so they share host categories such as key.categories.misc.
                var key = hostKey(legacy.defaultCode());
                var host = new KeyMapping(legacy.description(), KeyConflictContext.IN_GAME, key.getType(), key.getValue(), legacy.category());
                bridge.pairs.add(new Pair(legacy, host));
                BY_DESCRIPTION.putIfAbsent(legacy.description(), host);
            }
        } catch (Exception failure) { throw new IllegalStateException("Cannot read original key bindings", failure); }
        if (bridge.pairs.isEmpty()) return;
        bus.addListener((RegisterKeyMappingsEvent event) -> bridge.pairs.forEach(pair -> event.register(pair.host())));
        // Registered after the host LanguageManager, so this runs after each language reload has injected its table.
        bus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener((ResourceManagerReloadListener) manager -> bridge.names()));
        MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    /** The host mapping of an original binding by its original description; null when no loaded mod registered one. */
    public static KeyMapping find(String description) { return BY_DESCRIPTION.get(description); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        boolean focus = mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive();
        var states = new ArrayList<LegacyKeyBindings.State>(pairs.size());
        for (var pair : pairs) {
            int presses = 0;
            while (pair.host().consumeClick()) presses++;
            states.add(new LegacyKeyBindings.State(pair.legacy(), legacyCode(pair.host().getKey()), pair.host().isDown(), presses));
        }
        try {
            if (!bindings.apply(states, focus) || !focus) return;
            prepareActor(mc);
            bindings.postKeyInput();
        } catch (Exception failure) {
            // Old handlers may reach services the host does not connect; report each distinct failure once, keep input alive.
            if (reported.add(String.valueOf(failure.getCause() == null ? failure : failure.getCause())))
                LOGGER.error("MFCL original key input failed", failure);
        }
    }
    /** Old KeyInputEvent handlers read Minecraft.thePlayer; bind it to the host player like item use does. */
    private void prepareActor(Minecraft mc) throws Exception {
        var content = ModernContentBridge.active();
        if (content == null) return;
        var player = mc.player;
        services.items().inLegacy(() -> content.itemUse().clientActor(ClientWorldBridge.world(services), new LegacyTiles.Placer(player.getUUID(), player.getName().getString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.getAbilities().instabuild, player.isShiftKeyDown()), null));
    }
    private void names() {
        var mc = Minecraft.getInstance();
        var keys = new LinkedHashSet<String>();
        for (var pair : pairs) { keys.add(pair.legacy().description()); keys.add(pair.legacy().category()); }
        try {
            var names = bindings.translate(mc.options == null ? "en_us" : mc.options.languageCode, keys);
            var current = Language.getInstance();
            Language.inject(new OriginalNames(current instanceof OriginalNames wrapped ? wrapped.host : current, names));
        } catch (Exception failure) { LOGGER.error("MFCL cannot translate original key binding names", failure); }
    }
    /** Host translations win; original translations only fill the names of bridged bindings the host does not define. */
    private static final class OriginalNames extends Language {
        private final Language host;
        private final Map<String, String> names;
        OriginalNames(Language host, Map<String, String> names) { this.host = host; this.names = names; }
        @Override public String getOrDefault(String key, String fallback) {
            if (host.has(key)) return host.getOrDefault(key, fallback);
            var name = names.get(key);
            return name != null ? name : host.getOrDefault(key, fallback);
        }
        @Override public boolean has(String key) { return host.has(key) || names.containsKey(key); }
        @Override public boolean isDefaultRightToLeft() { return host.isDefaultRightToLeft(); }
        @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return host.getVisualOrder(text); }
    }

    // Old LWJGL2 Keyboard codes (DirectInput scan codes) paired with GLFW key tokens.
    private static final int[] CODES = {
            1, GLFW.GLFW_KEY_ESCAPE, 2, GLFW.GLFW_KEY_1, 3, GLFW.GLFW_KEY_2, 4, GLFW.GLFW_KEY_3, 5, GLFW.GLFW_KEY_4, 6, GLFW.GLFW_KEY_5,
            7, GLFW.GLFW_KEY_6, 8, GLFW.GLFW_KEY_7, 9, GLFW.GLFW_KEY_8, 10, GLFW.GLFW_KEY_9, 11, GLFW.GLFW_KEY_0, 12, GLFW.GLFW_KEY_MINUS,
            13, GLFW.GLFW_KEY_EQUAL, 14, GLFW.GLFW_KEY_BACKSPACE, 15, GLFW.GLFW_KEY_TAB, 16, GLFW.GLFW_KEY_Q, 17, GLFW.GLFW_KEY_W,
            18, GLFW.GLFW_KEY_E, 19, GLFW.GLFW_KEY_R, 20, GLFW.GLFW_KEY_T, 21, GLFW.GLFW_KEY_Y, 22, GLFW.GLFW_KEY_U, 23, GLFW.GLFW_KEY_I,
            24, GLFW.GLFW_KEY_O, 25, GLFW.GLFW_KEY_P, 26, GLFW.GLFW_KEY_LEFT_BRACKET, 27, GLFW.GLFW_KEY_RIGHT_BRACKET, 28, GLFW.GLFW_KEY_ENTER,
            29, GLFW.GLFW_KEY_LEFT_CONTROL, 30, GLFW.GLFW_KEY_A, 31, GLFW.GLFW_KEY_S, 32, GLFW.GLFW_KEY_D, 33, GLFW.GLFW_KEY_F,
            34, GLFW.GLFW_KEY_G, 35, GLFW.GLFW_KEY_H, 36, GLFW.GLFW_KEY_J, 37, GLFW.GLFW_KEY_K, 38, GLFW.GLFW_KEY_L, 39, GLFW.GLFW_KEY_SEMICOLON,
            40, GLFW.GLFW_KEY_APOSTROPHE, 41, GLFW.GLFW_KEY_GRAVE_ACCENT, 42, GLFW.GLFW_KEY_LEFT_SHIFT, 43, GLFW.GLFW_KEY_BACKSLASH,
            44, GLFW.GLFW_KEY_Z, 45, GLFW.GLFW_KEY_X, 46, GLFW.GLFW_KEY_C, 47, GLFW.GLFW_KEY_V, 48, GLFW.GLFW_KEY_B, 49, GLFW.GLFW_KEY_N,
            50, GLFW.GLFW_KEY_M, 51, GLFW.GLFW_KEY_COMMA, 52, GLFW.GLFW_KEY_PERIOD, 53, GLFW.GLFW_KEY_SLASH, 54, GLFW.GLFW_KEY_RIGHT_SHIFT,
            55, GLFW.GLFW_KEY_KP_MULTIPLY, 56, GLFW.GLFW_KEY_LEFT_ALT, 57, GLFW.GLFW_KEY_SPACE, 58, GLFW.GLFW_KEY_CAPS_LOCK,
            59, GLFW.GLFW_KEY_F1, 60, GLFW.GLFW_KEY_F2, 61, GLFW.GLFW_KEY_F3, 62, GLFW.GLFW_KEY_F4, 63, GLFW.GLFW_KEY_F5, 64, GLFW.GLFW_KEY_F6,
            65, GLFW.GLFW_KEY_F7, 66, GLFW.GLFW_KEY_F8, 67, GLFW.GLFW_KEY_F9, 68, GLFW.GLFW_KEY_F10, 69, GLFW.GLFW_KEY_NUM_LOCK,
            70, GLFW.GLFW_KEY_SCROLL_LOCK, 71, GLFW.GLFW_KEY_KP_7, 72, GLFW.GLFW_KEY_KP_8, 73, GLFW.GLFW_KEY_KP_9, 74, GLFW.GLFW_KEY_KP_SUBTRACT,
            75, GLFW.GLFW_KEY_KP_4, 76, GLFW.GLFW_KEY_KP_5, 77, GLFW.GLFW_KEY_KP_6, 78, GLFW.GLFW_KEY_KP_ADD, 79, GLFW.GLFW_KEY_KP_1,
            80, GLFW.GLFW_KEY_KP_2, 81, GLFW.GLFW_KEY_KP_3, 82, GLFW.GLFW_KEY_KP_0, 83, GLFW.GLFW_KEY_KP_DECIMAL, 87, GLFW.GLFW_KEY_F11,
            88, GLFW.GLFW_KEY_F12, 100, GLFW.GLFW_KEY_F13, 101, GLFW.GLFW_KEY_F14, 102, GLFW.GLFW_KEY_F15, 141, GLFW.GLFW_KEY_KP_EQUAL,
            156, GLFW.GLFW_KEY_KP_ENTER, 157, GLFW.GLFW_KEY_RIGHT_CONTROL, 181, GLFW.GLFW_KEY_KP_DIVIDE, 183, GLFW.GLFW_KEY_PRINT_SCREEN,
            184, GLFW.GLFW_KEY_RIGHT_ALT, 197, GLFW.GLFW_KEY_PAUSE, 199, GLFW.GLFW_KEY_HOME, 200, GLFW.GLFW_KEY_UP, 201, GLFW.GLFW_KEY_PAGE_UP,
            203, GLFW.GLFW_KEY_LEFT, 205, GLFW.GLFW_KEY_RIGHT, 207, GLFW.GLFW_KEY_END, 208, GLFW.GLFW_KEY_DOWN, 209, GLFW.GLFW_KEY_PAGE_DOWN,
            210, GLFW.GLFW_KEY_INSERT, 211, GLFW.GLFW_KEY_DELETE, 219, GLFW.GLFW_KEY_LEFT_SUPER, 220, GLFW.GLFW_KEY_RIGHT_SUPER, 221, GLFW.GLFW_KEY_MENU};
    private static final Map<Integer, Integer> TO_HOST = new HashMap<>(), TO_LEGACY = new HashMap<>();
    static { for (int i = 0; i < CODES.length; i += 2) { TO_HOST.put(CODES[i], CODES[i + 1]); TO_LEGACY.put(CODES[i + 1], CODES[i]); } }
    static InputConstants.Key hostKey(int legacy) {
        if (legacy < 0) return InputConstants.Type.MOUSE.getOrCreate(legacy + 100);
        return InputConstants.Type.KEYSYM.getOrCreate(TO_HOST.getOrDefault(legacy, GLFW.GLFW_KEY_UNKNOWN));
    }
    static int legacyCode(InputConstants.Key key) {
        return switch (key.getType()) {
            case MOUSE -> key.getValue() - 100;
            case KEYSYM -> TO_LEGACY.getOrDefault(key.getValue(), 0);
            // Old LWJGL has no code for keys the host binds by scancode; 0 is KEY_NONE.
            case SCANCODE -> 0;
        };
    }
}
