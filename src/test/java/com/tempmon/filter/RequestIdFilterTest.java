package com.tempmon.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private RequestIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldAttachRequestIdHeaderToNonHealthCheckRequest() throws ServletException, IOException {
        request.setRequestURI("/ingest");

        FilterChain chain = (req, res) -> {};
        filter.doFilter(request, response, chain);

        String headerValue = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(headerValue);
        assertDoesNotThrow(() -> UUID.fromString(headerValue));
    }

    @Test
    void shouldStoreRequestIdInRequestAttribute() throws ServletException, IOException {
        request.setRequestURI("/ingest");

        FilterChain chain = (req, res) -> {
            Object attr = req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            assertNotNull(attr);
            assertDoesNotThrow(() -> UUID.fromString((String) attr));
        };

        filter.doFilter(request, response, chain);
    }

    @Test
    void shouldStoreRequestIdInMdcDuringChainExecution() throws ServletException, IOException {
        request.setRequestURI("/ingest");

        FilterChain chain = (req, res) -> {
            String mdcValue = MDC.get("requestId");
            assertNotNull(mdcValue);
            assertDoesNotThrow(() -> UUID.fromString(mdcValue));
        };

        filter.doFilter(request, response, chain);

        // MDC should be cleaned up after filter completes
        assertNull(MDC.get("requestId"));
    }

    @Test
    void shouldNotAttachHeaderToSuccessfulHealthCheck() throws ServletException, IOException {
        request.setRequestURI("/health");

        FilterChain chain = (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        assertNull(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void shouldAttachHeaderToFailedHealthCheck() throws ServletException, IOException {
        request.setRequestURI("/health");

        FilterChain chain = (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(500);
        };

        filter.doFilter(request, response, chain);

        String headerValue = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(headerValue);
        assertDoesNotThrow(() -> UUID.fromString(headerValue));
    }

    @Test
    void shouldAttachHeaderToHealthCheckWith4xxStatus() throws ServletException, IOException {
        request.setRequestURI("/health");

        FilterChain chain = (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(404);
        };

        filter.doFilter(request, response, chain);

        String headerValue = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(headerValue);
        assertDoesNotThrow(() -> UUID.fromString(headerValue));
    }

    @Test
    void shouldGenerateUniqueIdsPerRequest() throws ServletException, IOException {
        request.setRequestURI("/ingest");
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);
        String firstId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        request2.setRequestURI("/ingest");

        filter.doFilter(request2, response2, chain);
        String secondId = response2.getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        assertNotEquals(firstId, secondId);
    }

    @Test
    void requestAttributeAndHeaderShouldMatchSameUuid() throws ServletException, IOException {
        request.setRequestURI("/readings");

        final String[] capturedAttribute = new String[1];
        FilterChain chain = (req, res) -> {
            capturedAttribute[0] = (String) req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        };

        filter.doFilter(request, response, chain);

        String headerValue = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertEquals(capturedAttribute[0], headerValue);
    }

    @Test
    void shouldCleanUpMdcEvenWhenChainThrows() throws ServletException, IOException {
        request.setRequestURI("/ingest");

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("simulated error");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));
        assertNull(MDC.get("requestId"));
    }
}
