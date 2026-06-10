package openccjava;

/**
 * Identifies one OpenCC dictionary slot that can be patched by a
 * {@link CustomDictSpec}.
 *
 * <p>Each value corresponds to one built-in OpenCC dictionary text file and
 * one serialized {@link DictionaryMaxlength} field. Custom dictionary files
 * are applied only to the selected slot; they do not replace the whole
 * conversion pipeline.</p>
 *
 * @see CustomDictSpec
 * @see DictionaryMaxlength#fromDicts(java.util.List)
 * @see DictionaryMaxlength#withCustomDictFiles(java.util.List)
 */
public enum DictSlot {
    /**
     * Simplified-to-Traditional character dictionary ({@code STCharacters.txt}).
     */
    STCharacters,

    /**
     * Simplified-to-Traditional phrase dictionary ({@code STPhrases.txt}).
     */
    STPhrases,

    /**
     * Simplified-to-Traditional punctuation dictionary ({@code STPunctuations.txt}).
     */
    STPunctuations,

    /**
     * Traditional-to-Simplified character dictionary ({@code TSCharacters.txt}).
     */
    TSCharacters,

    /**
     * Traditional-to-Simplified phrase dictionary ({@code TSPhrases.txt}).
     */
    TSPhrases,

    /**
     * Traditional-to-Simplified punctuation dictionary ({@code TSPunctuations.txt}).
     */
    TSPunctuations,

    /**
     * Traditional-to-Taiwan phrase dictionary ({@code TWPhrases.txt}).
     */
    TWPhrases,

    /**
     * Taiwan-phrase reverse dictionary ({@code TWPhrasesRev.txt}).
     */
    TWPhrasesRev,

    /**
     * Traditional-to-Taiwan variant dictionary ({@code TWVariants.txt}).
     */
    TWVariants,

    /**
     * Traditional-to-Taiwan variant phrase dictionary ({@code TWVariantsPhrases.txt}).
     */
    TWVariantsPhrases,

    /**
     * Taiwan variant reverse dictionary ({@code TWVariantsRev.txt}).
     */
    TWVariantsRev,

    /**
     * Taiwan variant reverse phrase dictionary ({@code TWVariantsRevPhrases.txt}).
     */
    TWVariantsRevPhrases,

    /**
     * Traditional-to-Hong-Kong phrase dictionary ({@code HKPhrases.txt}).
     */
    HKPhrases,

    /**
     * Hong Kong phrase reverse dictionary ({@code HKPhrasesRev.txt}).
     */
    HKPhrasesRev,

    /**
     * Traditional-to-Hong-Kong variant dictionary ({@code HKVariants.txt}).
     */
    HKVariants,

    /**
     * Traditional-to-Hong-Kong variant phrase dictionary ({@code HKVariantsPhrases.txt}).
     */
    HKVariantsPhrases,

    /**
     * Hong Kong variant reverse dictionary ({@code HKVariantsRev.txt}).
     */
    HKVariantsRev,

    /**
     * Hong Kong variant reverse phrase dictionary ({@code HKVariantsRevPhrases.txt}).
     */
    HKVariantsRevPhrases,

    /**
     * Traditional Japanese Kyujitai to Shinjitai character dictionary
     * ({@code JPShinjitaiCharacters.txt}).
     */
    JPSCharacters,

    /**
     * Traditional Japanese Kyujitai to Shinjitai phrase dictionary
     * ({@code JPShinjitaiPhrases.txt}).
     */
    JPSPhrases,

    /**
     * Traditional-to-Japanese variant dictionary ({@code JPVariants.txt}).
     */
    JPVariants,

    /**
     * Japanese variant reverse dictionary ({@code JPVariantsRev.txt}).
     */
    JPVariantsRev
}
