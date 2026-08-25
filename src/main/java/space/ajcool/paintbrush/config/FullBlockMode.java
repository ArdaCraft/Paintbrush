package space.ajcool.paintbrush.config;

/**
 * Controls when the paint knife should promote a max-layer block to its family's full block.
 * {@code PARTIAL} promotes only shapes that do not already visually fill a full cube.
 */
public enum FullBlockMode
{
    ALL,
    PARTIAL,
    NONE;

    /**
     * Cycles the mode in command order: {@code ALL -> PARTIAL -> NONE -> ALL}.
     *
     * @return the next full-block promotion mode
     */
    public FullBlockMode next()
    {
        return switch (this)
        {
            case ALL -> PARTIAL;
            case PARTIAL -> NONE;
            case NONE -> ALL;
        };
    }
}
