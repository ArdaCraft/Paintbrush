package space.ajcool.paintbrush.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.item.PaintbrushVolume;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders wireframe highlights for occluded blocks when painting with the paintbrush.
 * Shows the outline of the brush volume and the target block being painted.
 * Uses world render events to draw debug lines with camera-relative positioning.
 */
@Environment(EnvType.CLIENT)
public final class PaintbrushHighlightRenderer {

    /** Colour for the brush hull edges (semi-transparent white). */
    private static final float[] HULL_COLOR = new float[]{1.0F, 1.0F, 1.0F, 0.55F};

    /** Colour for the target block edges (bright white). */
    private static final float[] TARGET_COLOR = new float[]{1.0F, 1.0F, 1.0F, 0.95F};

    /** Last targeted block position (for caching). */
    private static BlockPos lastTargetPos;

    /** Last brush size used (for caching). */
    private static int lastBrushSize = -1;

    /** Last world tick when hull edges were computed (for caching). */
    private static long lastRefreshTick = Long.MIN_VALUE;

    /** Cached hull edges for the current brush volume. */
    private static List<Edge> cachedHullEdges = List.of();

    /**
     * Private constructor to prevent instantiation.
     */
    @SuppressWarnings("unused")
    private PaintbrushHighlightRenderer() {
    }

    /**
     * Renders highlights for occluded blocks and brush volume.
     * Called during world rendering. Only renders when filtering is on and blocks are occluded.
     *
     * @param context the world render context
     */
    public static void render(WorldRenderContext context) {
        var client = MinecraftClient.getInstance();
        if (!PaintbrushConfig.FILTER_FOLIAGE || !PaintbrushHighlightState.OCCLUDED) return;
        if (client.player == null || client.player.isSpectator() || client.world == null) return;

        var stack = client.player.getMainHandStack();
        if (!stack.isOf(Paintbrush.PAINTBRUSH_ITEM) && !stack.isOf(Paintbrush.PAINT_KNIFE_ITEM)) return;
        if (!(client.crosshairTarget instanceof BlockHitResult blockHitResult)) return;
        if (blockHitResult.getType() != HitResult.Type.BLOCK) return;

        var targetPos = blockHitResult.getBlockPos();
        var targetEdges = edgesForBlock(targetPos);
        var hullEdges = stack.isOf(Paintbrush.PAINTBRUSH_ITEM)
                ? getBrushHullEdges(client, stack, targetPos)
                : List.<Edge>of();

        drawEdges(context, hullEdges, HULL_COLOR);
        drawEdges(context, targetEdges, TARGET_COLOR);
    }

    /**
     * Gets all 12 edges of a block's wireframe outline.
     *
     * @param pos the block position
     * @return a list of all edges forming the block's outline
     */
    private static List<Edge> edgesForBlock(BlockPos pos) {
        var x = pos.getX();
        var y = pos.getY();
        var z = pos.getZ();
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

    /**
     * Gets the hull edges for the brush volume.
     * Caches results for up to 10 ticks to avoid frequent recomputation.
     *
     * @param client    the Minecraft client
     * @param stack     the paintbrush item stack
     * @param targetPos the targeted block position
     * @return a list of edges forming the outer hull of paintable blocks
     */
    private static List<Edge> getBrushHullEdges(MinecraftClient client, ItemStack stack, BlockPos targetPos) {
        var size = getBrushSize(stack);

        if (client.world == null) return List.of();

        var worldTime = client.world.getTime();

        if (targetPos.equals(lastTargetPos) && size == lastBrushSize && worldTime - lastRefreshTick < 10) {
            return cachedHullEdges;
        }

        var positions = PaintbrushVolume.collect(client.world, targetPos, size);
        var paintablePositions = new HashSet<BlockPos>();

        for (var pos : positions) {
            if (PaintbrushVolume.isPaintable(client.world, pos)) {
                paintablePositions.add(pos);
            }
        }

        cachedHullEdges = buildHullEdges(paintablePositions);
        lastTargetPos = targetPos;
        lastBrushSize = size;
        lastRefreshTick = worldTime;

        return cachedHullEdges;
    }

    /**
     * Draws a list of edges using debug lines with the specified colour.
     * Handles render state, matrix transformations, and buffer management.
     *
     * @param context the world render context
     * @param edges   the edges to draw
     * @param color   a float array [red, green, blue, alpha] with values 0.0-1.0
     */
    private static void drawEdges(WorldRenderContext context, List<Edge> edges, float[] color) {
        if (edges.isEmpty()) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        var cameraPos = context.camera().getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.0F);

        var buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int red = (int) (color[0] * 255.0F);
        int green = (int) (color[1] * 255.0F);
        int blue = (int) (color[2] * 255.0F);
        int alpha = (int) (color[3] * 255.0F);

        for (var edge : edges) {
            buffer.vertex(matrix, edge.start().x(), edge.start().y(), edge.start().z()).color(red, green, blue, alpha).next();
            buffer.vertex(matrix, edge.end().x(), edge.end().y(), edge.end().z()).color(red, green, blue, alpha).next();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        matrices.pop();
    }

    /**
     * Gets the brush size from the paintbrush NBT data.
     *
     * @param stack the paintbrush item stack
     * @return the brush size (1-5), or 1 if not set
     */
    private static int getBrushSize(ItemStack stack) {
        NbtCompound paintNbt = stack.getSubNbt("paintbrush");
        if (paintNbt == null || !paintNbt.contains("size")) return 1;

        return paintNbt.getInt("size");
    }

    /**
     * Builds edges for the convex hull of a set of block positions.
     * Only includes edges on the outer surface (where adjacent positions exist outside the set).
     *
     * @param positions the set of block positions
     * @return a list of edges forming the hull
     */
    private static List<Edge> buildHullEdges(Set<BlockPos> positions) {
        var edges = new HashSet<Edge>();

        for (var pos : positions) {
            for (var direction : Direction.values()) {
                if (positions.contains(pos.offset(direction))) continue;

                addFaceEdges(edges, pos, direction);
            }
        }

        return new ArrayList<>(edges);
    }

    /**
     * Adds edges for a face of a block at the given position and direction.
     *
     * @param edges     the edge set to add to
     * @param pos       the block position
     * @param direction the direction of the face
     */
    private static void addFaceEdges(Set<Edge> edges, BlockPos pos, Direction direction) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        switch (direction) {
            case UP -> addRect(edges, x, y + 1, z, x + 1, y + 1, z + 1, Axis.Y);
            case DOWN -> addRect(edges, x, y, z, x + 1, y, z + 1, Axis.Y);
            case NORTH -> addRect(edges, x, y, z, x + 1, y + 1, z, Axis.Z);
            case SOUTH -> addRect(edges, x, y, z + 1, x + 1, y + 1, z + 1, Axis.Z);
            case EAST -> addRect(edges, x + 1, y, z, x + 1, y + 1, z + 1, Axis.X);
            case WEST -> addRect(edges, x, y, z, x, y + 1, z + 1, Axis.X);
        }
    }

