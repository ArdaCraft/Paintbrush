package space.ajcool.paintbrush.state;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import space.ajcool.paintbrush.Paintbrush;

public class PaintbrushKeys {

    private static KeyBinding CAPSLOCK_KEY;

    public static void register() {

        CAPSLOCK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.paintbrush.lock.paint.foliage",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_CAPS_LOCK,
                "category." + Paintbrush.ModID
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (CAPSLOCK_KEY.wasPressed())
                PaintbrushState.DISABLE_FOLIAGE_PAINT = !PaintbrushState.DISABLE_FOLIAGE_PAINT;
        });
    }
}