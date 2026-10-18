package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Passive inspection only: no Class.forName, service providers, or mod initializers. */
public final class LegacyDiscovery {
    private static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    private static final Pattern MOD_REFERENCE = Pattern.compile("[A-Za-z0-9_.|\\-]+(?:@[^;\\s]+)?");

    public record Result(List<LegacyArchive> archives, List<LegacyDiagnostic> diagnostics) {
        public Result { archives = List.copyOf(archives); diagnostics = List.copyOf(diagnostics); }
    }

    public Result scan(LegacyPaths paths) throws IOException {
        Files.createDirectories(paths.modDirectory());
        List<LegacyArchive> archives = new ArrayList<>();
        List<LegacyDiagnostic> diagnostics = new ArrayList<>();
        List<Path> candidates;
        try (var files = Files.list(paths.modDirectory())) {
            candidates = files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        for (Path candidate : candidates) {
            try { archives.add(inspect(candidate)); }
            catch (IOException | RuntimeException failure) {
                diagnostics.add(LegacyDiagnostic.error("INVALID_ARCHIVE", candidate.getFileName() + ": " + failure.getMessage()));
            }
        }
        return new Result(archives, diagnostics);
    }

    public LegacyArchive inspect(Path path) throws IOException {
        List<LegacyMod> annotatedMods = new ArrayList<>();
        List<LegacyMod> metadataMods = new ArrayList<>();
        Set<String> classes = new LinkedHashSet<>();
        Set<String> requirements = new LinkedHashSet<>();
        Set<String> requiredClasses = new LinkedHashSet<>();
        Set<String> resources = new LinkedHashSet<>();
        Map<String, String> constantContainers = new LinkedHashMap<>();
        String corePlugin;
        List<String> ats = new ArrayList<>();
        String beforeHash = sha256(path);
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            var manifest = jar.getManifest();
            var attributes = manifest == null ? new java.util.jar.Attributes() : manifest.getMainAttributes();
            corePlugin = value(attributes.getValue("FMLCorePlugin"));
            String atList = value(attributes.getValue("FMLAT"));
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!resources.add(name)) throw new IOException("duplicate JAR entry " + name);
                if (name.equals("META-INF/mods.toml") || name.equals("fabric.mod.json") || name.equals("META-INF/neoforge.mods.toml")) {
                    throw new IOException("contains a modern loader descriptor; expected a Forge 1.7.10 archive");
                }
                if (name.endsWith(".class")) {
                    var reader = new ClassReader(read(jar, entry));
                    String className = reader.getClassName().replace('/', '.');
                    if (!classes.add(className)) throw new IOException("duplicate class " + className);
                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!descriptor.equals("Lcpw/mods/fml/common/Mod;")) return null;
                            Map<String, String> values = new LinkedHashMap<>();
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String key, Object v) { if (v instanceof String s) values.put(key, s); }
                                @Override public void visitEnd() {
                                    String id = values.getOrDefault("modid", "");
                                    if (id.isBlank()) throw new IllegalArgumentException("missing legacy @Mod.modid in " + className);
                                    List<String> required = requiredAnnotationMods(values.getOrDefault("dependencies", ""));
                                    requirements.addAll(required);
                                    annotatedMods.add(new LegacyMod(id, values.getOrDefault("name", id),
                                            values.getOrDefault("version", ""), values.getOrDefault("acceptedMinecraftVersions", ""),
                                            className, "@Mod", required));
                                }
                            };
                        }

                        @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                            if (!(method.equals("getModContainerClass") || method.equals("getRequirements"))) return null;
                            return new MethodVisitor(Opcodes.ASM9) {
                                String candidate;
                                boolean simple = true;
                                int constantCount;
                                @Override public void visitLdcInsn(Object constant) {
                                    if (constant instanceof String s) {
                                        candidate = s;
                                        constantCount++;
                                        if (method.equals("getRequirements") && s.contains("@") && MOD_REFERENCE.matcher(s).matches()) requirements.add(s);
                                    } else simple = false;
                                }
                                @Override public void visitVarInsn(int opcode, int var) { simple = false; }
                                @Override public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) { simple = false; }
                                @Override public void visitFieldInsn(int opcode, String owner, String n, String d) { simple = false; }
                                @Override public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) { simple = false; }
                                @Override public void visitInsn(int opcode) { if (opcode != Opcodes.ARETURN) simple = false; }
                                @Override public void visitEnd() {
                                    if (method.equals("getModContainerClass") && descriptor.equals("()Ljava/lang/String;")
                                            && simple && constantCount == 1 && candidate != null) constantContainers.put(className, candidate);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } else if (!name.contains("/") && name.endsWith(".info") && !name.equals("dependencies.info")) {
                    readMetadata(new String(read(jar, entry), StandardCharsets.UTF_8), name, metadataMods);
                } else if (name.equals("dependencies.info")) {
                    readLibraryRequirements(parse(new String(read(jar, entry), StandardCharsets.UTF_8)), requiredClasses);
                }
            }
            if (!corePlugin.isEmpty() && !classes.contains(corePlugin)) throw new IOException("FMLCorePlugin class is missing: " + corePlugin);
            for (String at : atList.isBlank() ? List.<String>of() : List.of(atList.trim().split("\\s+"))) {
                String resource = "META-INF/" + at;
                if (at.contains("..") || at.startsWith("/") || !resources.contains(resource)) throw new IOException("missing or invalid FMLAT resource " + resource);
                ats.add(resource);
            }
            String container = constantContainers.get(corePlugin);
            if (container != null) {
                var entry = jar.getJarEntry(container.replace('.', '/') + ".class");
                if (entry != null) {
                    LegacyMod coreMod = readContainerMetadata(read(jar, entry), container);
                    if (coreMod != null && metadataMods.stream().noneMatch(mod -> mod.id().equals(coreMod.id()))) metadataMods.add(coreMod);
                }
            }
        }
        if (!beforeHash.equals(sha256(path))) throw new IOException("archive changed during discovery");
        Map<String, LegacyMod> mods = new LinkedHashMap<>();
        for (LegacyMod mod : metadataMods) {
            if (mods.putIfAbsent(mod.id(), mod) != null) throw new IOException("duplicate metadata mod ID " + mod.id());
            requirements.addAll(mod.requiredMods());
        }
        for (LegacyMod mod : annotatedMods) {
            LegacyMod metadata = mods.get(mod.id());
            if (metadata != null && !metadata.entryClass().isEmpty()) throw new IOException("duplicate @Mod ID " + mod.id());
            mods.put(mod.id(), metadata == null ? mod : new LegacyMod(mod.id(),
                    mod.name().isBlank() ? metadata.name() : mod.name(), mod.version().isBlank() ? metadata.version() : mod.version(),
                    mod.minecraftVersion().isBlank() ? metadata.minecraftVersion() : mod.minecraftVersion(),
                    mod.entryClass(), metadata.metadataResource(), mod.requiredMods()));
        }
        return new LegacyArchive(path.toAbsolutePath().normalize().toString(), beforeHash, List.copyOf(mods.values()),
                corePlugin, constantContainers.getOrDefault(corePlugin, ""), ats, classes, List.copyOf(requirements), List.copyOf(requiredClasses));
    }

    /** Recognize literal ModMetadata factories in DummyModContainers without defining the plugin. */
    private static LegacyMod readContainerMetadata(byte[] bytes, String container) {
        var node = new org.objectweb.asm.tree.ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!node.superName.equals("cpw/mods/fml/common/DummyModContainer")) return null;
        for (var method : node.methods) {
            if (!method.desc.equals("()Lcpw/mods/fml/common/ModMetadata;")) continue;
            Map<String, String> fields = new LinkedHashMap<>();
            boolean straightLine = true;
            int allocations = 0;
            for (var insn : method.instructions) {
                if (insn instanceof org.objectweb.asm.tree.JumpInsnNode
                        || insn instanceof org.objectweb.asm.tree.TableSwitchInsnNode
                        || insn instanceof org.objectweb.asm.tree.LookupSwitchInsnNode) straightLine = false;
                if (insn instanceof org.objectweb.asm.tree.TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                        && type.desc.equals("cpw/mods/fml/common/ModMetadata")) allocations++;
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                        && field.owner.equals("cpw/mods/fml/common/ModMetadata") && field.desc.equals("Ljava/lang/String;")
                        && insn.getPrevious() instanceof org.objectweb.asm.tree.LdcInsnNode literal && literal.cst instanceof String text) {
                    fields.put(field.name, text);
                }
            }
            String id = fields.getOrDefault("modId", "");
            if (straightLine && allocations == 1 && !id.isBlank()) return new LegacyMod(id, fields.getOrDefault("name", id),
                    fields.getOrDefault("version", ""), fields.getOrDefault("mcversion", ""), "", container + "#ModMetadata", List.of());
        }
        return null;
    }

    private static List<String> requiredAnnotationMods(String dependencies) {
        List<String> required = new ArrayList<>();
        for (String token : dependencies.split(";")) {
            String trimmed = token.trim();
            if (trimmed.startsWith("required-after:") || trimmed.startsWith("required-before:")) required.add(trimmed.substring(trimmed.indexOf(':') + 1));
        }
        return required;
    }

    private static JsonElement parse(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true); // Historical FML metadata, including NEI, contains literal newlines in strings.
        return JsonParser.parseReader(reader);
    }

    private static void readMetadata(String text, String resource, List<LegacyMod> mods) {
        JsonElement root = parse(text);
        JsonElement list = root.isJsonObject() ? root.getAsJsonObject().get("modList") : root;
        if (list == null || !list.isJsonArray()) return;
        for (JsonElement element : list.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("invalid mod metadata in " + resource);
            JsonObject mod = element.getAsJsonObject();
            String id = string(mod, "modid");
            if (id.isBlank()) throw new IllegalArgumentException("missing modid in " + resource);
            List<String> dependencies = new ArrayList<>();
            JsonElement required = mod.get("dependencies");
            if (required != null && required.isJsonArray()) for (JsonElement dep : required.getAsJsonArray()) dependencies.add(dep.getAsString());
            mods.add(new LegacyMod(id, string(mod, "name"), string(mod, "version"), string(mod, "mcversion"), "", resource, dependencies));
        }
    }

    private static void readLibraryRequirements(JsonElement element, Set<String> classes) {
        if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) readLibraryRequirements(child, classes);
        else if (element.isJsonObject()) {
            String name = string(element.getAsJsonObject(), "class");
            if (!name.isBlank()) classes.add(name);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static byte[] read(JarFile jar, java.util.jar.JarEntry entry) throws IOException {
        if (entry.getSize() > MAX_ENTRY_BYTES) throw new IOException("entry is too large: " + entry.getName());
        try (InputStream stream = jar.getInputStream(entry)) {
            byte[] bytes = stream.readNBytes((int) MAX_ENTRY_BYTES + 1);
            if (bytes.length > MAX_ENTRY_BYTES) throw new IOException("entry is too large: " + entry.getName());
            return bytes;
        }
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[32768];
                int length;
                while ((length = stream.read(buffer)) != -1) digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }
}
