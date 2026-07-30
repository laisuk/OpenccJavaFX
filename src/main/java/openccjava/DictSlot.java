package openccjava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Identifies one OpenCC dictionary slot that can be patched by a
 * {@link CustomDictSpec}.
 *
 * <p>This enum is the single source of truth for active custom-dictionary
 * slots and their canonical external names. Canonical names, rather than enum
 * ordinals, are the stable portable contract. Parsing is case-insensitive but
 * accepts only canonical active-slot names; numeric strings, aliases, unknown
 * names, and retired slots are rejected.</p>
 *
 * <p>Each active value corresponds to one built-in OpenCC dictionary text file
 * and one serialized {@link DictionaryMaxlength} field. Deprecated constants
 * remain present so legacy source continues to compile, but they are not active
 * and cannot be parsed or used by custom-dictionary operations.</p>
 *
 * @see CustomDictSpec
 * @see DictionaryMaxlength#fromDicts(java.util.List)
 * @see DictionaryMaxlength#withCustomDicts(java.util.List)
 */
public enum DictSlot {
    /**
     * Simplified-to-Traditional character dictionary ({@code STCharacters.txt}).
     */
    STCharacters("STCharacters", true),

    /**
     * Simplified-to-Traditional phrase dictionary ({@code STPhrases.txt}).
     */
    STPhrases("STPhrases", true),

    /**
     * Simplified-to-Traditional punctuation dictionary ({@code STPunctuations.txt}).
     */
    STPunctuations("STPunctuations", true),

    /**
     * Traditional-to-Simplified character dictionary ({@code TSCharacters.txt}).
     */
    TSCharacters("TSCharacters", true),

    /**
     * Traditional-to-Simplified phrase dictionary ({@code TSPhrases.txt}).
     */
    TSPhrases("TSPhrases", true),

    /**
     * Traditional-to-Simplified punctuation dictionary ({@code TSPunctuations.txt}).
     */
    TSPunctuations("TSPunctuations", true),

    /**
     * Traditional-to-Taiwan phrase dictionary ({@code TWPhrases.txt}).
     */
    TWPhrases("TWPhrases", true),

    /**
     * Taiwan-phrase reverse dictionary ({@code TWPhrasesRev.txt}).
     */
    TWPhrasesRev("TWPhrasesRev", true),

    /**
     * Traditional-to-Taiwan variant dictionary ({@code TWVariants.txt}).
     */
    TWVariants("TWVariants", true),

    /**
     * Traditional-to-Taiwan variant phrase dictionary ({@code TWVariantsPhrases.txt}).
     */
    TWVariantsPhrases("TWVariantsPhrases", true),

    /**
     * Taiwan variant reverse dictionary ({@code TWVariantsRev.txt}).
     */
    TWVariantsRev("TWVariantsRev", true),

    /**
     * Taiwan variant reverse phrase dictionary ({@code TWVariantsRevPhrases.txt}).
     */
    TWVariantsRevPhrases("TWVariantsRevPhrases", true),

    /**
     * Traditional-to-Hong-Kong phrase dictionary ({@code HKPhrases.txt}).
     */
    HKPhrases("HKPhrases", true),

    /**
     * Hong Kong phrase reverse dictionary ({@code HKPhrasesRev.txt}).
     */
    HKPhrasesRev("HKPhrasesRev", true),

    /**
     * Traditional-to-Hong-Kong variant dictionary ({@code HKVariants.txt}).
     */
    HKVariants("HKVariants", true),

    /**
     * Traditional-to-Hong-Kong variant phrase dictionary ({@code HKVariantsPhrases.txt}).
     */
    HKVariantsPhrases("HKVariantsPhrases", true),

    /**
     * Hong Kong variant reverse dictionary ({@code HKVariantsRev.txt}).
     */
    HKVariantsRev("HKVariantsRev", true),

    /**
     * Hong Kong variant reverse phrase dictionary ({@code HKVariantsRevPhrases.txt}).
     */
    HKVariantsRevPhrases("HKVariantsRevPhrases", true),

    /**
     * Japanese Shinjitai-to-Traditional Kyujitai character dictionary
     * ({@code JPShinjitaiCharacters.txt}).
     */
    JPSCharacters("JPSCharacters", true),

    /**
     * Traditional Kyujitai-to-Japanese Shinjitai character dictionary
     * ({@code JPShinjitaiCharactersRev.txt}).
     */
    JPSCharactersRev("JPSCharactersRev", true),

    /**
     * Japanese Shinjitai-to-Traditional Kyujitai phrase dictionary
     * ({@code JPShinjitaiPhrases.txt}).
     */
    JPSPhrases("JPSPhrases", true),

    /**
     * Retired compatibility constant formerly associated with
     * {@link #JPSCharactersRev}.
     *
     * <p>This constant is not an active dictionary slot and is rejected by new
     * parsing, construction, and dictionary application APIs.</p>
     *
     * @deprecated Use {@link #JPSCharactersRev}. This constant remains defined
     * so legacy source continues to compile and may be removed in version 2.0.
     */
    @Deprecated
    JPVariants("JPVariants", false),

