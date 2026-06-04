package openccjava;

/**
 * Identifies one OpenCC dictionary slot that can be patched by a
 * {@link CustomDictSpec}.
 */
public enum DictSlot {
    STCharacters,
    STPhrases,
    STPunctuations,
    TSCharacters,
    TSPhrases,
    TSPunctuations,
    TWPhrases,
    TWPhrasesRev,
    TWVariants,
    TWVariantsPhrases,
    TWVariantsRev,
    TWVariantsRevPhrases,
    HKVariants,
    HKVariantsPhrases,
    HKVariantsRev,
    HKVariantsRevPhrases,
    JPSCharacters,
    JPSPhrases,
    JPVariants,
    JPVariantsRev
}
