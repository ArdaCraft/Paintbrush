package space.ajcool.paintbrush.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Method;

/**
 * Optional client-side compatibility checks for other mods.
 * All integrations are resolved reflectively so Paintbrush does not gain compile-time dependencies on those mods.
 */
@Environment(EnvType.CLIENT)
public final class PaintbrushCompat
{
    /**
     * Reflective handle for Axiom's editor active-state probe, or {@code null} when Axiom is absent or incompatible.
     */
    private static final Method AXIOM_EDITOR_ACTIVE_METHOD = resolveAxiomEditorActiveMethod();

    /**
     * Prevents instantiation of this utility class.
     */
    private PaintbrushCompat()
    {
    }

    /**
     * Checks whether Axiom's editor UI is currently active.
     * Failures are treated as inactive so missing or changed Axiom classes do not break Paintbrush behavior.
     *
     * @return {@code true} when Axiom reports that its editor UI is active; otherwise {@code false}
     */
    public static boolean axiomEditorActive()
    {
        if (AXIOM_EDITOR_ACTIVE_METHOD == null) return false;

        try
        {
            return Boolean.TRUE.equals(AXIOM_EDITOR_ACTIVE_METHOD.invoke(null));
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return false;
        }
    }

    /**
     * Resolves Axiom's {@code EditorUI.isActive()} method if it is available on the runtime classpath.
     *
     * @return the reflective method handle, or {@code null} when it cannot be resolved
     */
    private static Method resolveAxiomEditorActiveMethod()
    {
        try
        {
            var editorUiClass = Class.forName("com.moulberry.axiom.editor.EditorUI");
            var method = editorUiClass.getDeclaredMethod("isActive");
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException | SecurityException e)
        {
            return null;
        }
    }
}
