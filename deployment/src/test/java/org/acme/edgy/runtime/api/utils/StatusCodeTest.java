package org.acme.edgy.runtime.api.utils;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class StatusCodeTest {

    @Test
    void testIsSuccess() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(StatusCode.isSuccess(200)).isTrue();
            softly.assertThat(StatusCode.isSuccess(201)).isTrue();
            softly.assertThat(StatusCode.isSuccess(299)).isTrue();
            softly.assertThat(StatusCode.isSuccess(400)).isFalse();
            softly.assertThat(StatusCode.isSuccess(500)).isFalse();
            softly.assertThat(StatusCode.isSuccess(1200)).isFalse();
            softly.assertThat(StatusCode.isSuccess(-200)).isFalse();
        });
    }

    @Test
    void testIsClientError() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(StatusCode.isClientError(400)).isTrue();
            softly.assertThat(StatusCode.isClientError(404)).isTrue();
            softly.assertThat(StatusCode.isClientError(499)).isTrue();
            softly.assertThat(StatusCode.isClientError(200)).isFalse();
            softly.assertThat(StatusCode.isClientError(500)).isFalse();
            softly.assertThat(StatusCode.isClientError(1400)).isFalse();
            softly.assertThat(StatusCode.isClientError(-400)).isFalse();
        });
    }

    @Test
    void testIsServerError() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(StatusCode.isServerError(500)).isTrue();
            softly.assertThat(StatusCode.isServerError(502)).isTrue();
            softly.assertThat(StatusCode.isServerError(599)).isTrue();
            softly.assertThat(StatusCode.isServerError(200)).isFalse();
            softly.assertThat(StatusCode.isServerError(400)).isFalse();
            softly.assertThat(StatusCode.isServerError(1500)).isFalse();
            softly.assertThat(StatusCode.isServerError(-500)).isFalse();
        });
    }

    @Test
    void testIsError() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(StatusCode.isError(400)).isTrue();
            softly.assertThat(StatusCode.isError(500)).isTrue();
            softly.assertThat(StatusCode.isError(200)).isFalse();
            softly.assertThat(StatusCode.isError(300)).isFalse();
            softly.assertThat(StatusCode.isError(1400)).isFalse();
            softly.assertThat(StatusCode.isError(1500)).isFalse();
            softly.assertThat(StatusCode.isError(-400)).isFalse();
            softly.assertThat(StatusCode.isError(-500)).isFalse();
        });
    }
}
