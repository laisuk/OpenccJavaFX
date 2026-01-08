package openccjava;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache for mapping an {@link OpenccConfig} and punctuation setting
 * to a fully prepared {@link DictRefs} instance with per-round
 * {@link StarterUnion} precomputation.
 *
 * <p>
 * This class provides fast retrieval of conversion plans, avoiding
 * repeated recomputation of dictionary unions for the same configuration.
 * Internally, it relies on {@link DictionaryMaxlength}'s
 * {@link UnionKey}-based slot cache rather than building its own
 * round/union maps.
 * </p>
 *
 * <p>
 * Thread-safe: backed by a {@link ConcurrentHashMap}.
 * </p>
 */
public final class ConversionPlanCache {
    /**
     * Provider for a {@link DictionaryMaxlength} instance.
     * <p>
     * Implementations can supply a dictionary either lazily
     * or from a preloaded source.
     * </p>
     */
    public interface Provider {
        /**
         * Returns the {@link DictionaryMaxlength} backing this cache.
         *
         * @return a dictionary instance, never {@code null}
         */
        DictionaryMaxlength get();
    }

    /**
     * Supplier of the backing {@link DictionaryMaxlength}.
     */
    private final Provider provider;

    /**
     * Primary cache mapping from {@link PlanKey} (config + punctuation)
     * to {@link DictRefs} with per-round {@link StarterUnion}.
     */
    private final ConcurrentMap<PlanKey, DictRefs> planCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new cache with the given provider.
     *
     * @param provider the dictionary provider, must not be {@code null}
     * @throws NullPointerException if {@code provider} is {@code null}
     */
    public ConversionPlanCache(Provider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    /**
     * Retrieves or builds a {@link DictRefs} plan for the given
     * configuration and punctuation mode.
     *
     * @param config      the OpenCC configuration
     * @param punctuation whether punctuation conversion is enabled
     * @return the prepared {@link DictRefs} with unions for this config
     */
    public DictRefs getPlan(OpenccConfig config, boolean punctuation) {
        return planCache.computeIfAbsent(new PlanKey(config, punctuation),
                k -> buildPlan(config, punctuation));
    }

    /**
     * Clears all cached conversion plans.
     * <p>
     * This does not affect unions inside {@link DictionaryMaxlength},
     * which are managed separately in slot caches.
     * </p>
     */
    public void clear() {
        planCache.clear();
    }

    // ---------------- plan building ----------------

    /**
     * Builds a {@link DictRefs} conversion plan for the given configuration and punctuation mode.
     * <p>
     * Each plan consists of one or more "rounds," where each round applies a set of
     * dictionary entries to the input. A corresponding {@link StarterUnion} is attached
     * to each round for fast starter checks. Plans are built using
     * {@link DictionaryMaxlength}'s predefined slots and {@link UnionKey} caches.
     * </p>
     *
     * <p>
     * The switch below covers all supported {@link OpenccConfig} values:
     * </p>
     * <ul>
     *   <li><b>S2T / T2S</b> – Simplified ↔ Traditional with optional punctuation dictionaries</li>
     *   <li><b>S2Tw / Tw2S / S2Twp / Tw2Sp</b> – Conversions involving Taiwan-specific
     *       phrase and variant dictionaries</li>
     *   <li><b>S2Hk / Hk2S / T2Hk / Hk2T</b> – Conversions involving Hong Kong variants</li>
     *   <li><b>T2Tw / T2Twp / Tw2T / Tw2Tp</b> – Traditional ↔ Taiwan conversions</li>
     *   <li><b>T2Jp / Jp2T</b> – Traditional ↔ Japanese conversions</li>
     * </ul>
     *
     * <p>
     * For each case, the following rules apply:
     * </p>
     * <ul>
     *   <li>{@code r1}, {@code r2}, {@code r3} – the per-round dictionary lists</li>
     *   <li>{@link DictRefs} – created with the first round and extended with
     *       {@link DictRefs#withRound2(List, StarterUnion)} or
     *       {@link DictRefs#withRound3(List, StarterUnion)} as needed</li>
     *   <li>{@link StarterUnion} – retrieved from {@link DictionaryMaxlength#unionFor(UnionKey)}</li>
     * </ul>
     *
     * @param config      the OpenCC configuration (e.g. {@link OpenccConfig#S2T})
     * @param punctuation whether punctuation conversion should be included
     * @return the constructed {@link DictRefs} for this configuration
     * @throws IllegalArgumentException if the configuration is not handled
     */
    private DictRefs buildPlan(OpenccConfig config, boolean punctuation) {
        final DictionaryMaxlength d = provider.get();

        List<DictionaryMaxlength.DictEntry> r1, r2, r3;
        DictRefs refs;

        switch (config) {
            case S2T: {
                r1 = new ArrayList<>(Arrays.asList(d.st_phrases, d.st_characters));
                if (punctuation) r1.add(d.st_punctuations);
                refs = new DictRefs(r1, d.unionFor(punctuation ? UnionKey.S2T_PUNCT : UnionKey.S2T));
                break;
            }
            case T2S: {
                r1 = new ArrayList<>(Arrays.asList(d.ts_phrases, d.ts_characters));
                if (punctuation) r1.add(d.ts_punctuations);
                refs = new DictRefs(r1, d.unionFor(punctuation ? UnionKey.T2S_PUNCT : UnionKey.T2S));
                break;
            }
            case S2Tw: {
                r1 = new ArrayList<>(Arrays.asList(d.st_phrases, d.st_characters));
                if (punctuation) r1.add(d.st_punctuations);
                r2 = Collections.singletonList(d.tw_variants);
                refs = new DictRefs(r1, d.unionFor(punctuation ? UnionKey.S2T_PUNCT : UnionKey.S2T))
                        .withRound2(r2, d.unionFor(UnionKey.TwVariantsOnly));
                break;
            }
            case Tw2S: {
                r1 = Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev);
                r2 = new ArrayList<>(Arrays.asList(d.ts_phrases, d.ts_characters));
                if (punctuation) r2.add(d.ts_punctuations);
                refs = new DictRefs(r1, d.unionFor(UnionKey.TwRevPair))
                        .withRound2(r2, d.unionFor(punctuation ? UnionKey.T2S_PUNCT : UnionKey.T2S));
                break;
            }
            case S2Twp: {
                r1 = new ArrayList<>(Arrays.asList(d.st_phrases, d.st_characters));
                if (punctuation) r1.add(d.st_punctuations);
                r2 = Collections.singletonList(d.tw_phrases);
                r3 = Collections.singletonList(d.tw_variants);
                refs = new DictRefs(r1, d.unionFor(punctuation ? UnionKey.S2T_PUNCT : UnionKey.S2T))
                        .withRound2(r2, d.unionFor(UnionKey.TwPhrasesOnly))
                        .withRound3(r3, d.unionFor(UnionKey.TwVariantsOnly));
                break;
            }
            case Tw2Sp: {
                r1 = Arrays.asList(d.tw_phrases_rev, d.tw_variants_rev_phrases, d.tw_variants_rev);
                r2 = new ArrayList<>(Arrays.asList(d.ts_phrases, d.ts_characters));
                if (punctuation) r2.add(d.ts_punctuations);
                refs = new DictRefs(r1, d.unionFor(UnionKey.Tw2SpR1TwRevTriple))
                        .withRound2(r2, d.unionFor(punctuation ? UnionKey.T2S_PUNCT : UnionKey.T2S));
                break;
            }
            case S2Hk: {
                r1 = new ArrayList<>(Arrays.asList(d.st_phrases, d.st_characters));
                if (punctuation) r1.add(d.st_punctuations);
                r2 = Collections.singletonList(d.hk_variants);
                refs = new DictRefs(r1, d.unionFor(punctuation ? UnionKey.S2T_PUNCT : UnionKey.S2T))
                        .withRound2(r2, d.unionFor(UnionKey.HkVariantsOnly));
                break;
            }
            case Hk2S: {
                r1 = Arrays.asList(d.hk_variants_rev_phrases, d.hk_variants_rev);
                r2 = new ArrayList<>(Arrays.asList(d.ts_phrases, d.ts_characters));
                if (punctuation) r2.add(d.ts_punctuations);
                refs = new DictRefs(r1, d.unionFor(UnionKey.HkRevPair))
                        .withRound2(r2, d.unionFor(punctuation ? UnionKey.T2S_PUNCT : UnionKey.T2S));
                break;
            }
            case T2Tw: {
                r1 = Collections.singletonList(d.tw_variants);
                refs = new DictRefs(r1, d.unionFor(UnionKey.TwVariantsOnly));
                break;
            }
            case T2Twp: {
                r1 = Collections.singletonList(d.tw_phrases);
                r2 = Collections.singletonList(d.tw_variants);
                refs = new DictRefs(r1, d.unionFor(UnionKey.TwPhrasesOnly))
                        .withRound2(r2, d.unionFor(UnionKey.TwVariantsOnly));
                break;
            }
            case Tw2T: {
                r1 = Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev);
                refs = new DictRefs(r1, d.unionFor(UnionKey.TwRevPair));
                break;
            }
            case Tw2Tp: {
                r1 = Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev);
                r2 = Collections.singletonList(d.tw_phrases_rev);
                refs = new DictRefs(r1, d.unionFor(UnionKey.TwRevPair))
                        .withRound2(r2, d.unionFor(UnionKey.TwPhrasesRevOnly));
                break;
            }
            case T2Hk: {
                r1 = Collections.singletonList(d.hk_variants);
                refs = new DictRefs(r1, d.unionFor(UnionKey.HkVariantsOnly));
                break;
            }
            case Hk2T: {
                r1 = Arrays.asList(d.hk_variants_rev_phrases, d.hk_variants_rev);
                refs = new DictRefs(r1, d.unionFor(UnionKey.HkRevPair));
                break;
            }
            case T2Jp: {
                r1 = Collections.singletonList(d.jp_variants);
                refs = new DictRefs(r1, d.unionFor(UnionKey.JpVariantsOnly));
                break;
            }
            case Jp2T: {
                r1 = Arrays.asList(d.jps_phrases, d.jps_characters, d.jp_variants_rev);
                refs = new DictRefs(r1, d.unionFor(UnionKey.JpRevTriple));
                break;
            }
            default:
                throw new IllegalArgumentException("Unhandled config: " + config);
        }

        return refs;
    }

    // ------------- keys -------------

    /**
     * Composite key for caching conversion plans.
     * <p>
     * A {@code PlanKey} uniquely identifies a plan by:
     * <ul>
     *   <li>{@link OpenccConfig} – the conversion configuration</li>
     *   <li>Punctuation mode – whether punctuation conversion is enabled</li>
     * </ul>
     * </p>
     *
     * <p>
     * Instances are immutable and provide efficient hashing
     * for use in {@link java.util.concurrent.ConcurrentMap}.
     * </p>
     */
    static final class PlanKey {
        /**
         * Conversion configuration (e.g. {@link OpenccConfig#S2T}).
         */
        final OpenccConfig config;
        /**
         * Whether punctuation conversion is enabled.
         */
        final boolean punctuation;
        /**
         * Precomputed hash code for performance in maps.
         */
        private final int hash;

        /**
         * Constructs a new key from the given configuration and punctuation mode.
         *
         * @param c the OpenCC configuration
         * @param p whether punctuation conversion is enabled
         */
        PlanKey(OpenccConfig c, boolean p) {
            this.config = c;
            this.punctuation = p;
            this.hash = (c.ordinal() * 397) ^ (p ? 1 : 0);
        }

        /**
         * Equality is based on both {@link #config} and {@link #punctuation}.
         *
         * @param o the object to compare
         * @return {@code true} if the other object is a {@code PlanKey}
         * with the same config and punctuation flag
         */
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PlanKey)) return false;
            PlanKey k = (PlanKey) o;
            return k.config == config && k.punctuation == punctuation;
        }

        /**
         * Returns the precomputed hash code.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return hash;
        }

        /**
         * Returns a string form for debugging/logging.
         * <p>
         * Example: {@code "S2T_punct"} or {@code "T2S"}.
         * </p>
         *
         * @return the string representation of this key
         */
        @Override
        public String toString() {
            return config + (punctuation ? "_punct" : "");
        }
    }
}
