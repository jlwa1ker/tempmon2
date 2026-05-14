package com.tempmon.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that generates a unique X-Request-ID (UUID v4) for each request.
 * <p>
 * The ID is stored in:
 * <ul>
 *   <li>SLF4J MDC under key "requestId" for log correlation</li>
 *   <li>A request attribute for access by other components</li>
 * </ul>
 * <p>
 * The X-Request-ID response header is attached to all responses except successful
 * health-check responses (status 2xx on /health). Failed health-check responses
 * still receive the header per Requirement 5.6.
 */
@Component
public class RequestIdFilter implements Filter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_ATTRIBUTE = "com.tempmon.requestId";
    private static final String MDC_KEY = "requestId";
    private static final String HEALTH_CHECK_PATH = "/health";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String requestId = UUID.randomUUID().toString();

        // Store in MDC for log correlation
        MDC.put(MDC_KEY, requestId);

        // Store as request attribute for access by other components
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Attach X-Request-ID header unless this is a successful health-check response
            if (!isSuccessfulHealthCheck(request, response)) {
                if (!response.isCommitted()) {
                    response.setHeader(REQUEST_ID_HEADER, requestId);
                }
            }

            // Clean up MDC
            MDC.remove(MDC_KEY);
        }
    }

    private boolean isSuccessfulHealthCheck(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        int status = response.getStatus();
        return HEALTH_CHECK_PATH.equals(path) && status >= 200 && status < 300;
    }
}
