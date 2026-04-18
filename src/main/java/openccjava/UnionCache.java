package openccjava;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe runtime cache for lazily built {@link StarterUnion} instances.
 * <p>
 * This cache is derived entirely from a backing {@link DictionaryMaxlength}
 * and exists only to support conversion plan preparation. It is not part of
 * the serialized dictionary model.
 * </p>
 */
final class UnionCache {
    private final DictionaryMaxlength dictionary;
    private volatile Slots slots = new Slots();

    /**
     * Creates a union cache backed by the given dictionary data.
     *
     * @param dictionary source of dictionary entries for union construction
     */
    public UnionCache(DictionaryMaxlength dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Clears all cached unions so they will be rebuilt lazily on demand.
     */
    public void clear() {
        this.slots = new Slots();
    }

    /**
     * Returns the cached or newly built union for the given key.
     *
     * @param key identifies which dictionary entries participate in the union
     * @return cached or newly built starter union
     */
    public StarterUnion get(UnionKey key) {
        final DictionaryMaxlength d = dictionary;
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
            case TwVariantsOnly:
                return getOrInit(slots.tw_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(d.tw_variants)));
            case TwPhrasesRevOnly:
                return getOrInit(slots.tw_phrases_rev_only,
                        () -> StarterUnion.build(Collections.singletonList(d.tw_phrases_rev)));
            case TwRevPair:
                return getOrInit(slots.tw_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(d.tw_variants_rev_phrases, d.tw_variants_rev)));
            case Tw2SpR1TwRevTriple:
                return getOrInit(slots.tw2sp_r1_tw_rev_triple,
                        () -> StarterUnion.build(Arrays.asList(d.tw_phrases_rev, d.tw_variants_rev_phrases, d.tw_variants_rev)));
            case HkVariantsOnly:
                return getOrInit(slots.hk_variants_only,
                        () -> StarterUnion.build(Collections.singletonList(d.hk_variants)));
            case HkRevPair:
                return getOrInit(slots.hk_rev_pair,
                        () -> StarterUnion.build(Arrays.asList(d.hk_variants_rev_phrases, d.hk_variants_rev)));
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
        StarterUnion v = slot.get();
        if (v != null) return v;
        try {
            StarterUnion built = build.call();
            return slot.compareAndSet(null, built) ? built : slot.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fixed slot container for one cache generation.
     */
    static final class Slots {
        final AtomicReference<StarterUnion> s2t = new AtomicReference<>();
        final AtomicReference<StarterUnion> s2t_punct = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s = new AtomicReference<>();
        final AtomicReference<StarterUnion> t2s_punct = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_phrases_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_phrases_rev_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw_rev_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> tw2sp_r1_tw_rev_triple = new AtomicReference<>();
        final AtomicReference<StarterUnion> hk_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> hk_rev_pair = new AtomicReference<>();
        final AtomicReference<StarterUnion> jp_variants_only = new AtomicReference<>();
        final AtomicReference<StarterUnion> jp_rev_triple = new AtomicReference<>();
    }
}

