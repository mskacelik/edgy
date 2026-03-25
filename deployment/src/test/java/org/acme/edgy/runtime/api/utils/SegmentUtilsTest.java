package org.acme.edgy.runtime.api.utils;

import static org.acme.edgy.runtime.api.utils.SegmentUtils.extractPathVariables;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.fromRegexp;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.needsRegexRouting;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.toReservedExpansion;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.transform;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.acme.edgy.runtime.api.utils.SegmentUtils.CompiledPath;
import org.junit.jupiter.api.Test;

class SegmentUtilsTest {

    @Test
    void testTransform_simpleSegments() {
        assertTransform("/users/{userId}", "^/users/(?<userId>[^/]+)/?$", List.of("userId"));
        assertTransform("/users/{userId}/orders/{orderId}",
                "^/users/(?<userId>[^/]+)/orders/(?<orderId>[^/]+)/?$", List.of("userId", "orderId"));
    }

    @Test
    void testTransform_customRegex_jakartaRestSyntax() {
        assertTransform("/users/{userId:\\d+}", "^/users/(?<userId>\\d+)/?$", List.of("userId"));
        assertTransform("/files/{hash:[a-f0-9]+}", "^/files/(?<hash>[a-f0-9]+)/?$", List.of("hash"));
        assertTransform("/users/{userId:\\d+}/orders/{orderId:\\d+}",
                "^/users/(?<userId>\\d+)/orders/(?<orderId>\\d+)/?$", List.of("userId", "orderId"));
    }

    @Test
    void testTransform_nestedBracesInRegex() {
        assertTransform("/users/{id:\\d{3,5}}", "^/users/(?<id>\\d{3,5})/?$", List.of("id"));
    }

    @Test
    void testTransform_mixedSimpleAndCustomRegex() {
        assertTransform("/users/{name}/orders/{orderId:\\d+}",
                "^/users/(?<name>[^/]+)/orders/(?<orderId>\\d+)/?$", List.of("name", "orderId"));
    }

    @Test
    void testTransform_wildcard() {
        assertTransform("/api/*", "^/api/(?<suffix>.*)$",
                List.of(SegmentUtils.SUFFIX));
        assertTransform("/api/{name}/*", "^/api/(?<name>[^/]+)/(?<suffix>.*)$",
                List.of("name", SegmentUtils.SUFFIX));
    }

    @Test
    void testTransform_literalDotEscaping() {
        assertTransform("/hello/./{a}", "^/hello/\\./(?<a>[^/]+)/?$", List.of("a"));
    }

    @Test
    void testTransform_literalAsteriskEscaping() {
        // * not at end (not after /) is escaped as literal
        assertTransform("/escaped-regex/.*/{a}",
                "^/escaped-regex/\\.\\*/(?<a>[^/]+)/?$", List.of("a"));
    }

    @Test
    void testTransform_joinedSegments() {
        assertTransform("/joined/{a}-{b}",
                "^/joined/(?<a>[^/]+)-(?<b>[^/]+)/?$", List.of("a", "b"));
    }

    @Test
    void testTransform_trailingSlash() {
        // paths ending with / do NOT get optional /? appended
        assertTransform("/users/{userId}/", "^/users/(?<userId>[^/]+)/$", List.of("userId"));
    }

    @Test
    void testTransform_backreference_sameRegex() {
        CompiledPath tp = transform("/my/{a:[a-z]+}/{a:[a-z]+}/path");
        assertEquals("^/my/(?<a>[a-z]+)/\\k<a>/path/?$", tp.compiledPattern().pattern());
        // only first occurrence creates a group
        assertEquals(List.of("a"), tp.groupNames());
    }

    @Test
    void testTransform_backreference_simpleSegments() {
        CompiledPath tp = transform("/my/{a}/{a}/path");
        assertEquals("^/my/(?<a>[^/]+)/\\k<a>/path/?$", tp.compiledPattern().pattern());
        assertEquals(List.of("a"), tp.groupNames());
    }

