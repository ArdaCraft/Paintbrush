package space.ajcool.paintbrush.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.PaintbrushData;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.item.PaintbrushVolume;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders wireframe highlights for occluded blocks when painting with the paintbrush.
 * Rebuilt for 26.1 using RenderTypes.lines() and LevelRenderContext.bufferSource().
 */
@Environment(EnvType.CLIENT)
public final class PaintbrushHighlightRenderer {

    private static final float[] HULL_COLOR = {1.0F, 1.0F, 1.0F, 0.55F};
    private static final float[] TARGET_COLOR = {1.0F, 1.0F, 1.0F, 0.95F};

    /**
     * Line pipeline mirroring vanilla {@link RenderPipelines#LINES} (same shaders, vertex format,
     * translucent blend, no cull via {@code LINES_SNIPPET}) but with depth testing disabled
     * ({@link CompareOp#ALWAYS_PASS}) so occluded edges draw through the world — the x-ray outline.
     */
    private static final RenderPipeline LINES_NO_DEPTH_PIPELINE =
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation("pipeline/paintbrush_lines_no_depth")
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build();

    /** See-through line render type, wrapping {@link #LINES_NO_DEPTH_PIPELINE} like {@code RenderTypes.LINES}. */
    private static final RenderType LINES_SEE_THROUGH =
            RenderType.create("paintbrush_lines_see_through",
                    RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup());

    private static BlockPos lastTargetPos;
    private static int lastBrushSize = -1;
    private static long lastRefreshTick = Long.MIN_VALUE;
    private static List<Edge> cachedHullEdges = List.of();

    private PaintbrushHighlightRenderer() {}

    public static void render(LevelRenderContext context) {
        var client = Minecraft.getInstance();
        if (!PaintbrushConfig.FILTER_FOLIAGE || !PaintbrushHighlightState.OCCLUDED) return;
        if (client.player == null || client.player.isSpectator() || client.level == null) return;

        var stack = client.player.getMainHandItem();
        if (!stack.is(Paintbrush.PAINTBRUSH_ITEM) && !stack.is(Paintbrush.PAINT_KNIFE_ITEM)) return;
        if (!(client.hitResult instanceof BlockHitResult blockHitResult)) return;
        if (blockHitResult.getType() != HitResult.Type.BLOCK) return;

        var targetPos = blockHitResult.getBlockPos();
        var targetEdges = edgesForBlock(targetPos);
        var hullEdges = stack.is(Paintbrush.PAINTBRUSH_ITEM)
                ? getBrushHullEdges(client, stack, targetPos)
                : List.<Edge>of();

        drawEdges(context, hullEdges, HULL_COLOR);
        drawEdges(context, targetEdges, TARGET_COLOR);
    }

    private static List<Edge> edgesForBlock(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        var a = new Vertex(x, y, z);
        var b = new Vertex(x + 1, y, z);
        var c = new Vertex(x + 1, y + 1, z);
        var d = new Vertex(x, y + 1, z);
        var e = new Vertex(x, y, z + 1);
        var f = new Vertex(x + 1, y, z + 1);
        var g = new Vertex(x + 1, y + 1, z + 1);
        var h = new Vertex(x, y + 1, z + 1);
        return List.of(
                new Edge(a, b), new Edge(b, c), new Edge(c, d), new Edge(d, a),
                new Edge(e, f), new Edge(f, g), new Edge(g, h), new Edge(h, e),
                new Edge(a, e), new Edge(b, f), new Edge(c, g), new Edge(d, h)
        );
    }

    private static List<Edge> getBrushHullEdges(Minecraft client, ItemStack stack, BlockPos targetPos) {
        var size = PaintbrushData.read(stack).getIntOr("size", 1);
        if (client.level == null) return List.of();

        var worldTime = client.level.getGameTime();
        if (targetPos.equals(lastTargetPos) && size == lastBrushSize && worldTime - lastRefreshTick < 10) {
            return cachedHullEdges;
        }

        var positions = PaintbrushVolume.collect(client.level, targetPos, size);
        var paintablePositions = new HashSet<BlockPos>();
        for (var pos : positions) {
            if (PaintbrushVolume.isPaintable(client.level, pos)) {
                paintablePositions.add(pos);
            }
        }

        cachedHullEdges = buildHullEdges(paintablePositions);
        lastTargetPos = targetPos;
        lastBrushSize = size;
        lastRefreshTick = worldTime;
        return cachedHullEdges;
    }

