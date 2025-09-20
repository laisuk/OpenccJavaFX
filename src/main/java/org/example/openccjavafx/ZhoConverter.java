package org.example.openccjavafx;

import openccjava.OpenCC;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ZhoConverter {
    private static final Pattern NON_ZHO = Pattern.compile("[\\p{Punct}\\sA-Za-z0-9_著]");

    public static String convert(String input, String config, Boolean punctuation) {
        OpenCC converter = new OpenCC();
        converter.setConfig(config);
        return converter.convert(input, punctuation);
    }

    public static int zhoCheck(String text) {
        if (text.isEmpty())
            return 0;
//        var stripText = text.replaceAll("[\\p{Punct}\\sA-Za-z0-9]", "");
        String stripText = NON_ZHO.matcher(text).replaceAll("");
        String testText = stripText.substring(0, Math.min(stripText.length(), 50));
        OpenCC converter = new OpenCC();
        converter.setConfig("t2s");
        if (!testText.equals(converter.convert(testText, false))) {
            return 1; // traditional
        }
        converter.setConfig("s2t");
        if (!testText.equals(converter.convert(testText, false))) {
            return 2; // simplified
        }
        return 0; // unknown or mixed
    } // zhoCheck

    public static String convertPunctuation(String inputText, String config) {
        // Build map manually (instead of Map.of)
        Map<String, String> s2tPunctuationChars = new HashMap<>();
        s2tPunctuationChars.put("“", "「");
        s2tPunctuationChars.put("”", "」");
        s2tPunctuationChars.put("‘", "『");
        s2tPunctuationChars.put("’", "』");

        // Choose mapping direction
        Map<String, String> mapping;
        if (config.startsWith("s")) {
            mapping = s2tPunctuationChars;
        } else {
            mapping = s2tPunctuationChars.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        }

        // Build regex pattern from keys
        StringBuilder patternBuilder = new StringBuilder("[");
        for (String key : mapping.keySet()) {
            patternBuilder.append(key);
        }
        patternBuilder.append("]");
        String pattern = patternBuilder.toString();

        return replacePattern(inputText, pattern, mapping);
    }

    private static String replacePattern(String text, String pattern, Map<String, String> mapping) {
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = mapping.get(matcher.group());
            if (replacement == null) {
                replacement = matcher.group(); // fallback: keep original
            }
            // Escape replacement string for appendReplacement
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}
