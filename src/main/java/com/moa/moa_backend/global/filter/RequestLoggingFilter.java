package com.moa.moa_backend.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("REQUEST");


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        if("OPTIONS".equalsIgnoreCase(request.getMethod())){return true;}

        String path = request.getRequestURI();
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();

        //userId 최소검증
        String raw = req.getHeader("X-User-Id");
        String userId = (raw != null && raw.matches("\\d{1,10}")) ? raw : "anonymous";
        MDC.put("userId", userId);


        String method = req.getMethod();
        String path = req.getRequestURI();

        try{
            filterChain.doFilter(req,res);
        }finally {
            long latency = System.currentTimeMillis() - start;
            int status = res.getStatus();
            String errorCode = MDC.get("errorCode");


            if (status >= 500) {
                if (errorCode != null) log.error("[REQ] userId={} {} {} status={} {}ms error={}", userId, method, path, status, latency, errorCode);
                else log.error("[REQ] userId={} {} {} status={} {}ms", userId, method, path, status, latency);
            } else if (status >= 400) {
                if (errorCode != null) log.warn("[REQ] userId={} {} {} status={} {}ms error={}", userId, method, path, status, latency, errorCode);
                else log.warn("[REQ] userId={} {} {} status={} {}ms", userId, method, path, status, latency);
            } else {
                log.info("[REQ] userId={} {} {} status={} {}ms", userId, method, path, status, latency);
            }

            MDC.clear();
        }

    }
}