    @Test
    void testTransform_backreference_conflictingRegex() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transform("/my/{a:[a-z]+}/{a:\\d+}/path"));
        assertThat(ex.getMessage(), containsString("Conflicting regex"));
        assertThat(ex.getMessage(), containsString("a"));
    }

    @Test
    void testTransform_emptySegmentName() {
        assertThrows(IllegalArgumentException.class, () -> transform("/{:regex}"));
    }

    @Test
    void testTransform_unmatchedBrace() {
        assertThrows(IllegalArgumentException.class, () -> transform("/{name"));
    }

    @Test
    void testTransform_invalidSegmentName_underscore() {
        assertThrows(IllegalArgumentException.class, () -> transform("/{user_id}"));
    }

    @Test
    void testTransform_invalidSegmentName_leadingDigit() {
        assertThrows(IllegalArgumentException.class, () -> transform("/{1}"));
    }

    @Test
    void testExtractPathVariables() {
        CompiledPath tp = transform("/users/{userId}/orders/{orderId}");
        assertEquals(Map.of("userId", "123", "orderId", "456"),
                extractPathVariables(tp, "/users/123/orders/456"));
        assertEquals(Map.of("userId", "123", "orderId", "456"),
                extractPathVariables(tp, "/users/123/orders/456/"));
    }

    @Test
    void testExtractPathVariables_customRegex() {
        CompiledPath tp = transform("/users/{userId:\\d+}");
        assertEquals(Map.of("userId", "123"), extractPathVariables(tp, "/users/123"));
        assertEquals(Map.of(), extractPathVariables(tp, "/users/abc"));
    }

    @Test
    void testExtractPathVariables_wildcard() {
        CompiledPath tp = transform("/api/*");
        assertEquals(Map.of(SegmentUtils.SUFFIX, "foo/bar"),
                extractPathVariables(tp, "/api/foo/bar"));
    }

    @Test
    void testExtractPathVariables_stripsQueryString() {
        CompiledPath tp = transform("/users/{userId}");
        assertEquals(Map.of("userId", "123"),
                extractPathVariables(tp, "/users/123?foo=bar"));
    }

    @Test
    void testExtractPathVariables_nullCompiledPath() {
        assertEquals(Map.of(), extractPathVariables(null, "/anything"));
    }

    @Test
    void testExtractPathVariables_joined() {
        CompiledPath tp = transform("/a/{a}-{b}/c");
        assertEquals(Map.of("a", "foo", "b", "bar"),
                extractPathVariables(tp, "/a/foo-bar/c"));
    }

    @Test
    void testFromRegexp_withNamedGroups() {
        CompiledPath tp = fromRegexp("/user/(?<userId>[0-9]+)/profile");
        assertNotNull(tp);
        assertEquals(List.of("userId"), tp.groupNames());
        assertEquals(Map.of("userId", "123"),
                extractPathVariables(tp, "/user/123/profile"));
    }

    @Test
    void testFromRegexp_multipleNamedGroups() {
        CompiledPath tp = fromRegexp("/(?<type>[a-z]+)/(?<id>[0-9]+)");
        assertNotNull(tp);
        assertEquals(List.of("type", "id"), tp.groupNames());
        assertEquals(Map.of("type", "users", "id", "42"),
                extractPathVariables(tp, "/users/42"));
    }

    @Test
    void testFromRegexp_noNamedGroups() {
        assertNull(fromRegexp("/user/[0-9]+/profile"));
    }

    @Test
    void testToReservedExpansion() {
        assertEquals("{+var}", toReservedExpansion("{var}"));
        assertEquals("{+var}", toReservedExpansion("{+var}"));
        assertEquals("/test/{+a}/{+b}", toReservedExpansion("/test/{a}/{b}"));
        assertEquals("/test/{+requestURI}", toReservedExpansion("/test/{requestURI}"));
        assertEquals("/no-vars", toReservedExpansion("/no-vars"));
    }

    @Test
    void testNeedsRegexRouting() {
        assertFalse(needsRegexRouting("/hello"));
        assertFalse(needsRegexRouting("/hello/world"));
        assertFalse(needsRegexRouting("/hello/./world"));
        assertTrue(needsRegexRouting("/hello/{name}"));
        assertTrue(needsRegexRouting("/hello/*"));
        assertTrue(needsRegexRouting("/hello/{name}/*"));
        assertTrue(needsRegexRouting("/hello/{name:[a-z]+}"));
    }

    private void assertTransform(String path, String expectedRegex, List<String> expectedGroupNames) {
        CompiledPath tp = transform(path);
        assertEquals(expectedRegex, tp.compiledPattern().pattern());
        assertEquals(expectedGroupNames, tp.groupNames());
    }
}
