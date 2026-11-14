package github.com.gengyoubo.mfcl.internal.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * MCP names of original members ({@code getCurrentArmor}), each mapped to every SRG name it stands for
 * ({@code func_82169_q}). Lookups by SRG name or by the names of unobfuscated mod code never need this table.
 */
public final class LegacyNames {
    public static final LegacyNames NONE = new LegacyNames(Map.of(), Map.of());
    private final Map<String, Set<String>> fields, methods;

    private LegacyNames(Map<String, Set<String>> fields, Map<String, Set<String>> methods) {
        this.fields = fields; this.methods = methods;
    }

    /** From MCP {@code fields.csv} and {@code methods.csv} ({@code searge,name,...}). */
    public static LegacyNames load(Path fieldsCsv, Path methodsCsv) throws IOException {
        return new LegacyNames(read(fieldsCsv), read(methodsCsv));
    }

    /** SRG name -> MCP name maps; for tests. */
    public static LegacyNames of(Map<String, String> fields, Map<String, String> methods) {
        return new LegacyNames(invert(fields), invert(methods));
    }

    private static Map<String, Set<String>> read(Path csv) throws IOException {
        var names = new HashMap<String, String>();
        var lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
            String[] columns = line.split(",", 3);
            if (columns.length >= 2 && !columns[0].isEmpty() && !columns[1].isEmpty()) names.put(columns[0], columns[1]);
        }
        return invert(names);
    }

    private static Map<String, Set<String>> invert(Map<String, String> srgToMcp) {
        var result = new HashMap<String, Set<String>>();
        srgToMcp.forEach((srg, mcp) -> result.computeIfAbsent(mcp, ignored -> new TreeSet<>()).add(srg));
        result.replaceAll((mcp, srg) -> Collections.unmodifiableSet(srg));
        return Collections.unmodifiableMap(result);
    }

    public boolean isEmpty() { return fields.isEmpty() && methods.isEmpty(); }

    /** {@code name} itself first, then every SRG method it is the MCP name of. */
    Set<String> methodCandidates(String name) { return candidates(name, methods); }
    /** {@code name} itself first, then every SRG field it is the MCP name of. */
    Set<String> fieldCandidates(String name) { return candidates(name, fields); }

    private static Set<String> candidates(String name, Map<String, Set<String>> table) {
        var result = new LinkedHashSet<String>();
        result.add(name);
        result.addAll(table.getOrDefault(name, Set.of()));
        return result;
    }
}
