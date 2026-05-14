package com.tempmon.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Servlet filter that enforces the configured maximum payload size.
 * If the request body exceeds {@code app.max-payload-bytes}, the filter
 * short-circuits with a 422 response and error code PAYLOAD_TOO_LARGE.
 * A payload whose size equals the limit exactly is accepted.
 */
@Component
public class PayloadSizeFilter extends OncePerRequestFilter {

    private final long maxPayloadBytes;

    public PayloadSizeFilter(@Value("${app.max-payload-bytes:1048576}") long maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only check requests that may have a body (POST, PUT, PATCH)
        if (!hasBody(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Read the body up to maxPayloadBytes + 1 to detect overflow
        InputStream inputStream = request.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long totalRead = 0;
        int bytesRead;

        while ((bytesRead = inputStream.read(chunk)) != -1) {
            totalRead += bytesRead;
            if (totalRead > maxPayloadBytes) {
                // Body exceeds limit — return 422 immediately
                sendPayloadTooLargeResponse(response);
                return;
            }
            buffer.write(chunk, 0, bytesRead);
        }

        // Body is within limits — wrap the request so downstream can re-read the body
        byte[] body = buffer.toByteArray();
        HttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, body);
        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean hasBody(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private void sendPayloadTooLargeResponse(HttpServletResponse response) throws IOException {
        response.setStatus(422);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"status\":\"error\",\"error\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Request body exceeds the maximum allowed size of "
                + maxPayloadBytes + " bytes\"}";
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    /**
     * Request wrapper that replaces the input stream with a cached byte array,
     * allowing downstream components to read the body normally after the filter
     * has already consumed it for size checking.
     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.cachedBody = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Not needed for synchronous processing
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return byteArrayInputStream.read(b, off, len);
                }
            };
        }

        @Override
        public int getContentLength() {
            return cachedBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return cachedBody.length;
        }
    }
}
