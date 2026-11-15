package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.mfcl.bridge.LegacyObjects;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final ResourceLocation UNKNOWN = ResourceLocation.parse("thaumcraft:textures/aspects/_unknown.png");
    private static final int PER_ROW = 5, LIGHT = 0xF000F0;
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static float tagScale;
    private ThaumcraftGoggles() {}

    static void install(LegacyServices services) {
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) render(services, event);
        });
    }

    private static void render(LegacyServices services, RenderLevelStageEvent event) {
        // TC4 decreases this shared value once per rendered frame, even when no container is targeted.
        if (tagScale > 0) tagScale -= 0.005f;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        BlockHitResult hit = mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
        try { if (!goggles(services)) return; hit = nodeHit(services, mc, hit); }
        catch (Exception | LinkageError failure) { ThaumcraftReflection.reportOnce(REPORTED, "MFCL TC4 goggles overlay unavailable", failure); return; }
        if (hit == null) return;
        var hostState = mc.level.getBlockState(hit.getBlockPos());
        var hostTile = mc.level.getBlockEntity(hit.getBlockPos());
        boolean note = hostState.getBlock() instanceof net.minecraft.world.level.block.NoteBlock;
        if (!note && !(hostTile instanceof LegacyBlockEntity)) return;
        List<Tag> tags = List.of(); String text = null; boolean pedestal = false;
        try {
            // The vanilla note block is a host block here; its pitch is the same 0..24 TileEntityNote.note.
            if (note) text = "Note: " + hostState.getValue(net.minecraft.world.level.block.NoteBlock.NOTE);
            else {
                var tile = (LegacyBlockEntity) hostTile;
                Integer sensor = sensorNote(services, tile);
                if (sensor != null) text = "Note: " + sensor;
                else {
                    tags = tags(services, tile, mc.player.getGameProfile().getName());
                    pedestal = services.items().inLegacy(() -> services.loader().loadClass("thaumcraft.common.tiles.TileWandPedestal").isInstance(tile.legacyOrNull()));
                }
            }
        } catch (Exception | LinkageError failure) {
            ThaumcraftReflection.reportOnce(REPORTED, "MFCL TC4 goggles overlay unavailable", failure);
            return;
        }
        if (tags.isEmpty() && text == null) return;
        var pos = hit.getBlockPos(); var face = hit.getDirection(); var camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        if (text != null) {
            pose.pushPose();
            pose.translate(pos.getX() + 0.5 - camera.x, pos.getY() + 1.2 - camera.y, pos.getZ() + 0.5 - camera.z);
            pose.mulPose(event.getCamera().rotation());
            pose.scale(-0.025f, -0.025f, 0.025f);
            font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight, 0xFFFFFF, true, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LIGHT);
            pose.popPose();
        }
        if (!tags.isEmpty() && tagScale < 0.3f) tagScale += 0.031f - tagScale / 10f;
        boolean airAbove = mc.level.isEmptyBlock(pos.above());
        var side = airAbove ? net.minecraft.core.Direction.UP : face;
        var playerPos = mc.player.getPosition(event.getPartialTick());
        float yaw = (float)Math.toDegrees(Math.atan2(playerPos.x - pos.getX() - 0.5, playerPos.z - pos.getZ() - 0.5)) + 180f;
        for (int index = 0; index < tags.size(); index++) {
            var tag = tags.get(index);
            int row = index / PER_ROW, inRow = Math.min(PER_ROW, tags.size() - row * PER_ROW);
            pose.pushPose();
            pose.translate(pos.getX() + .5 + 2 * tagScale * side.getStepX() - camera.x,
                    pos.getY() + .5 + (airAbove ? .4 : 0) + (pedestal ? .6 : 0) + row * tagScale * 1.05 + 2 * tagScale * side.getStepY() - camera.y,
                    pos.getZ() + .5 + 2 * tagScale * side.getStepZ() - camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            pose.translate((index % PER_ROW - inRow / 2f + .5f) * 4 * tagScale * tagScale, 0, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
            pose.scale(tagScale, tagScale, tagScale);
            quad(pose, buffers.getBuffer(RenderType.textSeeThrough(tag.icon())), -.5f, -.5f, 1, tag.color());
            if (tag.amount() >= 0) {
                String amount = Integer.toString(tag.amount());
                pose.pushPose();
                pose.scale(.04f, .04f, .04f);
                pose.translate(0, 6, -.1f);
                font.drawInBatch(amount, 14 - font.width(amount), 1, 0x111111, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
                pose.translate(0, 0, -.1f);
                font.drawInBatch(amount, 13 - font.width(amount), 0, 0xFFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
                pose.popPose();
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Floating TC nodes have an empty selected bounding box, so the host ray can pass through them. */
    private static BlockHitResult nodeHit(LegacyServices services, Minecraft mc, BlockHitResult obstruction) throws Exception {
        var start = mc.player.getEyePosition();
        var direction = mc.player.getViewVector(1f);
        double reach = mc.gameMode == null ? 4.5 : mc.gameMode.getPickRange();
        if (obstruction != null) reach = Math.min(reach, start.distanceTo(obstruction.getLocation()));
        final double limit = reach;
        var end = start.add(direction.scale(limit));
        var min = net.minecraft.core.BlockPos.containing(Math.min(start.x,end.x),Math.min(start.y,end.y),Math.min(start.z,end.z));
        var max = net.minecraft.core.BlockPos.containing(Math.max(start.x,end.x),Math.max(start.y,end.y),Math.max(start.z,end.z));
        var candidates = new ArrayList<net.minecraft.core.BlockPos>();
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(min,max))
            if (mc.level.getBlockEntity(pos) instanceof LegacyBlockEntity) candidates.add(pos.immutable());
        return services.items().inLegacy(() -> {
            Class<?> node = services.loader().loadClass("thaumcraft.api.nodes.INode");
            BlockHitResult nearest = obstruction;
            double distance = limit;
            for (var pos : candidates) {
                var tile = (LegacyBlockEntity) mc.level.getBlockEntity(pos);
                if (tile == null || !node.isInstance(tile.legacyOrNull())) continue;
                var intersection = new net.minecraft.world.phys.AABB(pos).clip(start,end);
                if (intersection.isEmpty()) continue;
                double candidate = start.distanceTo(intersection.get());
                if (candidate > distance) continue;
                distance = candidate;
                nearest = new BlockHitResult(intersection.get(), net.minecraft.core.Direction.getNearest(-direction.x,-direction.y,-direction.z),pos,false);
            }
            return nearest;
        });
    }

    /** Both windings: the billboard's facing flips with the negative name-tag scale. */
    private static void quad(PoseStack pose, VertexConsumer out, float x, float y, float size, int rgb) {
        var matrix = pose.last().pose();
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float[][] corners = {{x, y, 0, 0}, {x, y + size, 0, 1}, {x + size, y + size, 1, 1}, {x + size, y, 1, 0}};
        for (int i = 0; i < 4; i++) { var c = corners[i]; out.vertex(matrix, c[0], c[1], 0).color(r, g, b, 191).uv(c[2], c[3]).uv2(LIGHT).endVertex(); }
        for (int i = 3; i >= 0; i--) { var c = corners[i]; out.vertex(matrix, c[0], c[1], 0).color(r, g, b, 191).uv(c[2], c[3]).uv2(LIGHT).endVertex(); }
    }

    /** Original check: the head-slot item is IGoggles and its showIngamePopups agrees. */
    private static boolean goggles(LegacyServices services) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            services.containers().preparePlayer(); // also mirrors the host's worn armor onto it
            Class<?> mc = loader.loadClass("net.minecraft.client.Minecraft");
            Object player = mc.getField("field_71439_g").get(mc.getMethod("func_71410_x").invoke(null));
            Object inventory = loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
            Object head = inventory.getClass().getMethod("func_70440_f", int.class).invoke(inventory, 3);
            Object item = LegacyObjects.item(head);
            Class<?> goggles = loader.loadClass("thaumcraft.api.IGoggles");
            return goggles.isInstance(item) && (boolean) goggles.getMethod("showIngamePopups", loader.loadClass("net.minecraft.item.ItemStack"),
                    loader.loadClass("net.minecraft.entity.EntityLivingBase")).invoke(item, head, player);
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
            try { Object proxy = ThaumcraftReflection.proxy(loader); knowledge = proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy); }
            catch (ReflectiveOperationException unavailable) { /* treat every aspect as discovered */ }
            var amountOf = list.getClass().getMethod("getAmount", aspectType);
            var discovered = knowledge == null ? null : knowledge.getClass().getMethod("hasDiscoveredAspect", String.class, aspectType);
            Method image = aspectType.getMethod("getImage"), colorOf = aspectType.getMethod("getColor"),
                    domain = locationType.getMethod("func_110624_b"), path = locationType.getMethod("func_110623_a");
            var result = new ArrayList<Tag>();
            for (Object aspect : (Object[]) list.getClass().getMethod("getAspects").invoke(list)) {
                if (aspect == null) continue;
                int amount = (int) amountOf.invoke(list, aspect);
                ResourceLocation icon;
                int color = (int) colorOf.invoke(aspect);
                if (discovered == null || (boolean) discovered.invoke(knowledge, viewer, aspect)) {
                    Object location = image.invoke(aspect);
                    icon = ResourceLocation.tryParse(domain.invoke(location) + ":" + path.invoke(location));
                } else { icon = UNKNOWN; }
                if (icon != null) result.add(new Tag(icon, color, amount));
            }
            return List.copyOf(result);
        });
    }
}
