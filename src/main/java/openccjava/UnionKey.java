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
     * Taiwan phrases only (excludes variants).
     */
    TwPhrasesOnly,
    /**
     * Taiwan variant phrases followed by character-level variants.
     */
    TwVariantsPair,
    /**
     * S2TWP round-2 Taiwan triple:
     * tw_phrases + tw_variants_phrases + tw_variants.
     */
    S2TwpR2TwTriple,
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
     * Hong Kong variant phrases followed by character-level variants.
     */
    HkVariantsPair,
    /**
     * S2HKP round-2 Hong Kong triple:
     * hk_phrases + hk_variants_phrases + hk_variants.
     */
    S2HkpR2HkTriple,
    /**
     * Hong Kong reverse phrase pairs.
     */
    HkRevPair,
    /**
     * HK2SP round-1 Hong Kong reverse triple:
     * hk_phrases_rev + hk_variants_rev_phrases + hk_variants_rev.
     */
    Hk2SpR1HkRevTriple,

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
