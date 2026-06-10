package openccjava;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cache of lazily built {@link StarterUnion} instances for a single
 * {@link DictionaryMaxlength} provider.
 *
 * <p>Package-private: this is an internal implementation detail shared by
 * {@link ConversionPlanCache} and should not be exposed as part of the
 * supported library API.</p>
 */
final class UnionCache {
    private final ConversionPlanCache.Provider provider;
    private volatile Unions unions = new Unions();

    UnionCache(ConversionPlanCache.Provider provider) {
        this.provider = provider;
    }

    void clear() {
        this.unions = new Unions();
    }

    StarterUnion unionFor(UnionKey key) {
        final DictionaryMaxlength d = provider.get();
        final Unions slots = unions;
        switch (key) {
            case S2T:
                return getOrInit(slots.s2t,
                        () -> StarterUnion.build(Arrays.asList(d.st_phrases, d.st_characters)));
            case S2T_PUNCT:
                return getOrInit(slots.s2t_punct,
                        () -> StarterUnion.build(Arrays.asList(d.st_phrases, d.st_characters, d.st_punctuations)));
            case T2S:
                return getOrInit(slots.t2s,
                        () -> StarterUnion.build(Arrays.asList(d.ts_phrases, d.ts_characters)));
            case T2S_PUNCT:
                return getOrInit(slots.t2s_punct,
                        () -> StarterUnion.build(Arrays.asList(d.ts_phrases, d.ts_characters, d.ts_punctuations)));
            case TwPhrasesOnly:
                return getOrInit(slots.tw_phrases_only,
                        () -> StarterUnion.build(Collections.singletonList(d.tw_phrases)));
            case TwVariantsPair:
                return getOrInit(slots.tw_variants_pair,
                        () -> StarterUnion.build(Arrays.asList(d.tw_variants_phrases, d.tw_variants)));
            case S2TwpR2TwTriple:
                return getOrInit(slots.s2twp_r2_tw_triple,
                        () -> StarterUnion.build(Arrays.asList(d.tw_phrases, d.tw_variants_phrases, d.tw_variants)));
            case TwPhrasesRevOnly:
                return getOrInit(slots.tw_phrases_rev_only,
                        () -> StarterUnion.build(Collections.singletonList(d.tw_phrases_rev)));
            case TwRevPair:
                return getOrInit(slots.tw_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev)));
            case Tw2SpR1TwRevTriple:
                return getOrInit(slots.tw2sp_r1_tw_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(d.tw_phrases_rev, d.tw_variants_rev_phrases, d.tw_variants_rev)));
            case HkVariantsPair:
                return getOrInit(slots.hk_variants_pair,
                        () -> StarterUnion.build(Arrays.asList(d.hk_variants_phrases, d.hk_variants)));
            case S2HkpR2HkTriple:
                return getOrInit(slots.s2hkp_r2_hk_triple,
                        () -> StarterUnion.build(Arrays.asList(d.hk_phrases, d.hk_variants_phrases, d.hk_variants)));
            case HkRevPair:
                return getOrInit(slots.hk_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(d.hk_variants_rev_phrases, d.hk_variants_rev)));
            case Hk2SpR1HkRevTriple:
                return getOrInit(slots.hk2sp_r1_hk_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(d.hk_phrases_rev, d.hk_variants_rev_phrases, d.hk_variants_rev)));
            case JpVariantsOnly:
                return getOrInit(slots.jp_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(d.jp_variants)));
            case JpRevTriple:
                return getOrInit(slots.jp_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(d.jps_phrases, d.jps_characters, d.jp_variants_rev)));
            default:
                throw new IllegalArgumentException("Unhandled UnionKey: " + key);
        }
    }

    private static StarterUnion getOrInit(AtomicReference<StarterUnion> slot,
                                          Callable<StarterUnion> build) {
        StarterUnion value = slot.get();
        if (value != null) return value;
        try {
            StarterUnion built = build.call();
            return slot.compareAndSet(null, built) ? built : slot.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final class Unions {
        final AtomicReference<StarterUnion> s2t = new AtomicReference<>();
        final AtomicReference<StarterUnion> s2t_punct = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s_punct = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_phrases_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_variants_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> s2twp_r2_tw_triple = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_phrases_rev_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_rev_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw2sp_r1_tw_rev_triple = new AtomicReference<>();
        final AtomicReference<StarterUnion> hk_variants_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> s2hkp_r2_hk_triple = new AtomicReference<>();
        final AtomicReference<StarterUnion> hk_rev_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> hk2sp_r1_hk_rev_triple = new AtomicReference<>();
        final AtomicReference<StarterUnion> jp_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> jp_rev_triple = new AtomicReference<>();
    }
}
