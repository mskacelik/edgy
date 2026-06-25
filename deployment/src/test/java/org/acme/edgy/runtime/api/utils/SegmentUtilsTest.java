package org.acme.edgy.runtime.api.utils;

import static org.acme.edgy.runtime.api.utils.SegmentUtils.extractPathVariables;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.fromRegexp;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.needsRegexRouting;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.toReservedExpansion;
import static org.acme.edgy.runtime.api.utils.SegmentUtils.transform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.acme.edgy.runtime.api.utils.SegmentUtils.CompiledPath;
import org.assertj.core.api.SoftAssertions;
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
        assertThat(tp.compiledPattern().pattern()).isEqualTo("^/my/(?<a>[a-z]+)/\\k<a>/path/?$");
        // only first occurrence creates a group
        assertThat(tp.groupNames()).isEqualTo(List.of("a"));
    }

    @Test
    void testTransform_backreference_simpleSegments() {
        CompiledPath tp = transform("/my/{a}/{a}/path");
        assertThat(tp.compiledPattern().pattern()).isEqualTo("^/my/(?<a>[^/]+)/\\k<a>/path/?$");
        assertThat(tp.groupNames()).isEqualTo(List.of("a"));
    }

    @Test
    void testTransform_backreference_conflictingRegex() {
        assertThatThrownBy(() -> transform("/my/{a:[a-z]+}/{a:\\d+}/path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting regex")
                .hasMessageContaining("a");
    }

    @Test
    void testTransform_emptySegmentName() {
        assertThatThrownBy(() -> transform("/{:regex}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTransform_unmatchedBrace() {
        assertThatThrownBy(() -> transform("/{name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTransform_invalidSegmentName_underscore() {
        assertThatThrownBy(() -> transform("/{user_id}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTransform_invalidSegmentName_leadingDigit() {
        assertThatThrownBy(() -> transform("/{1}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testExtractPathVariables() {
        CompiledPath tp = transform("/users/{userId}/orders/{orderId}");
        assertThat(extractPathVariables(tp, "/users/123/orders/456"))
                .isEqualTo(Map.of("userId", "123", "orderId", "456"));
        assertThat(extractPathVariables(tp, "/users/123/orders/456/"))
                .isEqualTo(Map.of("userId", "123", "orderId", "456"));
    }

    @Test
    void testExtractPathVariables_customRegex() {
        CompiledPath tp = transform("/users/{userId:\\d+}");
        assertThat(extractPathVariables(tp, "/users/123")).isEqualTo(Map.of("userId", "123"));
        assertThat(extractPathVariables(tp, "/users/abc")).isEqualTo(Map.of());
    }

    @Test
    void testExtractPathVariables_wildcard() {
        CompiledPath tp = transform("/api/*");
        assertThat(extractPathVariables(tp, "/api/foo/bar"))
                .isEqualTo(Map.of(SegmentUtils.SUFFIX, "foo/bar"));
    }

    @Test
    void testExtractPathVariables_stripsQueryString() {
        CompiledPath tp = transform("/users/{userId}");
        assertThat(extractPathVariables(tp, "/users/123?foo=bar"))
                .isEqualTo(Map.of("userId", "123"));
    }

    @Test
    void testExtractPathVariables_nullCompiledPath() {
        assertThat(extractPathVariables(null, "/anything")).isEqualTo(Map.of());
    }

    @Test
    void testExtractPathVariables_joined() {
        CompiledPath tp = transform("/a/{a}-{b}/c");
        assertThat(extractPathVariables(tp, "/a/foo-bar/c"))
                .isEqualTo(Map.of("a", "foo", "b", "bar"));
    }

    @Test
    void testFromRegexp_withNamedGroups() {
        CompiledPath tp = fromRegexp("/user/(?<userId>[0-9]+)/profile");
        assertThat(tp).isNotNull();
        assertThat(tp.groupNames()).isEqualTo(List.of("userId"));
        assertThat(extractPathVariables(tp, "/user/123/profile"))
                .isEqualTo(Map.of("userId", "123"));
    }

    @Test
    void testFromRegexp_multipleNamedGroups() {
        CompiledPath tp = fromRegexp("/(?<type>[a-z]+)/(?<id>[0-9]+)");
        assertThat(tp).isNotNull();
        assertThat(tp.groupNames()).isEqualTo(List.of("type", "id"));
        assertThat(extractPathVariables(tp, "/users/42"))
                .isEqualTo(Map.of("type", "users", "id", "42"));
    }

    @Test
    void testFromRegexp_noNamedGroups() {
        assertThat(fromRegexp("/user/[0-9]+/profile")).isNull();
    }

    @Test
    void testToReservedExpansion() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(toReservedExpansion("{var}")).isEqualTo("{+var}");
            softly.assertThat(toReservedExpansion("{+var}")).isEqualTo("{+var}");
            softly.assertThat(toReservedExpansion("/test/{a}/{b}")).isEqualTo("/test/{+a}/{+b}");
            softly.assertThat(toReservedExpansion("/test/{requestURI}")).isEqualTo("/test/{+requestURI}");
            softly.assertThat(toReservedExpansion("/no-vars")).isEqualTo("/no-vars");
        });
    }

    @Test
    void testNeedsRegexRouting() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(needsRegexRouting("/hello")).isFalse();
            softly.assertThat(needsRegexRouting("/hello/world")).isFalse();
            softly.assertThat(needsRegexRouting("/hello/./world")).isFalse();
            softly.assertThat(needsRegexRouting("/hello/{name}")).isTrue();
            softly.assertThat(needsRegexRouting("/hello/*")).isTrue();
            softly.assertThat(needsRegexRouting("/hello/{name}/*")).isTrue();
            softly.assertThat(needsRegexRouting("/hello/{name:[a-z]+}")).isTrue();
        });
    }

    private void assertTransform(String path, String expectedRegex, List<String> expectedGroupNames) {
        CompiledPath tp = transform(path);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(tp.compiledPattern().pattern()).isEqualTo(expectedRegex);
            softly.assertThat(tp.groupNames()).isEqualTo(expectedGroupNames);
        });
    }
}
