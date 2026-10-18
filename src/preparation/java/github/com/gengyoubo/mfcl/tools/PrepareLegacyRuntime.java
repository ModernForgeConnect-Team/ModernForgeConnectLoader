package github.com.gengyoubo.mfcl.tools;

import org.glavo.pack200.Pack200;
import org.tukaani.xz.LZMAInputStream;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.Adler32;

/** Local derived cache only: neither Mojang bytes nor modified mod jars are distributed. */
public final class PrepareLegacyRuntime {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("client.jar forge.jar joined.srg output.jar");
        Path client = Path.of(args[0]), forge = Path.of(args[1]), mappings = Path.of(args[2]), output = Path.of(args[3]);
        verify(client, "SHA-1", "e80d9b3bf5085002218d4be59e668bac718abbc6");
        verify(forge, "SHA-256", "00d1ca02192c7efb87da95552ebf247021f1e1d642f86d0a03076314def11529");
        verify(mappings, "SHA-256", "43d4376110b2638213d3096f80f8395c076eb05b58d6c0dbd22c0d999b37a357");
        Map<String, byte[]> contents = readJar(client);
        Map<String, byte[]> forgeContents = readJar(forge);
        byte[] packed = forgeContents.get("binpatches.pack.lzma");
        if (packed == null) throw new IOException("Forge client binary patches missing");
        var decoded = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(decoded); var lzma = new LZMAInputStream(new ByteArrayInputStream(packed))) {
            Pack200.newUnpacker().unpack(lzma, jar);
        }
        int patchCount = 0;
        // Use Forge's original GDIFF implementation, isolated from the modern game.
        try (var loader = new URLClassLoader(new URL[]{forge.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             var jar = new JarInputStream(new ByteArrayInputStream(decoded.toByteArray()))) {
            Class<?> patcherType = loader.loadClass("cpw.mods.fml.repackage.com.nothome.delta.GDiffPatcher");
            Object patcher = patcherType.getConstructor().newInstance();
            var patchMethod = patcherType.getMethod("patch", byte[].class, byte[].class);
            for (JarEntry entry; (entry = jar.getNextJarEntry()) != null;) {
                if (!entry.getName().startsWith("binpatch/client/") || !entry.getName().endsWith(".binpatch")) continue;
                var in = new DataInputStream(new ByteArrayInputStream(jar.readAllBytes()));
                in.readUTF();
                String source = in.readUTF().replace('.', '/') + ".class";
                in.readUTF();
                boolean exists = in.readBoolean();
                byte[] original = contents.getOrDefault(source, new byte[0]);
                if (exists) {
                    int checksum = in.readInt();
                    var adler = new Adler32(); adler.update(original);
                    if ((int) adler.getValue() != checksum) throw new IOException("Binary patch input mismatch: " + source);
                } else if (original.length != 0) throw new IOException("Binary patch expected a new class: " + source);
                int length = in.readInt();
                if (length < 0 || length > 16 * 1024 * 1024) throw new IOException("Invalid patch length");
                byte[] diff = in.readNBytes(length);
                if (diff.length != length) throw new EOFException(source);
                contents.put(source, (byte[]) patchMethod.invoke(patcher, original, diff));
                patchCount++;
            }
        }
        if (patchCount < 100) throw new IOException("Incomplete Forge patch set: " + patchCount);
        for (var e : forgeContents.entrySet()) {
            if (!e.getKey().startsWith("META-INF/") && !e.getKey().equals("binpatches.pack.lzma")) contents.putIfAbsent(e.getKey(), e.getValue());
        }
        // Hierarchy includes Forge additions; inherited member references need the declaring owner's SRG name.
        var hierarchyBytes = new HashMap<>(forgeContents); hierarchyBytes.putAll(contents);
        var remapper = new SrgRemapper(mappings, hierarchyBytes);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(output.toAbsolutePath().getParent(), "mfcl-runtime-", ".jar");
        int classes = 0;
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(temp))) {
                Set<String> written = new HashSet<>();
                for (var e : new TreeMap<>(contents).entrySet()) {
                    String name = e.getKey();
                    if (name.startsWith("META-INF/") || name.endsWith("/")) continue;
                    byte[] bytes = e.getValue();
                    if (name.endsWith(".class")) {
                        var reader = new ClassReader(bytes); var writer = new ClassWriter(0);
                        ClassVisitor mapped = new ClassRemapper(writer, remapper);
                        // The old logger must not replace process-wide streams owned by modern Forge.
                        if (reader.getClassName().equals("cpw/mods/fml/relauncher/FMLRelaunchLog")) mapped = new ClassVisitor(Opcodes.ASM9, mapped) {
                            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                                        if (owner.equals("java/lang/System") && (name.equals("setOut") || name.equals("setErr"))) visitInsn(Opcodes.POP);
                                        else super.visitMethodInsn(opcode, owner, name, descriptor, itf);
                                    }
                                };
                            }
                        };
                        reader.accept(mapped, 0);
                        name = remapper.map(reader.getClassName()) + ".class";
                        bytes = writer.toByteArray(); classes++;
                    }
                    if (!written.add(name)) throw new IOException("Duplicate remapped runtime class: " + name);
                    JarEntry entry = new JarEntry(name); entry.setTime(0); out.putNextEntry(entry); out.write(bytes); out.closeEntry();
                }
                JarEntry marker = new JarEntry("META-INF/mfcl-runtime.properties"); marker.setTime(0); out.putNextEntry(marker);
                out.write(("minecraft=1.7.10\nforge=10.13.4.1614\nnamespace=srg\nbinaryPatches=" + patchCount + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        System.out.println("MFCL_RUNTIME_PREPARED classes=" + classes + " binaryPatches=" + patchCount + " output=" + output);
    }

    private static void verify(Path file, String algorithm, String expected) throws Exception {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(file)));
        if (!actual.equals(expected)) throw new IOException("Unrecognized input: " + file + " " + actual);
    }
    private static Map<String, byte[]> readJar(Path file) throws IOException {
        var result = new LinkedHashMap<String, byte[]>();
        try (var jar = new JarFile(file.toFile())) {
            for (var entry : Collections.list(jar.entries())) if (!entry.isDirectory()) {
                try (var in = jar.getInputStream(entry)) { result.put(entry.getName(), in.readAllBytes()); }
            }
        }
        return result;
    }
    static final class SrgRemapper extends Remapper {
        private final Map<String, String> classes = new HashMap<>(), fields = new HashMap<>(), methods = new HashMap<>();
        private final Map<String, List<String>> parents = new HashMap<>();
        SrgRemapper(Path mapping, Map<String, byte[]> contents) throws IOException {
            for (String line : Files.readAllLines(mapping)) {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "CL:" -> classes.put(p[1], p[2]);
                    case "FD:" -> fields.put(p[1], simple(p[2]));
                    case "MD:" -> methods.put(p[1] + p[2], simple(p[3]));
                }
            }
            for (var e : contents.entrySet()) if (e.getKey().endsWith(".class")) {
                var c = new ClassReader(e.getValue()); var supers = new ArrayList<String>();
                if (c.getSuperName() != null) supers.add(c.getSuperName()); supers.addAll(List.of(c.getInterfaces()));
                parents.put(c.getClassName(), supers);
            }
        }
        private static String simple(String value) { return value.substring(value.lastIndexOf('/') + 1); }
        @Override public String map(String name) {
            String mapped = classes.get(name); if (mapped != null) return mapped;
            int nested = name.lastIndexOf('$');
            return nested < 0 ? name : map(name.substring(0, nested)) + name.substring(nested);
        }
        private String member(String owner, String name, String desc, Map<String, String> map, Set<String> seen) {
            if (!seen.add(owner)) return null;
            String direct = map.get(owner + "/" + name + desc); if (direct != null) return direct;
            for (String parent : parents.getOrDefault(owner, List.of())) {
                String inherited = member(parent, name, desc, map, seen); if (inherited != null) return inherited;
            }
            return null;
        }
        @Override public String mapFieldName(String owner, String name, String desc) {
            String result = member(owner, name, "", fields, new HashSet<>()); return result == null ? name : result;
        }
        @Override public String mapMethodName(String owner, String name, String desc) {
            if (name.startsWith("<")) return name;
            String result = member(owner, name, desc, methods, new HashSet<>()); return result == null ? name : result;
        }
    }
}
