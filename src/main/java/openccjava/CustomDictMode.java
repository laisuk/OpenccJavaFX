package openccjava;

/**
 * Defines how custom dictionary entries are applied to a selected
 * {@link DictSlot}.
 */
public enum CustomDictMode {
    /**
     * Merge custom entries into the selected dictionary slot.
     */
    Append,

    /**
     * Replace only the selected dictionary slot with custom entries.
     */
    Override
}
