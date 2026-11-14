package github.com.gengyoubo.mfcl.gradle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Build-time transformations shared by the plugin tasks. No Gradle project state is touched here. */
final class MfclDirectTooling {
    private MfclDirectTooling() {
    }

    @FunctionalInterface
    interface ClassBytes {
        void accept(byte[] bytes) throws IOException;
    }

    static final class ClassInfo {
        String superName;
        List<String> interfaces = List.of();
        final Map<String, String> members = new HashMap<>();
    }

    static Map<String, String> readNames(File zip) throws IOException {
        Map<String, String> names = new HashMap<>();
        try (ZipFile archive = new ZipFile(zip)) {
            for (String csv : List.of("fields.csv", "methods.csv")) {
                ZipEntry entry = archive.getEntry(csv);
                if (entry == null) throw new GradleException("MCP mappings " + zip.getName() + " have no " + csv);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(archive.getInputStream(entry), StandardCharsets.UTF_8))) {
                    String line;
                    boolean header = true;
                    while ((line = reader.readLine()) != null) {
                        if (header) {
                            header = false;
                            continue;
                        }
                        String[] columns = line.split(",", -1);
                        if (columns.length >= 2 && !columns[0].isEmpty() && !columns[1].isEmpty())
                            names.put(columns[0], columns[1]);
                    }
                }
            }
        }
        return names;
    }

    static void eachClassInJar(File jar, ClassBytes action) throws IOException {
        try (ZipFile archive = new ZipFile(jar)) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = archive.getInputStream(entry)) {
                    action.accept(input.readAllBytes());
                }
            }
        }
    }

    static void indexClass(byte[] bytes, Map<String, String> names, Map<String, ClassInfo> classes) {
        ClassInfo info = new ClassInfo();
        String[] owner = new String[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                owner[0] = name;
                info.superName = superName;
                info.interfaces = interfaces == null ? List.of() : List.of(interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                String mcp = names.get(name);
                if (mcp != null) info.members.put(mcp + " " + descriptor, name);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                String mcp = names.get(name);
                if (mcp != null) info.members.put(mcp + descriptor, name);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (owner[0] != null) classes.put(owner[0], info);
    }

    static void deobf(File srgJar, File mappings, File target) throws IOException {
        Map<String, String> names = readNames(mappings);
        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                return names.getOrDefault(name, name);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                return names.getOrDefault(name, name);
            }
        };
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        try (ZipFile archive = new ZipFile(srgJar);
             ZipOutputStream output = new ZipOutputStream(new FileOutputStream(target))) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (entry.getName().startsWith("META-INF/")
                        && (entry.getName().endsWith(".SF") || entry.getName().endsWith(".RSA"))) continue;
                byte[] bytes;
                try (InputStream input = archive.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                if (entry.getName().endsWith(".class")) bytes = deobfClass(bytes, remapper);
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(bytes);
                output.closeEntry();
            }
        }
    }

    private static byte[] deobfClass(byte[] bytes, Remapper remapper) {
        ClassReader reader = new ClassReader(bytes);
        Set<String> declared = new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_SYNTHETIC) == 0)
                    declared.add(name + descriptor.substring(0, descriptor.indexOf(')') + 1));
                return null;
            }
        }, ClassReader.SKIP_CODE);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, new ClassRemapper(writer, remapper)) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_SYNTHETIC) != 0
                        && !declared.contains(name + descriptor.substring(0, descriptor.indexOf(')') + 1)))
                    access &= ~(Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC);
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return writer.toByteArray();
    }

    static void reobf(Iterable<File> classes, File srgJar, File mappings, Iterable<File> mods, File target) throws IOException {
        Map<String, String> names = readNames(mappings);
        Map<String, ClassInfo> hierarchy = new HashMap<>();
        eachClassInJar(srgJar, bytes -> indexClass(bytes, names, hierarchy));
        for (File mod : mods) eachClassInJar(mod, bytes -> indexClass(bytes, names, hierarchy));

        List<File> roots = new ArrayList<>();
        for (File root : classes) if (root.isDirectory()) roots.add(root);
        for (File root : roots) {
            Path base = root.toPath();
            try (var files = Files.walk(base)) {
                files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .forEach(path -> {
                            try {
                                indexClass(Files.readAllBytes(path), Map.of(), hierarchy);
                            } catch (IOException failure) {
                                throw new GradleException("Cannot read compiled direct class " + path, failure);
                            }
                        });
            }
        }

        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                if (name.startsWith("<") || owner.startsWith("[")) return name;
                String found = srgName(hierarchy, owner, name + descriptor);
                return found == null ? name : found;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                String found = srgName(hierarchy, owner, name + " " + descriptor);
                return found == null ? name : found;
            }
        };

        deleteRecursively(target);
        target.mkdirs();
        for (File root : roots) {
            Path base = root.toPath();
            try (var files = Files.walk(base)) {
                files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .forEach(path -> {
                            try {
                                ClassWriter writer = new ClassWriter(0);
                                new ClassReader(Files.readAllBytes(path)).accept(new ClassRemapper(writer, remapper), 0);
                                Path output = target.toPath().resolve(base.relativize(path).toString());
                                Files.createDirectories(output.getParent());
                                Files.write(output, writer.toByteArray());
                            } catch (IOException failure) {
                                throw new GradleException("Cannot reobfuscate " + path, failure);
                            }
                        });
            }
        }
    }

    private static String srgName(Map<String, ClassInfo> hierarchy, String owner, String key) {
        LinkedList<String> queue = new LinkedList<>();
        queue.add(owner);
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (current == null || !seen.add(current)) continue;
            ClassInfo info = hierarchy.get(current);
            if (info == null) continue;
            String found = info.members.get(key);
            if (found != null) return found;
            queue.add(info.superName);
            queue.addAll(info.interfaces);
        }
        return null;
    }

    static void legacyNames(File srgJar, Iterable<File> mods, File target) throws IOException {
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        Set<String> written = new HashSet<>();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(target))) {
            List<File> jars = new ArrayList<>();
            jars.add(srgJar);
            for (File mod : mods) jars.add(mod);
            for (File jar : jars) {
                eachClassInJar(jar, bytes -> {
                    ClassWriter writer = new ClassWriter(0);
                    String[] className = new String[1];
                    new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            className[0] = name;
                            super.visit(version, access, name, null, "java/lang/Object", null);
                        }

                        @Override
                        public void visitSource(String source, String debug) {
                        }

                        @Override
                        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            return null;
                        }

                        @Override
                        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                            return null;
                        }

                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    String name = className[0];
                    if (name == null || name.equals("module-info") || name.endsWith("package-info") || !written.add(name))
                        return;
                    output.putNextEntry(new ZipEntry(name + ".class"));
                    output.write(writer.toByteArray());
                    output.closeEntry();
                });
            }
        }
    }

    static void writeManifest(File target, String version, String sourceSet, String mappings, String srgSha256) {
        String manifest = "{\n"
                + "  \"plugin\": \"mfcl.direct\",\n"
                + "  \"pluginVersion\": " + quote(version) + ",\n"
                + "  \"sourceSet\": " + quote(sourceSet) + ",\n"
                + "  \"mappings\": " + quote(mappings) + ",\n"
                + "  \"srgSha256\": " + quote(srgSha256) + "\n"
                + "}\n";
        try {
            Files.writeString(target.toPath().resolve("manifest.json"), manifest, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new GradleException("Cannot write direct class manifest", failure);
        }
    }

    static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = input.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new GradleException("Cannot hash " + file, failure);
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) return;
        try (var paths = Files.walk(file.toPath())) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException failure) {
                    throw new GradleException("Cannot delete " + path, failure);
                }
            });
        }
    }
}
