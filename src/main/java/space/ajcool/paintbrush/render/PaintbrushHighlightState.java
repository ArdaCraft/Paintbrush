package space.ajcool.paintbrush.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class PaintbrushHighlightState
{
    public static boolean OCCLUDED = false;

    private PaintbrushHighlightState()
    {
    }
}
