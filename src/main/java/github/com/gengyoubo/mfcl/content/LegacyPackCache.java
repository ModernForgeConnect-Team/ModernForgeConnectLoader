package github.com.gengyoubo.mfcl.content;

import com.google.gson.*;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.legacy.LegacyDiscovery;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Fingerprints the actual captured inputs of the immutable base pack, not just mod file names. */
final class LegacyPackCache {
    private static final String MARKER="mfcl-cache-complete.json";
    private static final Gson GSON=new Gson();
    record State(Map<String,String> textures,Set<String> missing) {}
    static String fingerprint(LegacyContent content,LegacyBlockRendering rendering) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(var sink=new DigestOutputStream(OutputStream.nullOutputStream(),hash);var writer=new OutputStreamWriter(sink,StandardCharsets.UTF_8)) {
            put(writer,"mfcl-resource-base-v1");
            // Include the actual generator bytecode so development and updated builds never share an incompatible cache.
            for(Class<?> type:List.of(LegacyPackCache.class,LegacyContentPack.class,LegacyRegistryIds.class,ModernContentBridge.class)) {
                String resource="/"+type.getName().replace('.','/')+".class";
                try(var in=type.getResourceAsStream(resource)){if(in==null)throw new IOException("Cannot fingerprint "+resource);put(writer,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}
            }
            if(!(content.loader() instanceof URLClassLoader loader))throw new IOException("Legacy resources have no enumerable class path");
            for(var url:loader.getURLs()) {
                if(!url.getProtocol().equals("file"))throw new IOException("Uncacheable resource URL "+url);
                Path file=Path.of(url.toURI());if(!Files.isRegularFile(file))throw new IOException("Uncacheable resource path "+file);
                put(writer,file.getFileName().toString());put(writer,LegacyDiscovery.sha256(file));
            }
            put(writer,content.blocks());put(writer,new TreeMap<>(content.miningTags()));
            // Tint colours, translated names and creative contents can change on every boot and are not inputs to item model JSON.
            for(var item:content.items()) {
                put(writer,Arrays.asList(item.key(),item.blockKey(),item.full3D(),item.rodRotation(),item.pulls()));
                for(var variant:item.variants())put(writer,Arrays.asList(variant.metadata(),variant.layers().stream().map(LegacyContent.Layer::icon).toList()));
            }
            put(writer,new TreeSet<>(content.customItems()));
            TreeSet<String> icons=new TreeSet<>(content.registeredIcons()),resources=new TreeSet<>();
            for(var block:content.blocks()) {
                for(var sides:block.faces())icons.addAll(sides);
                for(int meta=0;meta<16;meta++){put(writer,rendering.cuboid(block.key(),meta));put(writer,rendering.inventoryCuboid(block.key(),meta));}
            }
            for(var item:content.items()) {
                for(var variant:item.variants())for(var layer:variant.layers())icons.add(layer.icon());
                for(var pull:item.pulls())icons.addAll(pull.icons());
            }
            for(var map:List.of(content.geometry(),content.contextGeometry()))for(var primitives:map.values())for(var primitive:primitives)resources.add(primitive.texture());
            for(var primitive:rendering.tilePreviews())resources.add(primitive.texture());
            put(writer,resources);for(String resource:resources)if(!resource.contains("textures/"))icons.add(resource);
            for(String icon:icons){put(writer,icon);byte[] pixels=content.iconTexture(icon);put(writer,pixels==null?"":HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels)));put(writer,content.iconTextureMetadata(icon));}
            writer.flush();
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void put(Writer writer,Object value)throws IOException{GSON.toJson(value,writer);writer.write('\n');}
    static State read(Path base,String fingerprint) {
        try {
            var root=JsonParser.parseString(Files.readString(base.resolve(MARKER))).getAsJsonObject();
            if(!fingerprint.equals(root.get("fingerprint").getAsString()))return null;
            var files=root.getAsJsonObject("files");if(files==null||files.size()==0)return null;
            for(var entry:files.entrySet()){
                Path path=base.resolve(entry.getKey()).normalize();
                if(!path.startsWith(base)||!Files.isRegularFile(path)||Files.size(path)!=entry.getValue().getAsLong())return null;
            }
            Map<String,String> textures=new HashMap<>();root.getAsJsonObject("textures").entrySet().forEach(e->textures.put(e.getKey(),e.getValue().getAsString()));
            Set<String> missing=new TreeSet<>();root.getAsJsonArray("missing").forEach(e->missing.add(e.getAsString()));
            return new State(textures,missing);
        } catch(Exception invalid){return null;}
    }
    static void finish(Path base,String fingerprint,Map<String,String> textures,Set<String> missing)throws IOException {
        Map<String,Long> files=new TreeMap<>();
        try(var paths=Files.walk(base)){for(Path path:paths.filter(Files::isRegularFile).toList())files.put(base.relativize(path).toString().replace('\\','/'),Files.size(path));}
        Files.writeString(base.resolve(MARKER),GSON.toJson(Map.of("fingerprint",fingerprint,"textures",new TreeMap<>(textures),"missing",new TreeSet<>(missing),"files",files)),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
