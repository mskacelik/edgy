package org.acme.edgy.runtime.api.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusCodeTest {

    @Test
    void testIsSuccess() {
        assertTrue(StatusCode.isSuccess(200));
        assertTrue(StatusCode.isSuccess(201));
        assertTrue(StatusCode.isSuccess(299));
        assertFalse(StatusCode.isSuccess(400));
        assertFalse(StatusCode.isSuccess(500));
        assertFalse(StatusCode.isSuccess(1200));
        assertFalse(StatusCode.isSuccess(-200));
    }

    @Test
    void testIsClientError() {
        assertTrue(StatusCode.isClientError(400));
        assertTrue(StatusCode.isClientError(404));
        assertTrue(StatusCode.isClientError(499));
        assertFalse(StatusCode.isClientError(200));
        assertFalse(StatusCode.isClientError(500));
        assertFalse(StatusCode.isClientError(1400));
        assertFalse(StatusCode.isClientError(-400));
    }

    @Test
    void testIsServerError() {
        assertTrue(StatusCode.isServerError(500));
        assertTrue(StatusCode.isServerError(502));
        assertTrue(StatusCode.isServerError(599));
        assertFalse(StatusCode.isServerError(200));
        assertFalse(StatusCode.isServerError(400));
        assertFalse(StatusCode.isServerError(1500));
        assertFalse(StatusCode.isServerError(-500));
    }

    @Test
    void testIsError() {
        assertTrue(StatusCode.isError(400));
        assertTrue(StatusCode.isError(500));
        assertFalse(StatusCode.isError(200));
        assertFalse(StatusCode.isError(300));
        assertFalse(StatusCode.isError(1400));
        assertFalse(StatusCode.isError(1500));
        assertFalse(StatusCode.isError(-400));
        assertFalse(StatusCode.isError(-500));
    }
}
