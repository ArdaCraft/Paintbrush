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
    /** Maximum reach distance for unlimited Axiom infinite reach. */
    private static final double MAX_UNLIMITED_REACH = 100.0D;

    /**
     * Reflective handle for Axiom's editor active-state probe, or {@code null} when Axiom is absent or incompatible.
     */
    private static final Method AXIOM_EDITOR_ACTIVE_METHOD = resolveAxiomEditorActiveMethod();

    /**
     * Reflective handle for Axiom's isAxiomActive() static method, or {@code null} when Axiom is absent or incompatible.
     */
    private static final Method AXIOM_IS_AXIOM_ACTIVE_METHOD = resolveAxiomIsAxiomActiveMethod();

    /**
     * Reflective field for Axiom's Capability.INFINITE_REACH enum constant, or {@code null} when unavailable.
     */
    private static final Object AXIOM_INFINITE_REACH_CAPABILITY = resolveAxiomInfiniteReachCapability();

    /**
     * Reflective method for Capability.isEnabled(), or {@code null} when unavailable.
     */
    private static final Method AXIOM_CAPABILITY_IS_ENABLED_METHOD = resolveAxiomCapabilityIsEnabledMethod();

    /**
     * Reflective method for ClientRestrictions.getInfiniteReachLimit(), or {@code null} when unavailable.
     */
    private static final Method AXIOM_GET_INFINITE_REACH_LIMIT_METHOD = resolveAxiomGetInfiniteReachLimitMethod();

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
     * Returns the maximum reach distance that Paintbrush should use for targeting,
     * honouring Axiom's infinite reach capability if enabled.
     *
     * @param vanillaReach the vanilla reach distance (4.5 or 5.0)
     * @return the reach distance to use for raycast targeting
     */
    public static double targetingReach(double vanillaReach)
    {
        if (!isAxiomActive()) return vanillaReach;
        if (!infiniteReachEnabled()) return vanillaReach;

        var limit = getInfiniteReachLimit();
        if (limit < 0) return MAX_UNLIMITED_REACH;

        return Math.max(vanillaReach, limit);
    }

    /**
     * Checks whether Axiom is currently active.
     *
     * @return {@code true} when Axiom is active; otherwise {@code false}
     */
    private static boolean isAxiomActive()
    {
        if (AXIOM_IS_AXIOM_ACTIVE_METHOD == null) return false;

        try
        {
            return Boolean.TRUE.equals(AXIOM_IS_AXIOM_ACTIVE_METHOD.invoke(null));
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return false;
        }
    }

    /**
     * Checks whether Axiom's infinite reach capability is enabled.
     *
     * @return {@code true} when infinite reach is enabled; otherwise {@code false}
     */
    private static boolean infiniteReachEnabled()
    {
        if (AXIOM_INFINITE_REACH_CAPABILITY == null || AXIOM_CAPABILITY_IS_ENABLED_METHOD == null) return false;

        try
        {
            return Boolean.TRUE.equals(AXIOM_CAPABILITY_IS_ENABLED_METHOD.invoke(AXIOM_INFINITE_REACH_CAPABILITY));
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return false;
        }
    }

    /**
     * Gets the infinite reach limit from Axiom's configuration.
     * Returns -1 for unlimited, or a value clamped to 5..100.
     *
     * @return the reach limit, or -1 for unlimited
     */
    private static int getInfiniteReachLimit()
    {
        if (AXIOM_GET_INFINITE_REACH_LIMIT_METHOD == null) return -1;

        try
        {
            Object result = AXIOM_GET_INFINITE_REACH_LIMIT_METHOD.invoke(null);
            if (result instanceof Integer i) return i;
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            // ignore
        }

        return -1;
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

    /**
     * Resolves Axiom's {@code AxiomClient.isAxiomActive()} static method if it is available.
     *
     * @return the reflective method handle, or {@code null} when it cannot be resolved
     */
    private static Method resolveAxiomIsAxiomActiveMethod()
    {
        try
        {
            var axiomClientClass = Class.forName("com.moulberry.axiom.AxiomClient");
            var method = axiomClientClass.getDeclaredMethod("isAxiomActive");
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException | SecurityException e)
        {
            return null;
        }
    }

    /**
     * Resolves Axiom's {@code Capability.INFINITE_REACH} enum constant if it is available.
     *
     * @return the enum constant, or {@code null} when it cannot be resolved
     */
    private static Object resolveAxiomInfiniteReachCapability()
    {
        try
        {
            var capabilityClass = Class.forName("com.moulberry.axiom.capabilities.Capability");
            var field = capabilityClass.getDeclaredField("INFINITE_REACH");
            field.setAccessible(true);
            return field.get(null);
        }
        catch (ReflectiveOperationException | SecurityException e)
        {
            return null;
        }
    }

    /**
     * Resolves Axiom's {@code Capability.isEnabled()} instance method if it is available.
     *
     * @return the reflective method handle, or {@code null} when it cannot be resolved
     */
    private static Method resolveAxiomCapabilityIsEnabledMethod()
    {
        try
        {
            var capabilityClass = Class.forName("com.moulberry.axiom.capabilities.Capability");
            var method = capabilityClass.getDeclaredMethod("isEnabled");
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException | SecurityException e)
        {
            return null;
        }
    }

    /**
     * Resolves Axiom's {@code ClientRestrictions.getInfiniteReachLimit()} static method if it is available.
     *
     * @return the reflective method handle, or {@code null} when it cannot be resolved
     */
    private static Method resolveAxiomGetInfiniteReachLimitMethod()
    {
        try
        {
            var restrictionsClass = Class.forName("com.moulberry.axiom.restrictions.ClientRestrictions");
            var method = restrictionsClass.getDeclaredMethod("getInfiniteReachLimit");
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException | SecurityException e)
        {
            return null;
        }
    }
}
