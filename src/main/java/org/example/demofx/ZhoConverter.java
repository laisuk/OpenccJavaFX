package org.example.demofx;

import opencc.OpenCC;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;


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

    public static String convertPunctuation(String inputText, String config) {
        Map<String, String> s2tPunctuationChars = new HashMap<>();
        s2tPunctuationChars.put("“", "「");
        s2tPunctuationChars.put("”", "」");
        s2tPunctuationChars.put("‘", "『");
        s2tPunctuationChars.put("’", "』");

        // Fancy join method: Not used
        String s2tCharsJoin = s2tPunctuationChars.entrySet()
                .stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(joining(", "));
//        System.out.println("结果" + s2tCharsJoin);

        String pattern;
        if (config.startsWith("s")) {
            pattern = "[" + String.join("", s2tPunctuationChars.keySet()) + "]";
            return replacePattern(inputText, pattern, s2tPunctuationChars);
        } else {
            Map<String, String> t2sPunctuationChars = new HashMap<>();
            for (Map.Entry<String, String> entry : s2tPunctuationChars.entrySet()) {
                t2sPunctuationChars.put(entry.getValue(), entry.getKey());
            }
            pattern = "[" + String.join("", t2sPunctuationChars.keySet()) + "]";
            return replacePattern(inputText, pattern, t2sPunctuationChars);
        }
    } // convertPunctuation

    private static String replacePattern(String inputText, String pattern, Map<String, String> charMapping) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(inputText);
        StringBuilder outputText = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(outputText, Matcher.quoteReplacement(charMapping.get(m.group(0))));
        }
        m.appendTail(outputText);
        return outputText.toString();
    }
}
