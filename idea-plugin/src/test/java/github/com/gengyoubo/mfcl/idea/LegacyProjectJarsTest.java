package github.com.gengyoubo.mfcl.idea;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class LegacyProjectJarsTest {
    public static void main(String[] args) throws Exception {
        Path project = Files.createTempDirectory("mfcl-project-jars");
        Path directory = Files.createDirectories(project.resolve("build/mfcl-direct/direct"));
        Path jar = directory.resolve("legacy-names-1.7.10.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String name : List.of("net/minecraftforge/common/util/ForgeDirection.class", "thaumcraft/common/tiles/TileTube.class")) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(0); zip.closeEntry();
            }
        }
        var direction = LegacyProjectJars.find(project, "ForgeDirection");
        if (direction.size() != 1 || !direction.get(0).qualifiedName().equals("net.minecraftforge.common.util.ForgeDirection")) throw new AssertionError(direction);
        var tube = LegacyProjectJars.find(project, "TileTube");
        if (tube.size() != 1 || !tube.get(0).qualifiedName().equals("thaumcraft.common.tiles.TileTube")) throw new AssertionError(tube);
        if (!LegacyProjectJars.find(project, "Missing").isEmpty()) throw new AssertionError("Unknown class accepted");
        Path runtime = Files.createDirectories(project.resolve("run/mfcl/runtime/1.7.10"));
        Path forge = Files.write(runtime.resolve("minecraft-forge-1.7.10-srg.jar"), new byte[]{0});
        Path custom = Files.createDirectories(project.resolve("custom-runtime"));
        Path customForge = Files.write(custom.resolve("minecraft-forge-1.7.10-srg.jar"), new byte[]{0});
        Files.writeString(project.resolve("gradle-local.properties"), "legacyRuntimeDir=custom-runtime\n");
        var found = LegacyProjectJars.runtimeJars(project);
        if (!found.contains(forge) || !found.contains(customForge)) throw new AssertionError("Forge runtime not found: " + found);
    }
}
