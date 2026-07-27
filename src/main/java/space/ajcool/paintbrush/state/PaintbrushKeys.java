package space.ajcool.paintbrush.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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
    private static KeyBinding filterFoliage;

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
        filterFoliage = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.paintbrush.filter_foliage",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category." + Paintbrush.ModID
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            while (filterFoliage.wasPressed()) {
                PaintbrushConfig.FILTER_FOLIAGE = !PaintbrushConfig.FILTER_FOLIAGE;
                PaintbrushConfig.save();
            }
        });
    }
}
