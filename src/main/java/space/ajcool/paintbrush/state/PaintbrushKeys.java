package space.ajcool.paintbrush.state;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.config.PaintbrushConfig;

/**
 * Manages keybindings for the Paintbrush mod.
 * Registers the filter foliage toggle key (default: N) and its event handler.
 */
@Environment(EnvType.CLIENT)
public final class PaintbrushKeys {

    /** Keybinding for toggling foliage filtering. */
    private static KeyMapping filterFoliage;

    /**
     * Private constructor to prevent instantiation.
     */
    private PaintbrushKeys() {
    }

    /**
     * Registers all paintbrush keybindings and their event handlers.
     * The filter foliage key defaults to N and toggles FILTER_FOLIAGE config.
     */
    public static void register() {
        filterFoliage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.paintbrush.filter_foliage",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Paintbrush.ModID, "paintbrush"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(_ ->
        {
            while (filterFoliage.consumeClick()) {
                PaintbrushConfig.FILTER_FOLIAGE = !PaintbrushConfig.FILTER_FOLIAGE;
                PaintbrushConfig.save();
            }
        });
    }
}
