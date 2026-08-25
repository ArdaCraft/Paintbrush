package space.ajcool.paintbrush.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Tracks state for paintbrush wireframe highlighting during rendering.
 * Indicates whether the highlighted block is occluded (hidden behind other blocks).
 */
@Environment(EnvType.CLIENT)
public final class PaintbrushHighlightState {

    /** Whether the wireframe highlight is being drawn for an occluded block. */
    public static boolean OCCLUDED = false;

    /**
     * Private constructor to prevent instantiation.
     */
    private PaintbrushHighlightState() {
    }
}
