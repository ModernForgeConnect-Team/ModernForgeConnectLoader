package github.com.gengyoubo.mfcl.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Retains legacy GL_COMPILE commands during initialization, scoped to the private GL11 class identity. */
public final class LegacyDisplayLists {
    public record Command(String operation, List<Double> arguments) {
        public Command { arguments = List.copyOf(arguments); }
    }
    private static final class Domain {
        final AtomicInteger next = new AtomicInteger(1);
        final Map<Integer, List<Command>> lists = new ConcurrentHashMap<>();
        final ThreadLocal<Recording> recording = new ThreadLocal<>();
    }
    private static final class Recording {
        final int id;
        final List<Command> commands = new ArrayList<>();
        boolean primitive;
        Recording(int id) { this.id = id; }
    }
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
        domain.recording.set(new Recording(id));
    }
    public static void end(Class<?> owner) {
        Domain domain = DOMAINS.get(owner);
        Recording recording = domain.recording.get();
        if (recording == null) throw new IllegalStateException("No display list compilation");
        if (recording.primitive) throw new IllegalStateException("Unclosed display list primitive");
        domain.lists.put(recording.id, List.copyOf(recording.commands));
        domain.recording.remove();
    }
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
        if (recording.commands.size() >= 1_000_000) throw new IllegalStateException("Display list command limit exceeded");
        var values = new ArrayList<Double>(arguments.length);
        for (double value : arguments) values.add(value);
        recording.commands.add(new Command(operation, values));
        return true;
    }
    public static void call(Class<?> owner, int id) {
        if (record(owner, "glCallList(I)V", new double[]{id})) return;
        List<Command> commands = DOMAINS.get(owner).lists.get(id);
        if (commands != null) {
            if (LegacyWorldGraphics.active()) { LegacyWorldGraphics.callList(commands); return; }
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
    public static List<Command> commands(Class<?> owner, int id) {
        return DOMAINS.get(owner).lists.getOrDefault(id, List.of());
    }
    public static Map<String, Integer> statistics(Class<?> owner) {
        var lists = DOMAINS.get(owner).lists;
        return Map.of("lists", lists.size(), "commands", lists.values().stream().mapToInt(List::size).sum());
    }
}
