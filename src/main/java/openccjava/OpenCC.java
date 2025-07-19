package openccjava;

import openccjava.DictionaryMaxlength.DictEntry;

import java.io.File;
import java.util.*;
import java.util.stream.IntStream;

public class OpenCC {
    private final DictionaryMaxlength dictionary;
    private final Map<String, DictRefs> configCache = new HashMap<>();
    private final Set<Character> delimiters = DictRefs.DELIMITERS;
    private String config = "s2t";
    private String lastError;

    private static final int MAX_SB_CAPACITY = 1024;
    private static final ThreadLocal<StringBuilder> threadLocalSb =
            ThreadLocal.withInitial(() -> new StringBuilder(MAX_SB_CAPACITY));

    public OpenCC() {
        this("s2t");
    }

    public OpenCC(String config) {
        try {
            this.dictionary = new File("dicts/dictionary_maxlength.json").exists()
                    ? DictionaryMaxlength.fromJson("dicts/dictionary_maxlength.json")
                    : DictionaryMaxlength.fromDicts();

        } catch (Exception e) {
            this.lastError = e.getMessage();
            throw new RuntimeException("Failed to load text dictionaries", e);
        }

        setConfig(config);
    }

    public void setConfig(String config) {
        if (getSupportedConfigs().contains(config)) {
            this.config = config;
        } else {
            this.lastError = "Invalid config: " + config;
            this.config = "s2t";
        }
    }

    public String getConfig() {
        return config;
    }

    public String getLastError() {
        return lastError;
    }

    public static List<String> getSupportedConfigs() {
        return List.of("s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
                "t2tw", "tw2t", "t2twp", "tw2tp", "t2hk", "hk2t", "t2jp", "jp2t");
    }

    public String convert(String input, boolean punctuation) {
        return switch (config) {
            case "s2t" -> s2t(input, punctuation);
            case "t2s" -> t2s(input, punctuation);
            case "s2tw" -> s2tw(input, punctuation);
            case "tw2s" -> tw2s(input, punctuation);
            case "s2twp" -> s2twp(input, punctuation);
            case "tw2sp" -> tw2sp(input, punctuation);
            case "s2hk" -> s2hk(input, punctuation);
            case "hk2s" -> hk2s(input, punctuation);
            case "t2tw" -> t2tw(input);
            case "t2twp" -> t2twp(input);
            case "tw2t" -> tw2t(input);
            case "tw2tp" -> tw2tp(input);
            case "t2hk" -> t2hk(input);
            case "hk2t" -> hk2t(input);
            case "t2jp" -> t2jp(input);
            case "jp2t" -> jp2t(input);
            default -> {
                lastError = "Unsupported config: " + getConfig();
                yield getLastError();
            }
        };
    }

    private DictRefs getDictRefs(String key) {
        if (configCache.containsKey(key)) return configCache.get(key);
        var d = dictionary;
        DictRefs refs = switch (key) {
            case "s2t" -> new DictRefs(List.of(d.st_phrases, d.st_characters));
            case "t2s" -> new DictRefs(List.of(d.ts_phrases, d.ts_characters));
            case "s2tw" -> new DictRefs(List.of(d.st_phrases, d.st_characters)).withRound2(List.of(d.tw_variants));
            case "tw2s" ->
                    new DictRefs(List.of(d.tw_variants_rev_phrases, d.tw_variants_rev)).withRound2(List.of(d.ts_phrases, d.ts_characters));
            case "s2twp" ->
                    new DictRefs(List.of(d.st_phrases, d.st_characters)).withRound2(List.of(d.tw_phrases)).withRound3(List.of(d.tw_variants));
            case "tw2sp" ->
                    new DictRefs(List.of(d.tw_phrases_rev, d.tw_variants_rev_phrases, d.tw_variants_rev)).withRound2(List.of(d.ts_phrases, d.ts_characters));
            case "s2hk" -> new DictRefs(List.of(d.st_phrases, d.st_characters)).withRound2(List.of(d.hk_variants));
            case "hk2s" ->
                    new DictRefs(List.of(d.hk_variants_rev_phrases, d.hk_variants_rev)).withRound2(List.of(d.ts_phrases, d.ts_characters));
            default -> null;
        };
        if (refs != null) configCache.put(key, refs);
        return refs;
    }

    public String segmentReplace(String text, List<DictEntry> dicts, int maxLength) {
        if (text == null || text.isEmpty()) return text;

        List<int[]> ranges = getSplitRanges(text, true);
        int numSegments = ranges.size();

        if (numSegments == 1 &&
                ranges.get(0)[0] == 0 &&
                ranges.get(0)[1] == text.length()) {
            return convertSegment(text, dicts, maxLength); // Fast path
        }

        // Use parallelism if input is large enough
        boolean useParallel = text.length() > 10_000 || numSegments > 100;

        if (useParallel) {
            String[] segments = new String[numSegments];

            IntStream.range(0, numSegments).parallel().forEach(i -> {
                int[] range = ranges.get(i);
                String segment = text.substring(range[0], range[1]);
                segments[i] = convertSegment(segment, dicts, maxLength);
            });

            // Reconstruct result
            StringBuilder sb = new StringBuilder(text.length());
            for (String seg : segments) {
                sb.append(seg);
            }

            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(text.length());
            for (int[] range : ranges) {
                String segment = text.substring(range[0], range[1]);
                sb.append(convertSegment(segment, dicts, maxLength));
            }
            return sb.toString();
        }
    }