    /**
     * Retired compatibility constant formerly associated with
     * {@link #JPSCharacters}.
     *
     * <p>This constant is not an active dictionary slot and is rejected by new
     * parsing, construction, and dictionary application APIs.</p>
     *
     * @deprecated Use {@link #JPSCharacters}. This constant remains defined so
     * legacy source continues to compile and may be removed in version 2.0.
     */
    @Deprecated
    JPVariantsRev("JPVariantsRev", false);

    private final String canonicalName;
    private final boolean active;

    DictSlot(String canonicalName, boolean active) {
        this.canonicalName = canonicalName;
        this.active = active;
    }

    private static final List<DictSlot> ACTIVE_SLOTS;
    private static final List<String> SUPPORTED_CANONICAL_NAMES;
    private static final Map<String, DictSlot> ACTIVE_BY_CANONICAL_NAME;
    private static final String SUPPORTED_SLOT_DISPLAY;

    static {
        List<DictSlot> activeSlots = new ArrayList<>();
        List<String> canonicalNames = new ArrayList<>();
        Map<String, DictSlot> byName = new HashMap<>();

        for (DictSlot slot : values()) {
            if (!slot.active) {
                continue;
            }

            activeSlots.add(slot);
            canonicalNames.add(slot.canonicalName);
            byName.put(slot.canonicalName.toLowerCase(Locale.ROOT), slot);
        }

        ACTIVE_SLOTS = Collections.unmodifiableList(activeSlots);
        SUPPORTED_CANONICAL_NAMES = Collections.unmodifiableList(canonicalNames);
        ACTIVE_BY_CANONICAL_NAME = Collections.unmodifiableMap(byName);
        SUPPORTED_SLOT_DISPLAY = String.join(", ", canonicalNames);
    }

    /**
     * Returns whether this constant is an active custom-dictionary slot.
     *
     * <p>Deprecated compatibility constants return {@code false}.</p>
     *
     * @return {@code true} when custom-dictionary APIs support this slot
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the canonical external name of this active slot.
     *
     * <p>The returned name is the stable textual contract used by portable
     * custom-dictionary specifications. Enum ordinals are not an external
     * contract.</p>
     *
     * @return the canonical external slot name
     * @throws IllegalArgumentException if this constant is deprecated or
     *                                  otherwise inactive
     */
    public String toCanonicalName() {
        if (!isActive()) {
            throw new IllegalArgumentException(
                    "Unknown or retired dictionary slot: " + name()
            );
        }
        return canonicalName;
    }

    /**
     * Returns all active custom-dictionary slots in stable declaration order.
     *
     * <p>The returned list is immutable and excludes deprecated compatibility
     * constants.</p>
     *
     * @return an immutable list of active dictionary slots
     */
    public static List<DictSlot> activeSlots() {
        return ACTIVE_SLOTS;
    }

    /**
     * Returns all supported canonical external slot names.
     *
     * <p>The returned list is immutable and has the same order as
     * {@link #activeSlots()}.</p>
     *
     * @return an immutable list of canonical active-slot names
     */
    public static List<String> supportedCanonicalNames() {
        return SUPPORTED_CANONICAL_NAMES;
    }

    /**
     * Returns the supported canonical names as a comma-separated display value.
     *
     * @return canonical active-slot names separated by {@code ", "}
     */
    public static String supportedSlotDisplay() {
        return SUPPORTED_SLOT_DISPLAY;
    }

    /**
     * Attempts to parse a canonical active-slot name.
     *
     * <p>Matching is case-insensitive and ignores surrounding whitespace, but
     * otherwise remains strict. Numeric strings, empty values, unknown names,
     * deprecated names, and hyphen or underscore aliases return {@code null}.</p>
     *
     * @param value canonical slot name to parse; may be {@code null}
     * @return the matching active slot, or {@code null} when the value is invalid
     */
    public static DictSlot tryParse(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return ACTIVE_BY_CANONICAL_NAME.get(trimmed.toLowerCase(Locale.ROOT));
    }

    /**
     * Strictly parses a canonical active-slot name.
     *
     * <p>Matching is case-insensitive and ignores surrounding whitespace. No
     * numeric, deprecated, hyphenated, underscored, or other alias forms are
     * accepted.</p>
     *
     * @param value canonical slot name to parse
     * @return the matching active dictionary slot
     * @throws IllegalArgumentException if {@code value} is {@code null}, blank,
     *                                  numeric, unknown, deprecated, or an alias
     */
    public static DictSlot parse(String value) {
        DictSlot slot = tryParse(value);
        if (slot == null) {
            throw new IllegalArgumentException(
                    "Unknown dictionary slot '" + value + "'. Supported slots: "
                            + supportedSlotDisplay()
            );
        }
        return slot;
    }
}
