package openccjava;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache for (config,punctuation) → DictRefs with per-round StarterUnion.
 * Uses DictionaryMaxlength's UnionKey slot cache (no RoundKey/union map here).
 */
public final class ConversionPlanCache {

    public interface Provider {
        DictionaryMaxlength get();
    }

    private final Provider provider;

    /**
     * Primary cache: (config,punct) → DictRefs (with unions).
     */
    private final ConcurrentMap<PlanKey, DictRefs> planCache = new ConcurrentHashMap<>();

    public ConversionPlanCache(Provider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    public DictRefs getPlan(OpenCC.Config config, boolean punctuation) {
        return planCache.computeIfAbsent(new PlanKey(config, punctuation),
                k -> buildPlan(config, punctuation));
    }

    /**
     * Clear built plans (unions live inside DictionaryMaxlength slots and are managed there).
     */
    public void clear() {
        planCache.clear();
    }

    // ---------------- plan building ----------------

    private DictRefs buildPlan(OpenCC.Config config, boolean punctuation) {
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

    static final class PlanKey {
        final OpenCC.Config config;
        final boolean punctuation;
        private final int hash;

        PlanKey(OpenCC.Config c, boolean p) {
            this.config = c;
            this.punctuation = p;
            this.hash = (c.ordinal() * 397) ^ (p ? 1 : 0);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PlanKey)) return false;
            PlanKey k = (PlanKey) o;
            return k.config == config && k.punctuation == punctuation;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return config + (punctuation ? "_punct" : "");
        }
    }
}
