package com.tempmon.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayloadSizeFilterTest {

    private static final long MAX_PAYLOAD_BYTES = 100;
    private PayloadSizeFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private StringWriter responseBody;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        filter = new PayloadSizeFilter(MAX_PAYLOAD_BYTES);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void payloadExactlyAtLimit_isAccepted() throws Exception {
        byte[] body = new byte[(int) MAX_PAYLOAD_BYTES]; // exactly 100 bytes
        java.util.Arrays.fill(body, (byte) 'a');

        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(toServletInputStream(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(HttpServletRequest.class), eq(response));
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void payloadOneByteOverLimit_returns422() throws Exception {
        byte[] body = new byte[(int) MAX_PAYLOAD_BYTES + 1]; // 101 bytes
        java.util.Arrays.fill(body, (byte) 'a');

        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(toServletInputStream(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(422);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());

        JsonNode json = objectMapper.readTree(responseBody.toString());
        assertEquals("error", json.get("status").asText());
        assertEquals("PAYLOAD_TOO_LARGE", json.get("error").asText());
        assertTrue(json.has("message"));
    }

    @Test
    void payloadWellOverLimit_returns422() throws Exception {
        byte[] body = new byte[500]; // well over 100 byte limit
        java.util.Arrays.fill(body, (byte) 'x');

        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(toServletInputStream(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(422);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void emptyBody_isAccepted() throws Exception {
        byte[] body = new byte[0];

        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(toServletInputStream(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(HttpServletRequest.class), eq(response));
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void getRequest_passesThrough() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void deleteRequest_passesThrough() throws Exception {
        when(request.getMethod()).thenReturn("DELETE");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void putRequest_isChecked() throws Exception {
        byte[] body = new byte[(int) MAX_PAYLOAD_BYTES + 1];
        java.util.Arrays.fill(body, (byte) 'b');

        when(request.getMethod()).thenReturn("PUT");
        when(request.getInputStream()).thenReturn(toServletInputStream(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(422);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void payloadUnderLimit_bodyIsReadableDownstream() throws Exception {
        String content = "{\"readings\":[{\"test\":true}]}";
        byte[] body = content.getBytes();

        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(toServletInputStream(body));

        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

        // Verify the wrapped request can still provide the body
        HttpServletRequest wrappedRequest = requestCaptor.getValue();
        ServletInputStream is = wrappedRequest.getInputStream();
        byte[] readBody = is.readAllBytes();
        assertEquals(content, new String(readBody));
    }

    private static ServletInputStream toServletInputStream(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return bais.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return bais.read(b, off, len);
            }
        };
    }
}
