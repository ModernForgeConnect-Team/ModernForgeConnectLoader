package github.com.gengyoubo.mfcl.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retains legacy GL_COMPILE lists as an ordered event stream, scoped to the private GL11 class identity.
 * Immediate-mode calls are kept as commands; Tessellator draws are kept as local-space geometry. Replaying the
 * stream in order preserves the original matrix, texture and colour sequence, as real glCallList does.
 */
public final class LegacyDisplayLists {
    public sealed interface Entry permits CommandEntry, GeometryEntry {}
    public record CommandEntry(String operation, List<Double> arguments) implements Entry {
        public CommandEntry { arguments = List.copyOf(arguments); }
    }
    public record GeometryEntry(LegacyVertexStream.Primitive primitive, boolean inheritTexture,
                                boolean inheritColor, boolean inheritNormal) implements Entry {}
    private static final class Recording {
        final int id;
        final List<Entry> entries = new ArrayList<>();
        boolean primitive;
        Recording(int id) { this.id = id; }
    }
    private static final class Domain {
        final AtomicInteger next = new AtomicInteger(1);
        final Map<Integer, List<Entry>> lists = new ConcurrentHashMap<>();
        final ThreadLocal<Recording> recording = new ThreadLocal<>();
    }
    private static final ThreadLocal<Recording> ACTIVE = new ThreadLocal<>();
    private static final ClassValue<Domain> DOMAINS = new ClassValue<>() {
        @Override protected Domain computeValue(Class<?> type) { return new Domain(); }
    };
    private LegacyDisplayLists() { }

    public static int generate(Class<?> owner, int range) {
        if (range < 0) throw new IllegalArgumentException("Negative display list range");
        if (range == 0) return 0;
        return DOMAINS.get(owner).next.getAndUpdate(next -> Math.addExact(next, range));
    }
    public static void begin(Class<?> owner, int id, int mode) {
        if (id <= 0) throw new IllegalArgumentException("Invalid display list ID");
        if (mode != 4864) throw new UnsupportedOperationException("Only GL_COMPILE is available without a host world renderer");
        Domain domain = DOMAINS.get(owner);
        if (domain.recording.get() != null) throw new IllegalStateException("Nested display list compilation");
        Recording recording = new Recording(id);
        domain.recording.set(recording);
        ACTIVE.set(recording);
    }
    public static void end(Class<?> owner) {
        Domain domain = DOMAINS.get(owner);
        Recording recording = domain.recording.get();
        if (recording == null) throw new IllegalStateException("No display list compilation");
        if (recording.primitive) throw new IllegalStateException("Unclosed display list primitive");
        domain.lists.put(recording.id, List.copyOf(recording.entries));
        domain.recording.remove();
        ACTIVE.remove();
    }
    /** True while a GL_COMPILE list is open; the capture then records instead of executing. */
    public static boolean compiling() { return ACTIVE.get() != null; }
    /** False means this call belongs to the immediate GUI/world renderer instead. */
    public static boolean record(Class<?> owner, String operation, double[] arguments) {
        Recording recording = DOMAINS.get(owner).recording.get();
        if (recording == null) return false;
        if (operation.equals("glBegin(I)V")) {
            if (recording.primitive) throw new IllegalStateException("Nested display list primitive");
            recording.primitive = true;
        } else if (operation.equals("glEnd()V")) {
            if (!recording.primitive) throw new IllegalStateException("Display list primitive was not started");
            recording.primitive = false;
        } else if (operation.startsWith("glVertex") && !recording.primitive) {
            throw new IllegalStateException("Display list vertex outside a primitive");
        }
        if (recording.entries.size() >= 1_000_000) throw new IllegalStateException("Display list command limit exceeded");
        var values = new ArrayList<Double>(arguments.length);
        for (double value : arguments) values.add(value);
        recording.entries.add(new CommandEntry(operation, values));
        return true;
    }
    /** Records a Tessellator draw as local-space geometry inside the open list. */
    public static void geometry(LegacyVertexStream.Primitive primitive) {
        geometry(primitive, false, false, false);
    }
    public static void geometry(LegacyVertexStream.Primitive primitive, boolean inheritTexture,
                                boolean inheritColor, boolean inheritNormal) {
        Recording recording = ACTIVE.get();
        if (recording != null) recording.entries.add(new GeometryEntry(primitive, inheritTexture, inheritColor, inheritNormal));
    }
    public static void call(Class<?> owner, int id) {
        if (record(owner, "glCallList(I)V", new double[]{id})) return; // nested call is recorded, not expanded
        List<Entry> entries = DOMAINS.get(owner).lists.get(id);
        if (entries != null) {
            if (LegacyWorldGraphics.active()) { LegacyWorldGraphics.replayEntries(owner, entries); return; }
            throw new UnsupportedOperationException("Legacy display list " + id + " requires a connected host world renderer");
        }
        // OpenGL ignores undefined display lists.
    }
    public static boolean exists(Class<?> owner, int id) { return DOMAINS.get(owner).lists.containsKey(id); }
    public static void delete(Class<?> owner, int first, int range) {
        if (range < 0) throw new IllegalArgumentException("Negative display list range");
        int limit = Math.addExact(first, range);
        DOMAINS.get(owner).lists.keySet().removeIf(id -> id >= first && id < limit);
    }
    public static List<Entry> entries(Class<?> owner, int id) {
        return DOMAINS.get(owner).lists.getOrDefault(id, List.of());
    }
    public static List<CommandEntry> commands(Class<?> owner, int id) {
        return entries(owner, id).stream().filter(CommandEntry.class::isInstance).map(CommandEntry.class::cast).toList();
    }
    public static Map<String, Integer> statistics(Class<?> owner) {
        var lists = DOMAINS.get(owner).lists;
        return Map.of("lists", lists.size(), "commands", lists.values().stream().mapToInt(List::size).sum());
    }
}
