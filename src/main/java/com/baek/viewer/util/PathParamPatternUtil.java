package com.baek.viewer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring MVC 스타일 경로 변수 {@code {name}} 를 {@code api_path}에서 추출.
 */
public final class PathParamPatternUtil {

    private static final Pattern SEGMENT = Pattern.compile("\\{([^{}]+)\\}");

    private PathParamPatternUtil() {}

    /**
     * @return 등장 순서대로 {@code "{a}, {b}"} 형태, 없으면 null
     */
    public static String fromApiPath(String apiPath) {
        if (apiPath == null || apiPath.isBlank()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        Matcher m = SEGMENT.matcher(apiPath);
        while (m.find()) {
            parts.add("{" + m.group(1) + "}");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(", ", parts);
    }
}
