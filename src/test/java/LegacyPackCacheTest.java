package github.com.gengyoubo.mfcl.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPackCacheTest {
    @TempDir Path root;
    @Test void incompleteAndDamagedBasesAreMisses() throws Exception {
        Path base=root.resolve("base");Files.createDirectories(base.resolve("assets/example"));
        Path content=base.resolve("assets/example/test.txt");Files.writeString(content,"base");
        assertNull(LegacyPackCache.read(base,"key"));
        LegacyPackCache.finish(base,"key",Map.of("blocks:test","mfcl:block/test"),Set.of("missing"));
        var state=LegacyPackCache.read(base,"key");assertNotNull(state);assertEquals("mfcl:block/test",state.textures().get("blocks:test"));
        assertNull(LegacyPackCache.read(base,"other"));
        Files.writeString(content,"truncated!");assertNull(LegacyPackCache.read(base,"key"));
    }
    @Test void sessionResourcesOverrideBaseWithoutMutatingIt() throws Exception {
        Path base=root.resolve("base"),session=root.resolve("session");
        Files.createDirectories(base.resolve("assets/example/test"));Files.createDirectories(session.resolve("assets/example/test"));
        Files.writeString(base.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"test\"}}");
        Files.writeString(base.resolve("assets/example/test/a.json"),"old");Files.writeString(base.resolve("assets/example/test/b.json"),"base");
        Files.writeString(session.resolve("assets/example/test/a.json"),"new");
        try(var pack=new LayeredLegacyPackResources("test",base,session)) {
            var id=new ResourceLocation("example","test/a.json");
            try(var in=pack.getResource(PackType.CLIENT_RESOURCES,id).get()){assertEquals("new",new String(in.readAllBytes()));}
            Map<ResourceLocation,String> listed=new HashMap<>();
            pack.listResources(PackType.CLIENT_RESOURCES,"example","test",(key,supplier)->{try(var in=supplier.get()){listed.put(key,new String(in.readAllBytes()));}catch(Exception e){throw new RuntimeException(e);}});
            assertEquals(Map.of(id,"new",new ResourceLocation("example","test/b.json"),"base"),listed);
            assertTrue(pack.getNamespaces(PackType.CLIENT_RESOURCES).contains("example"));
        }
        assertEquals("old",Files.readString(base.resolve("assets/example/test/a.json")));
    }
}
