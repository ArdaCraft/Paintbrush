package space.ajcool.paintbrush.tokenizer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import space.ajcool.paintbrush.family.FamilyGroupRegistry;
import space.ajcool.paintbrush.filtering.PaintbrushFilter;

/**
 * Handles resource reloading for paintbrush client data.
 * Reloads tokens, family groups, and foliage filters when resources change.
 * Fired on resource reload events (F3+T or world reload).
 */
@Environment(EnvType.CLIENT)
public class PaintbrushResourcesReloadListener implements SimpleSynchronousResourceReloadListener {

    /** Unique identifier for this reload listener. */
    private static final Identifier ID = new Identifier("paintbrush", "resources_reload");

    /**
     * Gets the fabric identifier for this reload listener.
     *
     * @return the unique identifier
     */
    @Override
    public Identifier getFabricId() {
        return ID;
    }

    /**
     * Reloads all paintbrush client-side resources.
     * Called whenever the resource manager reloads.
     *
     * @param manager the resource manager
     */
    @Override
    public void reload(ResourceManager manager) {
        TokenLoader.load();
        FamilyGroupRegistry.load();
        PaintbrushFilter.load();
    }
}
