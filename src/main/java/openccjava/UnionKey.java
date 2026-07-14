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
 * Each key corresponds to a union slot owned by {@link UnionCache} and
 * shared by {@link ConversionPlanCache} while building prepared
 * {@link DictRefs} plans.
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
     * Taiwan phrase and variant triple:
     * Taiwan phrases plus phrase-level and character-level variants.
     */
    TwTriple,
    /**
     * Taiwan variant phrases followed by character-level variants.
     */
    TwVariantsPair,
    /**
     * Taiwan reverse phrase pairs.
     */
    TwRevPair,
    /**
     * Taiwan reverse phrase and variant triple: phrases followed by
     * phrase-level and character-level variant mappings.
     */
    TwRevTriple,

    // ===== Hong Kong-specific unions =====
    /**
     * Hong Kong variant phrases followed by character-level variants.
     */
    HkVariantsPair,
    /**
     * Hong Kong phrase and variant triple: phrases followed by
     * phrase-level and character-level variant mappings.
     */
    HkTriple,
    /**
     * Hong Kong reverse phrase pairs.
     */
    HkRevPair,
    /**
     * Hong Kong reverse phrase and variant triple: phrases followed by
     * phrase-level and character-level variant mappings.
     */
    HkRevTriple,

    // ===== Japan-specific unions =====
    /**
     * Japanese Shinjitai-to-Traditional Kyujitai characters only.
     */
    JpsCharactersRev,
    /**
     * Japanese Shinjitai pair: JPS phrases + JPS characters.
     */
    JpsPair
}
