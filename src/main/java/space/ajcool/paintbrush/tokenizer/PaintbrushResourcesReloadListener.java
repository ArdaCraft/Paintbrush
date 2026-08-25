package space.ajcool.paintbrush.tokenizer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jspecify.annotations.NonNull;
import space.ajcool.paintbrush.family.FamilyGroupRegistry;
import space.ajcool.paintbrush.filtering.PaintbrushFilter;

/**
 * Handles resource reloading for paintbrush client data.
 * Reloads tokens, family groups, and foliage filters when resources change.
 * Fired on resource reload events (F3+T or world reload).
 */
@Environment(EnvType.CLIENT)
public class PaintbrushResourcesReloadListener extends SimpleReloadListener<Void> {

    /**
     * Prepares reload data off-thread. The paintbrush loaders read the client resource
     * manager directly, so there is nothing to prepare here.
     *
     * @param state the shared reload state
     * @return null, as no prepared data is needed
     */
    @Override
    protected Void prepare(PreparableReloadListener.@NonNull SharedState state) {
        return null;
    }

    /**
     * Reloads all paintbrush client-side resources on the main thread.
     *
     * @param data  the prepared data (unused)
     * @param state the shared reload state
     */
    @Override
    protected void apply(Void data, PreparableReloadListener.@NonNull SharedState state) {
        TokenLoader.load();
        FamilyGroupRegistry.load();
        PaintbrushFilter.load();
    }
}
