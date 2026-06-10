package openccjava;

/**
 * Defines how custom dictionary entries are applied to a selected
 * {@link DictSlot}.
 *
 * <p>Modes are evaluated while building or copying a
 * {@link DictionaryMaxlength}. The resulting dictionary can then be supplied
 * to {@link OpenCC}; conversion remains immutable and fast after construction.</p>
 */
public enum CustomDictMode {
    /**
     * Merge custom entries into the selected dictionary slot.
     *
     * <p>Existing built-in entries remain available, but custom entries win
     * when a key already exists. This is the recommended mode for most user
     * dictionaries.</p>
     */
    Append,

    /**
     * Replace only the selected dictionary slot with custom entries.
     *
     * <p>This is an advanced mode. Other dictionary slots in the same OpenCC
     * conversion chain may still run afterward; for example, {@code STCharacters}
     * may still apply after an overridden {@code STPhrases} slot.</p>
     */
    Override
}
