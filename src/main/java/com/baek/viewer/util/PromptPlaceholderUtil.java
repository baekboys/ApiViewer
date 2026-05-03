package com.baek.viewer.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {{key}} 형태 플레이스홀더 치환 */
public final class PromptPlaceholderUtil {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private PromptPlaceholderUtil() {}

    public static String apply(String template, Map<String, String> values) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String v = values != null && values.containsKey(key) ? nullToEmpty(values.get(key)) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