    public String convertSegment(String segment, List<DictEntry> dicts, int maxLength) {
        if (segment.length() == 1 && delimiters.contains(segment.charAt(0))) {
            return segment;
        }

        int segLen = segment.length();
        StringBuilder sb = threadLocalSb.get();
        sb.setLength(0); // reset for reuse

        int i = 0;
        while (i < segLen) {
            int bestLen = 0;
            String bestMatch = null;
            int maxScanLen = Math.min(maxLength, segLen - i);

            for (int len = maxScanLen; len > 0; len--) {
                final int end = i + len;
                String word = segment.substring(i, end);
                for (DictEntry entry : dicts) {
                    if (entry.maxLength < len) continue;

                    String value = entry.dict.get(word);
                    if (value != null) {
                        bestMatch = value;
                        bestLen = len;
                        break; // found, break out of dicts loop
                    }
                }
                if (bestMatch != null) break; // found, break out of len loop
            }

            if (bestMatch != null) {
                sb.append(bestMatch);
                i += bestLen;
            } else {
                sb.append(segment.charAt(i));
                i++;
            }
        }

        return sb.toString();
    }

    public List<int[]> getSplitRanges(String text, boolean inclusive) {
        List<int[]> result = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < text.length(); i++) {
            if (delimiters.contains(text.charAt(i))) {
                if (inclusive) {
                    result.add(new int[]{start, i + 1});
                } else {
                    if (i > start) {
                        result.add(new int[]{start, i});     // before delimiter
                    }
                    result.add(new int[]{i, i + 1});         // delimiter itself
                }
                start = i + 1;
            }
        }

        if (start < text.length()) {
            result.add(new int[]{start, text.length()});
        }

        return result;
    }


    public String s2t(String input, boolean punctuation) {
        var refs = getDictRefs("s2t");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_S2T_MAP) : output;
    }

    public String t2s(String input, boolean punctuation) {
        var refs = getDictRefs("t2s");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_T2S_MAP) : output;
    }

    public String s2tw(String input, boolean punctuation) {
        var refs = getDictRefs("s2tw");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_S2T_MAP) : output;
    }

    public String tw2s(String input, boolean punctuation) {
        var refs = getDictRefs("tw2s");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_T2S_MAP) : output;
    }

    public String s2twp(String input, boolean punctuation) {
        var refs = getDictRefs("s2twp");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_S2T_MAP) : output;
    }

    public String tw2sp(String input, boolean punctuation) {
        var refs = getDictRefs("tw2sp");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_T2S_MAP) : output;
    }

    public String s2hk(String input, boolean punctuation) {
        var refs = getDictRefs("s2hk");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_S2T_MAP) : output;
    }

    public String hk2s(String input, boolean punctuation) {
        var refs = getDictRefs("hk2s");
        if (refs == null) return input;
        String output = refs.applySegmentReplace(input, this::segmentReplace);
        return punctuation ? translatePunctuation(output, DictRefs.PUNCT_T2S_MAP) : output;
    }

    public String t2tw(String input) {
        var refs = new DictRefs(List.of(dictionary.tw_variants));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String t2twp(String input) {
        var refs = new DictRefs(List.of(dictionary.tw_phrases))
                .withRound2(List.of(dictionary.tw_variants));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String tw2t(String input) {
        var refs = new DictRefs(List.of(dictionary.tw_variants_rev_phrases, dictionary.tw_variants_rev));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String tw2tp(String input) {
        var refs = new DictRefs(List.of(dictionary.tw_variants_rev_phrases, dictionary.tw_variants_rev))
                .withRound2(List.of(dictionary.tw_phrases_rev));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String t2hk(String input) {
        var refs = new DictRefs(List.of(dictionary.hk_variants));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String hk2t(String input) {
        var refs = new DictRefs(List.of(dictionary.hk_variants_rev_phrases, dictionary.hk_variants_rev));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String t2jp(String input) {
        var refs = new DictRefs(List.of(dictionary.jp_variants));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String jp2t(String input) {
        var refs = new DictRefs(List.of(dictionary.jps_phrases, dictionary.jps_characters, dictionary.jp_variants_rev));
        return refs.applySegmentReplace(input, this::segmentReplace);
    }

    public String st(String input) {
        return convertSegment(input, List.of(dictionary.st_characters), 1);
    }

    public String ts(String input) {
        return convertSegment(input, List.of(dictionary.ts_characters), 1);
    }

    public int zhoCheck(String input) {
        if (input == null || input.isEmpty()) return 0;
        String stripped = DictRefs.STRIP_REGEX.matcher(input).replaceAll("");
        stripped = stripped.length() >= 100 ? stripped.substring(0, 100) : stripped;
        int limit = DictRefs.findMaxUtf8Length(stripped, 200);
        String slice = stripped.substring(0, Math.min(limit, stripped.length()));
        if (!slice.equals(ts(slice))) return 1;
        if (!slice.equals(st(slice))) return 2;
        return 0;
    }

    private String translatePunctuation(String input, Map<Character, Character> map) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            sb.append(map.getOrDefault(c, c));
        }
        return sb.toString();
    }
}
