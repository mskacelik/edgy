package org.acme.edgy.runtime.api.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SegmentUtils {

    public static final String SUFFIX = "suffix";
    public static final String REQUEST_URI = "requestURI";

    public static final char OPEN_BRACE = '{';
    private static final char CLOSE_BRACE = '}';
    private static final char WILDCARD = '*';
    private static final char COLON = ':';
    private static final char SLASH = '/';
    private static final char QUERY = '?';

    private static final String DEFAULT_SEGMENT_REGEX = "[^/]+";
    private static final String REGEX_ANCHOR_START = "^";
    private static final String REGEX_ANCHOR_END = "$";
    private static final String OPTIONAL_TRAILING_SLASH = "/?";
    private static final String RESERVED_EXPANSION_PREFIX = "{+";

    private static final Pattern SIMPLE_EXPANSION_PATTERN = Pattern.compile("\\{(?!\\+)([^}]+)}");
    // based on java.util.regex.Pattern for named groups
    private static final Pattern VALID_SEGMENT_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");
    private static final Pattern NAMED_GROUP_PATTERN = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

    private SegmentUtils() {
    }

    public record CompiledPath(List<String> groupNames, Pattern compiledPattern) {
    }

    /**
     * Transforms a BASIC mode path into a strict regex pattern with named groups.
     * <p>
     * Supports Jakarta REST syntax for path parameters: {@code {name}} and
     * {@code {name:regex}}.
     * A trailing {@code /*} is interpreted as a wildcard capturing everything after
     * the prefix into a named group {@code suffix}.
     * Duplicate segment names produce backreferences; conflicting regex definitions
     * throw.
     *
     * <pre>
     * /hello/./{a}/{b:[a-z]+}/*
     * </pre>
     *
     * becomes
     *
     * <pre>
     * ^/hello/\./(?&lt;a&gt;[^/]+)/(?&lt;b&gt;[a-z]+)/(?&lt;suffix&gt;.*)$
     * </pre>
     */
    public static CompiledPath transform(String path) {
        StringBuilder regex = new StringBuilder(REGEX_ANCHOR_START);
        List<String> groupNames = new ArrayList<>();
        Set<String> seenSegments = new HashSet<>();
        Map<String, String> seenRegexPatterns = new HashMap<>();

        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == OPEN_BRACE) {
                i = appendSegment(path, i, regex, groupNames, seenSegments, seenRegexPatterns);
            } else if (c == WILDCARD && isTrailingWildcard(path, i)) {
                groupNames.add(SUFFIX);
                regex.append("(?<").append(SUFFIX).append(">").append(".*").append(")");
                i++;
            } else {
                regex.append(escapeRegexChar(c));
                i++;
            }
        }

        if (!path.endsWith(String.valueOf(SLASH)) && !path.endsWith(String.valueOf(WILDCARD))) {
            regex.append(OPTIONAL_TRAILING_SLASH);
        }
        regex.append(REGEX_ANCHOR_END);

        return new CompiledPath(groupNames, Pattern.compile(regex.toString()));
    }

    /**
     * Extracts named group names from a raw regex pattern (for REGEXP mode).
     * Scans for {@code (?<name>} patterns and returns the group names found.
     */
    public static CompiledPath fromRegexp(String regex) {
        List<String> groupNames = new ArrayList<>();
        Matcher m = NAMED_GROUP_PATTERN.matcher(regex);
        while (m.find()) {
            groupNames.add(m.group(1));
        }
        if (groupNames.isEmpty()) {
            return null;
        }
        return new CompiledPath(groupNames, Pattern.compile(regex));
    }

    /**
     * Extracts path variable values from a request URI using a pre-compiled
     * transformed path with named groups.
     */
    public static Map<String, String> extractPathVariables(CompiledPath transformedPath, String uri) {
        if (transformedPath == null || transformedPath.groupNames().isEmpty()) {
            return Map.of();
        }
        int queryIdx = uri.indexOf(QUERY);
        if (queryIdx >= 0) {
            uri = uri.substring(0, queryIdx);
        }
        Matcher m = transformedPath.compiledPattern().matcher(uri);
        if (!m.matches()) {
            return Map.of();
        }
        Map<String, String> variables = new HashMap<>();
        for (String name : transformedPath.groupNames()) {
            String value = m.group(name);
            if (value != null) {
                variables.put(name, value);
            }
        }
        return variables;
    }

    /**
     * Converts simple UriTemplate variables {@code {var}} to reserved expansion
     * {@code {+var}}
     * to prevent double-encoding of already URL-encoded values extracted from
     * request URIs.
     */
    public static String toReservedExpansion(String originPath) {
        return SIMPLE_EXPANSION_PATTERN.matcher(originPath)
                .replaceAll(RESERVED_EXPANSION_PREFIX + "$1}");
    }

    /**
     * Returns true if the path contains segments or wildcards that require
     * regex-based routing.
     */
    public static boolean needsRegexRouting(String path) {
        return path.indexOf(OPEN_BRACE) >= 0 || path.indexOf(WILDCARD) >= 0;
    }

    private static int appendSegment(String path, int openIndex, StringBuilder regex,
            List<String> groupNames,
            Set<String> seenSegments,
            Map<String, String> seenRegexPatterns) {
        int closeBrace = findMatchingBrace(path, openIndex);
        String content = path.substring(openIndex + 1, closeBrace);
        int colonIdx = content.indexOf(COLON);
        String name = colonIdx >= 0 ? content.substring(0, colonIdx) : content;
        String segRegex = colonIdx >= 0 ? content.substring(colonIdx + 1) : DEFAULT_SEGMENT_REGEX;

        validateSegmentName(name, path);

        if (seenSegments.contains(name)) {
            String prevRegex = seenRegexPatterns.get(name);
            if (!prevRegex.equals(segRegex)) {
                throw new IllegalArgumentException(
                        "Conflicting regex for segment '" + name + "' in path '" + path
                                + "': '" + prevRegex + "' vs '" + segRegex + "'");
            }
            regex.append("\\k<").append(name).append(">");
        } else {
            seenSegments.add(name);
            seenRegexPatterns.put(name, segRegex);
            groupNames.add(name);
            regex.append("(?<").append(name).append(">").append(segRegex).append(")");
        }
        return closeBrace + 1;
    }

    private static void validateSegmentName(String name, String path) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Segment name must not be empty in path: " + path);
        }
        if (!VALID_SEGMENT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Segment name '" + name + "' is not a valid identifier in path: " + path
                            + ". Names must start with a letter and contain only letters and digits.");
        }
    }

    private static boolean isTrailingWildcard(String path, int index) {
        return index == path.length() - 1 && index > 0 && path.charAt(index - 1) == SLASH;
    }

    private static int findMatchingBrace(String path, int openIndex) {
        int depth = 1;
        for (int j = openIndex + 1; j < path.length(); j++) {
            if (path.charAt(j) == OPEN_BRACE)
                depth++;
            if (path.charAt(j) == CLOSE_BRACE)
                depth--;
            if (depth == 0)
                return j;
        }
        throw new IllegalArgumentException("Unmatched '" + OPEN_BRACE + "' in path: " + path);
    }

    private static String escapeRegexChar(char c) {
        return switch (c) {
            case '.', '+', '?', '(', ')', '[', ']', '\\', '^', '$', '|', '*', '}' -> "\\" + c;
            default -> String.valueOf(c);
        };
    }
}
