package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import github.com.gengyoubo.mfcl.bridge.LegacyContent;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraftforge.event.AddPackFindersEvent;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Derived resources only: original mod archives are read, never edited or installed as modern mods. */
public final class LegacyContentPack {
    private final Path directory;
    private final LegacyContent content;
    private final Map<String, String> textures = new HashMap<>();
    private final Set<String> missingIcons = new TreeSet<>();
    private final Map<String, List<LegacyVertexStream.Primitive>> geometry = new LinkedHashMap<>();
    public LegacyContentPack(Path gameDirectory, LegacyContent content) throws Exception {
        this.content = content;
        Path parent = gameDirectory.resolve("mfcl/generated-packs"); Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, "content-1.7.10-");
        json("pack.mcmeta", Map.of("pack", Map.of("pack_format", 15, "description", "MFCL original 1.7.10 content resources")));
        // builtin/entity item models reference no texture, so their sprites must be stitched explicitly.
        json("assets/mfcl/atlases/blocks.json", Map.of("sources", List.of(Map.of("type", "minecraft:directory", "source", "block/legacy"))));
        for (var block : content.blocks()) {
            var id = ModernContentBridge.id(block.key()); var variants = new LinkedHashMap<String, Object>();
            for (int meta = 0; meta < 16; meta++) {
                String model = id.getNamespace() + ":block/" + id.getPath() + "_" + meta;
                var sides = new LinkedHashMap<String, String>();
                String[] names = {"down", "up", "north", "south", "west", "east"};
                for (int side = 0; side < 6; side++) sides.put(names[side], texture(block.faces().get(meta).get(side), "blocks"));
                sides.put("particle", sides.get("north"));
                json("assets/" + id.getNamespace() + "/models/block/" + id.getPath() + "_" + meta + ".json", Map.of("parent", "minecraft:block/cube", "textures", sides));
                variants.put("legacy_meta=" + meta, Map.of("model", model));
            }
            json("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json", Map.of("variants", variants));
        }
        for (var item : content.items()) {
            var id = ModernContentBridge.id(item.key());
            if (content.customItems().contains(item.key())) {
                // The real old ISBRH geometry is replayed by a modern custom item renderer.
                json("assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json", Map.of("parent", "builtin/entity"));
                continue;
            }
            var overrides = new ArrayList<Object>();
            LegacyContent.Variant base = item.variants().stream().filter(v -> v.metadata() == 0).findFirst().orElse(item.variants().get(0));
            Map<String, Object> model = itemModel(item, base);
            for (var variant : item.variants()) if (variant.metadata() != 0) {
                String path = "item/" + id.getPath() + "_meta_" + variant.metadata();
                json("assets/" + id.getNamespace() + "/models/" + path + ".json", itemModel(item, variant));
                overrides.add(Map.of("predicate", Map.of("mfcl:metadata", variant.metadata()), "model", id.getNamespace() + ":" + path));
            }
            model.put("overrides", overrides);
            json("assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json", model);
        }
        for (var entry : content.geometry().entrySet()) {
            var resolved = new ArrayList<LegacyVertexStream.Primitive>();
            for (var primitive : entry.getValue())
                resolved.add(new LegacyVertexStream.Primitive(primitive.mode(), texture(primitive.texture(), "blocks"), primitive.vertices()));
            geometry.put(entry.getKey(), List.copyOf(resolved));
        }
        languages();
        json("mfcl-content-manifest.json", Map.of("blocks", content.blocks(), "items", content.items(), "creativeTabs", content.tabs(), "missingIcons", missingIcons,
                "customItems", List.copyOf(content.customItems()),
                "simpleBlocks", content.blocks().stream().filter(LegacyContent.Block::simple).map(LegacyContent.Block::key).toList()));
    }
    private Map<String, Object> itemModel(LegacyContent.Item item, LegacyContent.Variant variant) throws Exception {
        var block = item.blockKey() == null ? null : content.blocks().stream()
                .filter(b -> b.key().equals(item.blockKey())).findFirst().orElse(null);
        if (block != null && block.blockModel()) {
            var id = ModernContentBridge.id(block.key());
            return new LinkedHashMap<>(Map.of("parent", id.getNamespace() + ":block/" + id.getPath() + "_" + (variant.metadata() & 15)));
        }
        return new LinkedHashMap<>(Map.of("parent", "minecraft:item/generated", "textures", Map.of("layer0", texture(variant.icon(), "items"))));
    }
    private String texture(String icon, String preferred) throws Exception {
        String cacheKey = preferred + ":" + icon;
        if (textures.containsKey(cacheKey)) return textures.get(cacheKey);
        String namespace = icon.contains(":") ? icon.substring(0, icon.indexOf(':')) : "minecraft";
        String name = icon.contains(":") ? icon.substring(icon.indexOf(':') + 1) : icon;
        for (String folder : List.of(preferred, preferred.equals("items") ? "blocks" : "items")) {
            String resource = "assets/" + namespace + "/textures/" + folder + "/" + name + ".png";
            try (var input = content.loader().getResourceAsStream(resource)) {
                if (input == null) continue;
                byte[] bytes = input.readAllBytes();
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.getBytes(StandardCharsets.UTF_8)));
                // Vanilla 1.20.1 atlas sources include the block/ and item/ directories, not arbitrary texture folders.
                String id = "mfcl:block/legacy/" + hash;
                Path path = directory.resolve("assets/mfcl/textures/block/legacy/" + hash + ".png"); Files.createDirectories(path.getParent()); Files.write(path, bytes);
                try (var meta = content.loader().getResourceAsStream(resource + ".mcmeta")) {
                    if (meta != null) Files.write(path.resolveSibling(path.getFileName() + ".mcmeta"), meta.readAllBytes());
                }
                textures.put(cacheKey, id); return id;
            }
        }
        missingIcons.add(cacheKey); textures.put(cacheKey, "minecraft:item/barrier"); return "minecraft:item/barrier";
    }
    private void languages() throws Exception {
        Map<String, Map<String, String>> languages = new TreeMap<>();
        if (content.loader() instanceof URLClassLoader urls) for (var url : urls.getURLs()) {
            if (!url.getProtocol().equals("file")) continue;
            Path file = Path.of(url.toURI()); if (!Files.isRegularFile(file)) continue;
            try (var jar = new JarFile(file.toFile())) {
                for (var entries = jar.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement(); String name = entry.getName();
                    if (!name.matches("assets/[^/]+/lang/[^/]+\\.lang") || name.startsWith("assets/minecraft/")) continue;
                    String language = name.substring(name.lastIndexOf('/') + 1, name.length() - 5).toLowerCase(Locale.ROOT);
                    var values = languages.computeIfAbsent(language, ignored -> new TreeMap<>());
                    try (var reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
                        // Old language files split on the first '=', without Java Properties escape processing.
                        for (String line; (line = reader.readLine()) != null;) {
                            int split = line.indexOf('='); if (line.isBlank() || line.startsWith("#") || split < 0) continue;
                            values.put(line.substring(0, split), line.substring(split + 1).replaceAll("%(\\d+\\$)?[\\d.]*[df]", "%$1s"));
                        }
                    }
                }
            }
        }
        languages.computeIfAbsent("en_us", ignored -> new TreeMap<>()).putAll(Map.of("itemGroup.mfcl.unclassified_1_7_10", "Other 1.7.10 items",
                "mfcl.content.special_block_pending", "This block needs special rendering or placement support, which is not available yet.",
                "mfcl.content.machine_logic_pending", "Placement and saved data are supported. Machine operation and interaction are pending.",
                "mfcl.content.placement_failed", "This block could not be initialized. Details are in the game log."));
        languages.computeIfAbsent("zh_cn", ignored -> new TreeMap<>()).putAll(Map.of("itemGroup.mfcl.unclassified_1_7_10", "其他 1.7.10 物品",
                "mfcl.content.special_block_pending", "此方块需要特殊渲染或放置支持，暂不能放置。",
                "mfcl.content.machine_logic_pending", "已支持放置和保存数据；机器运行与交互尚未接通。",
                "mfcl.content.placement_failed", "无法初始化此方块，详细信息见游戏日志。"));
        for (var entry : languages.entrySet()) json("assets/mfcl/lang/" + entry.getKey() + ".json", entry.getValue());
    }
    private void json(String resource, Object value) throws IOException {
        Path file = directory.resolve(resource).normalize();
        if (!file.startsWith(directory)) throw new IllegalArgumentException("Generated resource escapes pack: " + resource);
        Files.createDirectories(file.getParent()); Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value), StandardCharsets.UTF_8);
    }
    public Map<String, List<LegacyVertexStream.Primitive>> geometry() { return geometry; }
    public Path directory() { return directory; }
    public Set<String> missingIcons() { return Collections.unmodifiableSet(missingIcons); }
    public void findPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate("mfcl_legacy_1_7_10", Component.literal("MFCL 1.7.10"), true,
                    name -> new PathPackResources(name, directory, false), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            consumer.accept(Objects.requireNonNull(pack, "Generated legacy pack metadata"));
        });
    }
}
