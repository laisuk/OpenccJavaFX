package openccjava;

/**
 * Enumeration of dictionary union keys used by {@code openccjava}.
 * <p>
 * These keys represent different prebuilt dictionary groupings or
 * conversion plans for Simplified–Traditional Chinese conversion
 * and region-specific variants (Taiwan, Hong Kong, Japan).
 * </p>
 *
 * <p>
 * Each key corresponds to a union of dictionary slots inside
 * {@link DictionaryMaxlength}, used to speed up phrase lookups and
 * provide consistent conversions across different configurations.
 * </p>
 */
public enum UnionKey {
    // S2T / T2S
    /**
     * Simplified → Traditional (main).
     */
    S2T,
    /**
     * Simplified → Traditional with punctuation conversion.
     */
    S2T_PUNCT,
    /**
     * Traditional → Simplified (main).
     */
    T2S,
    /**
     * Traditional → Simplified with punctuation conversion.
     */
    T2S_PUNCT,

    // ===== Taiwan-specific unions =====
    /**
     * Taiwan phrases only (excludes variants).
     */
    TwPhrasesOnly,
    /**
     * Taiwan variants only (character-level).
     */
    TwVariantsOnly,
    /**
     * Reverse mapping of Taiwan phrases only.
     */
    TwPhrasesRevOnly,
    /**
     * Taiwan reverse phrase pairs.
     */
    TwRevPair,
    /**
     * Taiwan to Simplified conversion, round 1, with
     * Taiwan reverse triple fallback.
     */
    Tw2SpR1TwRevTriple,

    // ===== Hong Kong-specific unions =====
    /**
     * Hong Kong variants only (character-level).
     */
    HkVariantsOnly,
    /**
     * Hong Kong reverse phrase pairs.
     */
    HkRevPair,

    // ===== Japan-specific unions =====
    /**
     * Japanese variants only (character-level).
     */
    JpVariantsOnly,
    /**
     * Japanese reverse triple mapping.
     */
    JpRevTriple
}