    private static void drawEdges(LevelRenderContext context, List<Edge> edges, float[] color) {
        if (edges.isEmpty()) return;

        PoseStack poseStack = context.poseStack();
        var cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        var pose = poseStack.last();
        MultiBufferSource.BufferSource bufferSource = context.bufferSource();
        var consumer = bufferSource.getBuffer(LINES_SEE_THROUGH);

        int r = (int) (color[0] * 255);
        int g = (int) (color[1] * 255);
        int b = (int) (color[2] * 255);
        int a = (int) (color[3] * 255);

        for (var edge : edges) {
            float x1 = edge.start().x(), y1 = edge.start().y(), z1 = edge.start().z();
            float x2 = edge.end().x(), y2 = edge.end().y(), z2 = edge.end().z();
            float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len == 0) continue;
            float nx = dx / len, ny = dy / len, nz = dz / len;

            consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0F);
            consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0F);
        }

        bufferSource.endBatch(LINES_SEE_THROUGH);
        poseStack.popPose();
    }

    private static List<Edge> buildHullEdges(Set<BlockPos> positions) {
        var edges = new HashSet<Edge>();
        for (var pos : positions) {
            for (var direction : Direction.values()) {
                if (positions.contains(pos.relative(direction))) continue;
                addFaceEdges(edges, pos, direction);
            }
        }
        return new ArrayList<>(edges);
    }

    private static void addFaceEdges(Set<Edge> edges, BlockPos pos, Direction direction) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        switch (direction) {
            case UP    -> addRect(edges, x, y + 1, z, x + 1, y + 1, z + 1, Axis.Y);
            case DOWN  -> addRect(edges, x, y,     z, x + 1, y,     z + 1, Axis.Y);
            case NORTH -> addRect(edges, x, y,     z, x + 1, y + 1, z,     Axis.Z);
            case SOUTH -> addRect(edges, x, y, z + 1, x + 1, y + 1, z + 1, Axis.Z);
            case EAST  -> addRect(edges, x + 1, y, z, x + 1, y + 1, z + 1, Axis.X);
            case WEST  -> addRect(edges, x,     y, z, x,     y + 1, z + 1, Axis.X);
        }
    }

    private static void addRect(Set<Edge> edges, int x1, int y1, int z1, int x2, int y2, int z2, Axis axis) {
        switch (axis) {
            case X -> {
                var a = new Vertex(x1, y1, z1); var b = new Vertex(x1, y2, z1);
                var c = new Vertex(x1, y2, z2); var d = new Vertex(x1, y1, z2);
                addLoop(edges, a, b, c, d);
            }
            case Y -> {
                var a = new Vertex(x1, y1, z1); var b = new Vertex(x2, y1, z1);
                var c = new Vertex(x2, y1, z2); var d = new Vertex(x1, y1, z2);
                addLoop(edges, a, b, c, d);
            }
            case Z -> {
                var a = new Vertex(x1, y1, z1); var b = new Vertex(x2, y1, z1);
                var c = new Vertex(x2, y2, z1); var d = new Vertex(x1, y2, z1);
                addLoop(edges, a, b, c, d);
            }
        }
    }

    private static void addLoop(Set<Edge> edges, Vertex a, Vertex b, Vertex c, Vertex d) {
        edges.add(new Edge(a, b));
        edges.add(new Edge(b, c));
        edges.add(new Edge(c, d));
        edges.add(new Edge(d, a));
    }

    private enum Axis { X, Y, Z }

    private record Vertex(int x, int y, int z) implements Comparable<Vertex> {
        @Override
        public int compareTo(Vertex other) {
            if (x != other.x) return Integer.compare(x, other.x);
            if (y != other.y) return Integer.compare(y, other.y);
            return Integer.compare(z, other.z);
        }
    }

    private record Edge(Vertex start, Vertex end) {
        private Edge(Vertex start, Vertex end) {
            this.start = start.compareTo(end) <= 0 ? start : end;
            this.end   = start.compareTo(end) <= 0 ? end   : start;
        }
    }
}