    /**
     * Adds the four edges of a rectangular face to the edge set.
     *
     * @param edges the edge set to add to
     * @param x1    first x coordinate
     * @param y1    first y coordinate
     * @param z1    first z coordinate
     * @param x2    second x coordinate
     * @param y2    second y coordinate
     * @param z2    second z coordinate
     * @param axis  which axis the rectangle is on
     */
    private static void addRect(Set<Edge> edges, int x1, int y1, int z1, int x2, int y2, int z2, Axis axis) {
        switch (axis) {
            case X -> {
                var a = new Vertex(x1, y1, z1);
                var b = new Vertex(x1, y2, z1);
                var c = new Vertex(x1, y2, z2);
                var d = new Vertex(x1, y1, z2);
                addLoop(edges, a, b, c, d);
            }
            case Y -> {
                var a = new Vertex(x1, y1, z1);
                var b = new Vertex(x2, y1, z1);
                var c = new Vertex(x2, y1, z2);
                var d = new Vertex(x1, y1, z2);
                addLoop(edges, a, b, c, d);
            }
            case Z -> {
                var a = new Vertex(x1, y1, z1);
                var b = new Vertex(x2, y1, z1);
                var c = new Vertex(x2, y2, z1);
                var d = new Vertex(x1, y2, z1);
                addLoop(edges, a, b, c, d);
            }
        }
    }

    /**
     * Adds four edges forming a loop connecting the given vertices.
     *
     * @param edges the edge set to add to
     * @param a     the first vertex
     * @param b     the second vertex
     * @param c     the third vertex
     * @param d     the fourth vertex
     */
    private static void addLoop(Set<Edge> edges, Vertex a, Vertex b, Vertex c, Vertex d) {
        edges.add(new Edge(a, b));
        edges.add(new Edge(b, c));
        edges.add(new Edge(c, d));
        edges.add(new Edge(d, a));
    }

    /** Enumeration for axis orientation (X, Y, or Z). */
    private enum Axis {
        X,
        Y,
        Z
    }

    /**
     * Represents a 3D vertex for rendering.
     * Comparable to allow consistent edge construction regardless of vertex order.
     */
    private record Vertex(int x, int y, int z) implements Comparable<Vertex> {
        /**
         * Compares vertices for ordering by x, then y, then z coordinate.
         *
         * @param other the other vertex to compare to
         * @return a comparison value
         */
        @Override
        public int compareTo(Vertex other) {
            if (x != other.x) return Integer.compare(x, other.x);
            if (y != other.y) return Integer.compare(y, other.y);
            return Integer.compare(z, other.z);
        }
    }

    /**
     * Represents a line segment between two vertices for rendering.
     * Automatically orders vertices to ensure consistent edge equality.
     *
     * @param start the first vertex
     * @param end   the second vertex
     */
    private record Edge(Vertex start, Vertex end) {
        /**
         * Creates an edge, automatically ordering vertices so smaller comes first.
         *
         * @param start the first vertex
         * @param end   the second vertex
         */
        private Edge(Vertex start, Vertex end) {
            this.start = start.compareTo(end) <= 0 ? start : end;
            this.end = start.compareTo(end) <= 0 ? end : start;
        }
    }
}
