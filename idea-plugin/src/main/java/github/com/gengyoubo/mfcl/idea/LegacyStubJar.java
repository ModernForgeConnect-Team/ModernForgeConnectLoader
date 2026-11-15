package github.com.gengyoubo.mfcl.idea;

import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/** Name-only copies, never the legacy implementation classes. */
final class LegacyStubJar {
    record Result(Path jar, List<String> matches) {}
    static Result create(Path original, Path destination, String simpleName) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(original)) {
            byte[] bytes = new byte[32768]; int count;
            while ((count = in.read(bytes)) != -1) digest.update(bytes, 0, count);
        }
        String hash = HexFormat.of().formatHex(digest.digest());
        Files.createDirectories(destination);
        Path target = destination.resolve(hash + ".jar"), temporary = Files.createTempFile(destination, ".mfcl-", ".tmp");
        Set<String> seen = new HashSet<>(); List<String> matches = new ArrayList<>();
        try {
            try (JarFile source = new JarFile(original.toFile(), false); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                var entries = source.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement(); String path = entry.getName();
                    if (entry.isDirectory() || !path.endsWith(".class") || path.startsWith("META-INF/")
                            || path.endsWith("package-info.class") || path.equals("module-info.class")) continue;
                    ClassReader reader;
                    try (InputStream in = source.getInputStream(entry)) { reader = new ClassReader(in); }
                    String name = reader.getClassName();
                    if (!seen.add(name)) throw new IOException("Duplicate class in JAR: " + name);
                    String simple = name.substring(name.lastIndexOf('/') + 1);
                    if (simple.equals(simpleName) || simple.endsWith("$" + simpleName)) matches.add(name.replace('/', '.').replace('$', '.'));
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override public void visit(int version, int access, String className, String signature, String superName, String[] interfaces) {
                            super.visit(version, access, className, null, "java/lang/Object", null);
                        }
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { return null; }
                        @Override public FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) { return null; }
                        @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) { return null; }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    output.putNextEntry(new ZipEntry(name + ".class")); output.write(writer.toByteArray()); output.closeEntry();
                }
            }
            if (matches.isEmpty()) throw new IOException("No class named " + simpleName + " exists in the selected JAR");
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(destination.resolve(hash + ".origin"), original.toAbsolutePath().normalize().toString());
            Collections.sort(matches);
            return new Result(target, List.copyOf(matches));
        } finally { Files.deleteIfExists(temporary); }
    }
}
