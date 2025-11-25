package space.ajcool.paintbrush.tokenizer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TokenReloadListener implements SimpleSynchronousResourceReloadListener {

    private static final Identifier ID = new Identifier("paintbrush", "token_reload");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {

        TokenLoader.load();
    }
}
