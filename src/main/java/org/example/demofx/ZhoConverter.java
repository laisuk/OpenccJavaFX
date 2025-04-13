package org.example.demofx;

import opencc.OpenCC;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ZhoConverter {
    public static String convert(String input, String config) {
        OpenCC converter = new OpenCC();
        converter.setConversion(config);
        return converter.convert(input);
    }

    public static int zhoCheck(String text) {
        if (text.isEmpty())
            return 0;
        var stripText = text.replaceAll("[\\p{Punct}\\sA-Za-z0-9]", "");
        var testText = stripText.length() > 50 ? stripText.substring(0, 50) : stripText;
        var converter = new OpenCC();
        converter.setConversion("t2s");
        if (!testText.equals(converter.convert(testText))) {
            return 1;
        } else {
            converter.setConversion("s2t");
            if (!testText.equals(converter.convert(testText))) {
                return 2;
            } else {
                return 0;
            }
        }
    } // zhoCheck

//    public static String convertPunctuation(String inputText, String config) {
//        Map<String, String> s2tPunctuationChars = new HashMap<>();
//        s2tPunctuationChars.put("“", "「");
//        s2tPunctuationChars.put("”", "」");
//        s2tPunctuationChars.put("‘", "『");
//        s2tPunctuationChars.put("’", "』");
//
//        // Fancy join method: Not used
//        String s2tCharsJoin = s2tPunctuationChars.entrySet()
//                .stream()
//                .map(e -> e.getKey() + ":" + e.getValue())
//                .collect(joining(", "));
//
//        String pattern;
//        if (config.startsWith("s")) {
//            pattern = "[" + String.join("", s2tPunctuationChars.keySet()) + "]";
//            return replacePattern(inputText, pattern, s2tPunctuationChars);
//        } else {
//            Map<String, String> t2sPunctuationChars = new HashMap<>();
//            for (Map.Entry<String, String> entry : s2tPunctuationChars.entrySet()) {
//                t2sPunctuationChars.put(entry.getValue(), entry.getKey());
//            }
//            pattern = "[" + String.join("", t2sPunctuationChars.keySet()) + "]";
//            return replacePattern(inputText, pattern, t2sPunctuationChars);
//        }
//    } // convertPunctuation

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
