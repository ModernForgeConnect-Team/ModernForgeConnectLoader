package github.com.gengyoubo.mfcl.legacy;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class TestArchives {
    private TestArchives() {}

    public static Path jar(Path path, Map<String, String> attributes, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(path.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.forEach((key, value) -> manifest.getMainAttributes().putValue(key, value));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            for (var entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return path;
    }

    public static byte[] text(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    public static byte[] classBytes(String name, String modId) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
        if (modId != null) {
            var annotation = writer.visitAnnotation("Lcpw/mods/fml/common/Mod;", true);
            annotation.visit("modid", modId);
            annotation.visit("version", "1.0");
            annotation.visitEnd();
        }
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn("mfcl.test.scanExecuted");
        initializer.visitLdcInsn("true");
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
