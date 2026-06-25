package org.acme.edgy.runtime.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class OriginTest {

    @Test
    void checkBasicSpec() {
        Origin spec = Origin.of("origin", "https://my.api.private/backend");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec.identifier()).isEqualTo("origin");
            softly.assertThat(spec.protocol()).isEqualTo(Protocol.https);
            softly.assertThat(spec.host()).isEqualTo("my.api.private");
            softly.assertThat(spec.port()).isEqualTo(8080);
            softly.assertThat(spec.path()).isEqualTo("/backend");
        });
    }

    @Test
    void checkSpecWithNonStandardChars() {
        Origin spec = Origin.of("origin", "https://my.api.private:1443/:backend/{version}");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec.identifier()).isEqualTo("origin");
            softly.assertThat(spec.protocol()).isEqualTo(Protocol.https);
            softly.assertThat(spec.host()).isEqualTo("my.api.private");
            softly.assertThat(spec.port()).isEqualTo(1443);
            softly.assertThat(spec.path()).isEqualTo("/:backend/{version}");
        });
    }

    @Test
    void checkBasicSpecWithQuery() {
        Origin spec = Origin.of("origin", "https://my.api.private/backend?a=1&b=2");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec.identifier()).isEqualTo("origin");
            softly.assertThat(spec.protocol()).isEqualTo(Protocol.https);
            softly.assertThat(spec.host()).isEqualTo("my.api.private");
            softly.assertThat(spec.port()).isEqualTo(8080);
            softly.assertThat(spec.path()).isEqualTo("/backend?a=1&b=2");
        });
    }

    @Test
    void checkStorkWithPort() {
        assertThatThrownBy(() -> Origin.of("stork-origin", "stork://my.api.private:4500/backend?a=1&b=2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
    }

    @Test
    void checkStorkSpec() {
        Origin spec = Origin.of("stork-origin", "stork://my.api.private/backend?a=1&b=2");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec.identifier()).isEqualTo("stork-origin");
            softly.assertThat(spec.protocol()).isEqualTo(Protocol.stork);
            softly.assertThat(spec.host()).isEqualTo("my.api.private");
            // default port, but is never used (so for a sake of not having a null value for
            // a port)
            softly.assertThat(spec.port()).isEqualTo(8080);
            softly.assertThat(spec.path()).isEqualTo("/backend?a=1&b=2");
        });
    }

    @Test
    void checkPortUpperBound() {
        assertThatThrownBy(() -> Origin.of("upper-bound-origin", "http://my.api.private:65536/backend"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("range");
    }

    @Test
    void checkPortLowerBound() {
        assertThatThrownBy(() -> Origin.of("lower-bound-origin", "http://my.api.private:-1/backend"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("range");
    }

    @Test
    void checkTrailingColon() {
        assertThatThrownBy(() -> Origin.of("trailing-colon-origin", "https://my.api.private:/backend"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Port separator");
    }

    @Test
    void checkHostCanonicalization() {
        Origin spec1 = Origin.of("canonical-1", "https://MY.API.PRIVATE/backend");
        Origin spec2 = Origin.of("canonical-1", "https://my.api.private/backend");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec1.host()).isEqualTo("my.api.private");
            softly.assertThat(spec2.host()).isEqualTo("my.api.private");
            softly.assertThat(spec1.uri()).isEqualTo(spec2.uri());
        });
    }

    @Test
    void checkOriginWithoutPort() {
        Origin spec = Origin.of("no-port-origin", "https://example.com/path");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec.identifier()).isEqualTo("no-port-origin");
            softly.assertThat(spec.protocol()).isEqualTo(Protocol.https);
            softly.assertThat(spec.host()).isEqualTo("example.com");
            softly.assertThat(spec.port()).isEqualTo(8080);
            softly.assertThat(spec.path()).isEqualTo("/path");
        });
    }

    @Test
    void checkOriginWithoutPath() {
        Origin spec = Origin.of("no-path-origin", "https://example.com:9000");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec.identifier()).isEqualTo("no-path-origin");
            softly.assertThat(spec.protocol()).isEqualTo(Protocol.https);
            softly.assertThat(spec.host()).isEqualTo("example.com");
            softly.assertThat(spec.port()).isEqualTo(9000);
            softly.assertThat(spec.path()).isEqualTo("/");
        });
    }

    @Test
    void checkMinimalOrigin() {
        Origin spec = Origin.of("minimal-origin", "example.com");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spec.identifier()).isEqualTo("minimal-origin");
            softly.assertThat(spec.protocol()).isEqualTo(Protocol.http);
            softly.assertThat(spec.host()).isEqualTo("example.com");
            softly.assertThat(spec.port()).isEqualTo(8080);
            softly.assertThat(spec.path()).isEqualTo("/");
        });
    }

    @Test
    void checkNullAndBlankOrigins() {
        Origin nullOrigin = Origin.of("null-id", null);
        Origin blankOrigin = Origin.of("blank-id", "   ");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(nullOrigin.identifier()).isEqualTo("null-id");
            softly.assertThat(nullOrigin.protocol()).isEqualTo(Protocol.http);
            softly.assertThat(nullOrigin.host()).isEqualTo("localhost");
            softly.assertThat(nullOrigin.port()).isEqualTo(8080);
            softly.assertThat(nullOrigin.path()).isEqualTo("/");

            softly.assertThat(blankOrigin.identifier()).isEqualTo("blank-id");
            softly.assertThat(blankOrigin.protocol()).isEqualTo(Protocol.http);
            softly.assertThat(blankOrigin.host()).isEqualTo("localhost");
            softly.assertThat(blankOrigin.port()).isEqualTo(8080);
            softly.assertThat(blankOrigin.path()).isEqualTo("/");
        });
    }
}
