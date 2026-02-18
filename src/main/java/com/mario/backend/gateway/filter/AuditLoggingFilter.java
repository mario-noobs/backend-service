package com.mario.backend.gateway.filter;

import com.mario.backend.audit.event.AuditActionMapper;
import com.mario.backend.audit.event.AuditActionMapper.AuditActionMapping;
import com.mario.backend.audit.event.AuditEvent;
import com.mario.backend.audit.publisher.AuditEventPublisher;
import com.mario.backend.auth.security.AuthenticatedUser;
import com.mario.backend.logging.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditLoggingFilter extends OncePerRequestFilter {

    private final AuditEventPublisher auditEventPublisher;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        TraceContext.setTraceId(requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatus();

            // Extract actor info from SecurityContext
            Long actorId = null;
            String actorEmail = null;
            String actorRole = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                actorId = user.getUserId();
                actorEmail = user.getEmail();
                actorRole = user.getRoleName();
                TraceContext.setUser(actorId, actorEmail);
            }

            // Resolve semantic action mapping
            String httpMethod = request.getMethod();
            String httpPath = request.getRequestURI();
            AuditActionMapping mapping = AuditActionMapper.resolve(httpMethod, httpPath);

            // Determine outcome
            String outcome = statusCode < 400 ? "success" : "failure";

            // Build enriched audit event
            AuditEvent event = AuditEvent.builder()
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .actorIp(getClientIp(request))
                    .actorAgent(request.getHeader("User-Agent"))
                    .actorRole(actorRole)
                    .action(mapping.action())
                    .httpMethod(httpMethod)
                    .httpPath(httpPath)
                    .targetType(mapping.targetType())
                    .targetId(mapping.targetId())
                    .outcome(outcome)
                    .statusCode(statusCode)
                    .requestId(requestId)
                    .durationMs(duration)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditEventPublisher.publish(event);

            log.info("Request completed: {} {} {} {}ms",
                    httpMethod, httpPath, statusCode, duration);

            TraceContext.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/ping") || path.startsWith("/api/v1/audit");
    }
}
