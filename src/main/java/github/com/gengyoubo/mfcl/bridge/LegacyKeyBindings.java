package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;
import java.util.*;

/** Original ClientRegistry key bindings. The host owns input; each tick it pushes key, held state and presses into the old objects. */
public final class LegacyKeyBindings {
    /** One binding a mod appended to GameSettings.keyBindings. Codes are old LWJGL2 codes (negative: mouse button - 100). */
    public record Binding(String description, String category, int defaultCode, Object handle) {}
    /** Host input for one binding during one client tick. */
    public record State(Binding binding, int code, boolean down, int presses) {}
    private final LegacyServices services;
    private final Class<?> type;
    private final Field keyCode, pressed, pressTime;
    public LegacyKeyBindings(LegacyServices services) throws Exception {
        this.services = services;
        type = services.loader().loadClass("net.minecraft.client.settings.KeyBinding");
        keyCode = accessible("field_74512_d"); pressed = accessible("field_74513_e"); pressTime = accessible("field_151474_i");
    }
    private Field accessible(String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private Object client() throws Exception {
        return services.loader().loadClass("net.minecraft.client.Minecraft").getMethod("func_71410_x").invoke(null);
    }
    /** Registration order of ClientRegistry.registerKeyBinding; bindings GameSettings declares itself are vanilla and excluded. */
    public List<Binding> modBindings() throws Exception {
        return services.items().inLegacy(() -> {
            Object client = client();
            Object settings = client.getClass().getField("field_71474_y").get(client);
            Set<Object> vanilla = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Field field : settings.getClass().getFields()) {
                if (field.getType() == type) vanilla.add(field.get(settings));
                // keyBindings itself is the full list; other arrays (hotbar) are vanilla declarations.
                else if (field.getType() == type.arrayType() && !field.getName().equals("field_74324_K")) vanilla.addAll(Arrays.asList((Object[]) field.get(settings)));
            }
            var result = new ArrayList<Binding>();
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object binding : (Object[]) settings.getClass().getField("field_74324_K").get(settings)) {
                if (binding == null || vanilla.contains(binding) || !seen.add(binding)) continue;
                result.add(new Binding((String) type.getMethod("func_151464_g").invoke(binding), (String) type.getMethod("func_151466_e").invoke(binding),
                        (int) type.getMethod("func_151469_h").invoke(binding), binding));
            }
            return List.copyOf(result);
        });
    }
    /**
     * Applies one tick of host input, the way the old Minecraft.runTick updates KeyBinding and inGameHasFocus.
     * @return whether any binding received a new press
     */
    public boolean apply(List<State> states, boolean inGameFocus) throws Exception {
        return services.items().inLegacy(() -> {
            boolean rebound = false, any = false;
            for (var state : states) {
                Object binding = state.binding().handle();
                if (keyCode.getInt(binding) != state.code()) { keyCode.setInt(binding, state.code()); rebound = true; }
                pressed.setBoolean(binding, inGameFocus && state.down());
                // Old unPressAllKeys clears queued presses whenever a screen takes focus.
                if (!inGameFocus) pressTime.setInt(binding, 0);
                else if (state.presses() > 0) { pressTime.setInt(binding, pressTime.getInt(binding) + state.presses()); any = true; }
            }
            // The old code -> binding hash is what KeyBinding.onTick/setKeyBindState consult.
            if (rebound) type.getMethod("func_74508_b").invoke(null);
            Object client = client();
            client.getClass().getField("field_71415_G").setBoolean(client, inGameFocus);
            return any;
        });
    }
    /** Posts the old FML InputEvent.KeyInputEvent that mods poll their bindings from. */
    public void postKeyInput() throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            Object bus = loader.loadClass("cpw.mods.fml.common.FMLCommonHandler").getMethod("bus").invoke(
                    loader.loadClass("cpw.mods.fml.common.FMLCommonHandler").getMethod("instance").invoke(null));
            Class<?> event = loader.loadClass("cpw.mods.fml.common.eventhandler.Event");
            bus.getClass().getMethod("post", event).invoke(bus, loader.loadClass("cpw.mods.fml.common.gameevent.InputEvent$KeyInputEvent").getConstructor().newInstance());
            return null;
        });
    }
    /** Old translations of the given keys in a host language; keys the old files leave undefined are omitted. */
    public Map<String, String> translate(String language, Collection<String> keys) throws Exception {
        services.items().language(language);
        return services.items().inLegacy(() -> {
            var format = services.loader().loadClass("net.minecraft.client.resources.I18n").getMethod("func_135052_a", String.class, Object[].class);
            var result = new HashMap<String, String>();
            for (String key : keys) {
                String text = (String) format.invoke(null, key, new Object[0]);
                if (text != null && !text.equals(key)) result.put(key, text);
            }
            return Map.copyOf(result);
        });
    }
}
