package github.com.gengyoubo.mfcl.legacy.runtime;

import com.google.gson.JsonParser;
import github.com.gengyoubo.mfcl.legacy.LegacyDiagnostic;
import github.com.gengyoubo.mfcl.legacy.LegacyDiscovery;
import github.com.gengyoubo.mfcl.legacy.LegacyRuntimeCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeInstallerTest {
    @TempDir Path game;

    private Path runtime() { return game.resolve("mfcl/runtime/1.7.10"); }

    private static String sha256(String content) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private RuntimeInstaller installer(String served, AtomicInteger fetches, List<String> log) {
        return new RuntimeInstaller(runtime(), game.resolve("downloads"), log::add, (url, target) -> {
            fetches.incrementAndGet();
            Files.writeString(target, served);
        });
    }

    @Test void writtenManifestListsEveryFileWithItsHashAndPassesTheCheck() throws Exception {
        Files.createDirectories(runtime());
        Files.writeString(runtime().resolve(LegacyRuntimeCheck.SRG_JAR), "srg");
        Files.writeString(runtime().resolve("lwjgl-2.9.1.jar"), "lwjgl");
        Files.writeString(runtime().resolve(RuntimeInstaller.LANGUAGE_PACK), "languages");
        RuntimeInstaller.writeManifest(runtime(), List.of(LegacyRuntimeCheck.SRG_JAR, "lwjgl-2.9.1.jar", "lwjgl-2.9.1.jar"), List.of(RuntimeInstaller.LANGUAGE_PACK));

        var manifest = JsonParser.parseString(Files.readString(runtime().resolve(RuntimeInstaller.MANIFEST))).getAsJsonObject();
        assertEquals("1.7.10", manifest.get("minecraft").getAsString());
        assertEquals("10.13.4.1614", manifest.get("forge").getAsString());
        var inputs = manifest.getAsJsonArray("inputs");
        assertEquals(2, inputs.size(), "duplicates are listed once");
        assertEquals("lwjgl-2.9.1.jar", inputs.get(0).getAsJsonObject().get("file").getAsString(), "entries are sorted");
        assertEquals(sha256("lwjgl"), inputs.get(0).getAsJsonObject().get("sha256").getAsString());
        assertEquals(sha256("srg"), inputs.get(1).getAsJsonObject().get("sha256").getAsString());
        var resources = manifest.getAsJsonArray("resources");
        assertEquals(RuntimeInstaller.LANGUAGE_PACK, resources.get(0).getAsJsonObject().get("file").getAsString());
        assertEquals(List.of(), LegacyRuntimeCheck.errors(runtime()));
        assertFalse(Files.exists(runtime().resolve(RuntimeInstaller.MANIFEST + ".part")));

        Files.writeString(runtime().resolve("lwjgl-2.9.1.jar"), "changed");
        assertEquals(List.of("RUNTIME_CHECKSUM_MISMATCH"), LegacyRuntimeCheck.errors(runtime()).stream().map(LegacyDiagnostic::code).toList());
    }

    @Test void downloadKeepsOnlyFilesWithThePinnedHashAndReusesTheCache() throws Exception {
        var fetches = new AtomicInteger();
        var installer = installer("library", fetches, new ArrayList<>());
        var download = new RuntimeInstaller.Download("library.jar", "https://example.invalid/library.jar", "SHA-256", sha256("library"));
        Path file = installer.download(download, "library");
        assertEquals("library", Files.readString(file));
        assertEquals(file, installer.download(download, "library"));
        assertEquals(1, fetches.get(), "a verified cached file is not downloaded again");

        Files.writeString(file, "truncated");
        assertEquals("library", Files.readString(installer.download(download, "library")));
        assertEquals(2, fetches.get(), "a cached file with the wrong hash is replaced");
    }

    @Test void downloadRejectsContentThatDiffersFromThePin() throws Exception {
        var fetches = new AtomicInteger();
        var installer = installer("tampered", fetches, new ArrayList<>());
        var download = new RuntimeInstaller.Download("client.jar", "https://example.invalid/client.jar", "SHA-1", "e80d9b3bf5085002218d4be59e668bac718abbc6");
        var failure = assertThrows(IOException.class, () -> installer.download(download, "client"));
        assertTrue(failure.getMessage().contains("expected e80d9b3bf5085002218d4be59e668bac718abbc6"), failure.getMessage());
        assertEquals(3, fetches.get(), "retried before giving up");
        try (Stream<Path> left = Files.list(game.resolve("downloads"))) {
            assertEquals(List.of(), left.toList(), "no unverified or partial file is kept");
        }
    }

    @Test void digestMatchesTheDiscoveryHash() throws Exception {
        Path file = Files.writeString(game.resolve("a.jar"), "content");
        assertEquals(LegacyDiscovery.sha256(file), RuntimeInstaller.digest(file, "SHA-256"));
        assertEquals("040f06fd774092478d450774f5ba30c5da78acc8", RuntimeInstaller.digest(file, "SHA-1"));
    }

    @Test void vanillaLibrariesSkipReplacedAndNativeOnlyEntries() {
        var metadata = JsonParser.parseString("""
                {"libraries":[
                  {"name":"com.google.guava:guava:15.0","downloads":{"artifact":{"path":"com/google/guava/guava/15.0/guava-15.0.jar","url":"u","sha1":"1"}}},
                  {"name":"org.apache.logging.log4j:log4j-core:2.0-beta9","downloads":{"artifact":{"path":"x/log4j-core-2.0-beta9.jar","url":"u","sha1":"2"}}},
                  {"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.1","downloads":{"classifiers":{}}},
                  {"name":"org.lwjgl.lwjgl:lwjgl:2.9.1","downloads":{"artifact":{"path":"org/lwjgl/lwjgl/lwjgl/2.9.1/lwjgl-2.9.1.jar","url":"https://libraries.minecraft.net/lwjgl","sha1":"3"}}}
                ]}""").getAsJsonObject();
        var libraries = RuntimeInstaller.minecraftLibraries(metadata);
        assertEquals(List.of("lwjgl-2.9.1.jar"), List.copyOf(libraries.keySet()));
        assertEquals(new RuntimeInstaller.Download("lwjgl-2.9.1.jar", "https://libraries.minecraft.net/lwjgl", "SHA-1", "3"), libraries.get("lwjgl-2.9.1.jar"));
    }

    @Test void startupPreparationIsSkippedWithoutLegacyModsOrWhenDisabled() throws Exception {
        var log = new ArrayList<String>();
        assertNull(RuntimeInstaller.prepareIfNeeded(game, runtime(), log::add));
        Files.createDirectories(game.resolve("mods/1.7.10"));
        Files.writeString(game.resolve("mods/1.7.10/readme.txt"), "not a mod");
        assertNull(RuntimeInstaller.prepareIfNeeded(game, runtime(), log::add));
        assertEquals(List.of(), log);

        Files.writeString(game.resolve("mods/1.7.10/Thaumcraft.jar"), "mod");
        String previous = System.setProperty(RuntimeInstaller.AUTO_PREPARE_PROPERTY, "false");
        try {
            String reason = RuntimeInstaller.prepareIfNeeded(game, runtime(), log::add);
            assertTrue(reason.contains("-Dmfcl.autoPrepareRuntime=false"), reason);
        } finally {
            if (previous == null) System.clearProperty(RuntimeInstaller.AUTO_PREPARE_PROPERTY);
            else System.setProperty(RuntimeInstaller.AUTO_PREPARE_PROPERTY, previous);
        }
        assertFalse(Files.exists(runtime()));
        assertFalse(Files.exists(game.resolve("mfcl")), "nothing is created when preparation is disabled");
    }
}
