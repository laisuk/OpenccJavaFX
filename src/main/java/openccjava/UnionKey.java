package openccjava;

public enum UnionKey {
    // S2T / T2S
    S2T,
    S2T_PUNCT,
    T2S,
    T2S_PUNCT,

    // TW
    TwPhrasesOnly,
    TwVariantsOnly,
    TwPhrasesRevOnly,
    TwRevPair,
    Tw2SpR1TwRevTriple,

    // HK
    HkVariantsOnly,
    HkRevPair,

    // JP
    JpVariantsOnly,
    JpRevTriple
}
