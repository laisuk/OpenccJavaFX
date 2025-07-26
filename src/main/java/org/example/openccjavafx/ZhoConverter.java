package org.example.openccjavafx;

import openccjava.OpenCC;

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
        var stripText = NON_ZHO.matcher(text).replaceAll("");
        var testText = stripText.substring(0, Math.min(stripText.length(), 50));
        var converter = new OpenCC();
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
        Map<String, String> s2tPunctuationChars = Map.of(
                "“", "「",
                "”", "」",
                "‘", "『",
                "’", "』"
        ); // Use Map.of for Java 9+

        Map<String, String> mapping = config.startsWith("s") ?
                s2tPunctuationChars :
                s2tPunctuationChars.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        String pattern = "[" + String.join("", mapping.keySet()) + "]";
        return replacePattern(inputText, pattern, mapping);
    }

    private static String replacePattern(String text, String pattern, Map<String, String> mapping) {
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, mapping.get(matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}
