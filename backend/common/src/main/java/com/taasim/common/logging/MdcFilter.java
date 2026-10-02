package com.taasim.common.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

public class MdcFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String TRIP_ID_HEADER = "X-Trip-Id";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_TRIP_ID = "tripId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest http) {
            try {
                String cid = http.getHeader(CORRELATION_ID_HEADER);
                if (cid == null || cid.isBlank()) {
                    cid = UUID.randomUUID().toString();
                }
                MDC.put(MDC_CORRELATION_ID, cid);

                String userId = http.getHeader(USER_ID_HEADER);
                if (userId != null && !userId.isBlank()) {
                    MDC.put(MDC_USER_ID, userId);
                }

                String tripId = http.getHeader(TRIP_ID_HEADER);
                if (tripId != null && !tripId.isBlank()) {
                    MDC.put(MDC_TRIP_ID, tripId);
                }

                chain.doFilter(req, resp);
            } finally {
                MDC.clear();
            }
        } else {
            chain.doFilter(req, resp);
        }
    }
}
