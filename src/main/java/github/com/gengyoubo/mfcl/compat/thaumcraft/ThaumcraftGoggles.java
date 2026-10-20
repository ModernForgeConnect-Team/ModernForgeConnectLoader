package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * TC4 RenderEventHandler.blockHighlight/drawTagsOnContainer: while the original IGoggles on the head say
 * showIngamePopups, the aspects of a looked-at IAspectContainer (nodes, crucible, alembic, furnace, jars...) float
 * over it, and note blocks / Arcane Ears show their pitch. The decision and the data are the original's; only the drawing is host-side, because TC's own pass is an
 * old Forge highlight event drawn with its TrueType font.
 */
final class ThaumcraftGoggles {
    private record Tag(ResourceLocation icon, int color, int amount) {}
    private static final int PER_ROW = 5, CELL = 18, LIGHT = 0xF000F0;
    private static boolean reported;
    private ThaumcraftGoggles() {}

    static void install(LegacyServices services) {
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) render(services, event);
        });
    }

    private static void render(LegacyServices services, RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var hostState = mc.level.getBlockState(hit.getBlockPos());
        var hostTile = mc.level.getBlockEntity(hit.getBlockPos());
        boolean note = hostState.getBlock() instanceof net.minecraft.world.level.block.NoteBlock;
        if (!note && !(hostTile instanceof LegacyBlockEntity)) return;
        List<Tag> tags = List.of(); String text = null;
        try {
            if (!goggles(services)) return;
            // The vanilla note block is a host block here; its pitch is the same 0..24 TileEntityNote.note.
            if (note) text = "Note: " + hostState.getValue(net.minecraft.world.level.block.NoteBlock.NOTE);
            else {
                var tile = (LegacyBlockEntity) hostTile;
                Integer sensor = sensorNote(services, tile);
                if (sensor != null) text = "Note: " + sensor;
                else tags = tags(services, tile, mc.player.getGameProfile().getName());
            }
        } catch (Exception | LinkageError failure) {
            if (!reported) { reported = true; LogUtils.getLogger().error("MFCL TC4 goggles overlay unavailable", failure); }
            return;
        }
        if (tags.isEmpty() && text == null) return;
        var pos = hit.getBlockPos(); var face = hit.getDirection(); var camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Float just outside the looked-at face, raised a little, always facing the viewer.
        pose.translate(pos.getX() + 0.5 + face.getStepX() * 0.6 - camera.x, pos.getY() + 0.5 + face.getStepY() * 0.6 + (face.getStepY() == 0 ? 0.3 : 0) - camera.y,
                pos.getZ() + 0.5 + face.getStepZ() * 0.6 - camera.z);
        pose.mulPose(event.getCamera().rotation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        if (text != null) font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight, 0xFFFFFF, true, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x40000000, LIGHT);
        int rows = (tags.size() + PER_ROW - 1) / PER_ROW;
        for (int index = 0; index < tags.size(); index++) {
            var tag = tags.get(index);
            int row = index / PER_ROW, inRow = Math.min(PER_ROW, tags.size() - row * PER_ROW);
            float x = -inRow * CELL / 2f + (index % PER_ROW) * CELL, y = (row - rows) * CELL;
            quad(pose, buffers.getBuffer(RenderType.text(tag.icon())), x, y, 16, tag.color());
            String amount = Integer.toString(tag.amount());
            pose.pushPose();
            pose.translate(x + 16, y + 11, -0.5f);
            pose.scale(0.5f, 0.5f, 1);
            font.drawInBatch(amount, -font.width(amount), 0, 0xFFFFFF, true, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
            pose.popPose();
        }
        buffers.endBatch();
        pose.popPose();
    }

    /** Both windings: the billboard's facing flips with the negative name-tag scale. */
    private static void quad(PoseStack pose, VertexConsumer out, float x, float y, float size, int rgb) {
        var matrix = pose.last().pose();
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float[][] corners = {{x, y, 0, 0}, {x, y + size, 0, 1}, {x + size, y + size, 1, 1}, {x + size, y, 1, 0}};
        for (int i = 0; i < 4; i++) { var c = corners[i]; out.vertex(matrix, c[0], c[1], 0).color(r, g, b, 255).uv(c[2], c[3]).uv2(LIGHT).endVertex(); }
        for (int i = 3; i >= 0; i--) { var c = corners[i]; out.vertex(matrix, c[0], c[1], 0).color(r, g, b, 255).uv(c[2], c[3]).uv2(LIGHT).endVertex(); }
    }

    /** Original check: the head-slot item is IGoggles and its showIngamePopups agrees. */
    private static boolean goggles(LegacyServices services) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            services.containers().preparePlayer(); // also mirrors the host's worn armor onto it
            Class<?> mc = loader.loadClass("net.minecraft.client.Minecraft");
            Object player = mc.getField("field_71439_g").get(mc.getMethod("func_71410_x").invoke(null));
            Object inventory = loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Object head = inventory.getClass().getMethod("func_70440_f", int.class).invoke(inventory, 3);
            if (head == null) return false;
            Class<?> goggles = loader.loadClass("thaumcraft.api.IGoggles");
            Object item = stackType.getMethod("func_77973_b").invoke(head);
            return goggles.isInstance(item) && (boolean) goggles.getMethod("showIngamePopups", stackType, loader.loadClass("net.minecraft.entity.EntityLivingBase")).invoke(item, head, player);
        });
    }
    /** TC4 Arcane Ear (TileSensor) pitch, or null for any other tile. */
    private static Integer sensorNote(LegacyServices services, LegacyBlockEntity tile) throws Exception {
        return services.items().inLegacy(() -> {
            Object legacy = tile.legacyOrNull();
            Class<?> sensor = services.loader().loadClass("thaumcraft.common.tiles.TileSensor");
            return legacy != null && sensor.isInstance(legacy) ? (Integer) (int) sensor.getField("note").getByte(legacy) : null;
        });
    }
    private static List<Tag> tags(LegacyServices services, LegacyBlockEntity tile, String viewer) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Object legacy = tile.legacyOrNull();
            Class<?> container = loader.loadClass("thaumcraft.api.aspects.IAspectContainer");
            if (legacy == null || !container.isInstance(legacy)) return List.<Tag>of();
            Object list = container.getMethod("getAspects").invoke(legacy);
            if (list == null) return List.<Tag>of();
            Class<?> aspectType = loader.loadClass("thaumcraft.api.aspects.Aspect"), locationType = loader.loadClass("net.minecraft.util.ResourceLocation");
            Object knowledge = null;
            try { Object proxy = loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null); knowledge = proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy); }
            catch (ReflectiveOperationException unavailable) { /* treat every aspect as discovered */ }
            var result = new ArrayList<Tag>();
            for (Object aspect : (Object[]) list.getClass().getMethod("getAspects").invoke(list)) {
                if (aspect == null) continue;
                int amount = (int) list.getClass().getMethod("getAmount", aspectType).invoke(list, aspect);
                boolean known = knowledge == null || (boolean) knowledge.getClass().getMethod("hasDiscoveredAspect", String.class, aspectType).invoke(knowledge, viewer, aspect);
                ResourceLocation icon;
                int color;
                if (known) {
                    Object image = aspectType.getMethod("getImage").invoke(aspect);
                    icon = ResourceLocation.tryParse(locationType.getMethod("func_110624_b").invoke(image) + ":" + locationType.getMethod("func_110623_a").invoke(image));
                    color = (int) aspectType.getMethod("getColor").invoke(aspect);
                } else { icon = ResourceLocation.tryParse("thaumcraft:textures/aspects/_unknown.png"); color = 0xAAAAAA; }
                if (icon != null) result.add(new Tag(icon, color, amount));
            }
            return List.copyOf(result);
        });
    }
}
