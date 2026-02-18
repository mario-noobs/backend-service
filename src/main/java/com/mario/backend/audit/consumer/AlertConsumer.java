package com.mario.backend.audit.consumer;

import com.mario.backend.audit.config.AlertProperties;
import com.mario.backend.audit.config.RabbitMQConfig;
import com.mario.backend.audit.event.AuditEvent;
import com.mario.email.EmailRequest;
import com.mario.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertConsumer {

    private static final String BRUTE_FORCE_KEY_PREFIX = "alert:brute-force:";
    private static final String BRUTE_FORCE_FIRED_PREFIX = "alert:brute-force:fired:";

    private final EmailService emailService;
    private final AlertProperties alertProperties;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = RabbitMQConfig.AUDIT_ALERT_QUEUE)
    public void consume(AuditEvent event) {
        try {
            evaluateServerErrorRule(event);
            evaluateBruteForceRule(event);
        } catch (Exception e) {
            log.error("Alert processing failed (best-effort): action={}, requestId={}",
                    event.getAction(), event.getRequestId(), e);
        }
    }

    private void evaluateServerErrorRule(AuditEvent event) {
        if (!alertProperties.getServerError().isEnabled()) {
            return;
        }
        if (event.getStatusCode() == null || event.getStatusCode() < 500) {
            return;
        }

        log.warn("Server error detected: {} {} → {}", event.getHttpMethod(),
                event.getHttpPath(), event.getStatusCode());

        String timestamp = event.getTimestamp() != null
                ? event.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        EmailRequest emailRequest = EmailRequest.builder()
                .to(alertProperties.getRecipients())
                .subject("[ALERT] Server Error " + event.getStatusCode() + " on " + event.getHttpPath())
                .templateName("error-alert")
                .model("alertType", "Server Error")
                .model("alertTitle", "HTTP " + event.getStatusCode() + " Error Detected")
                .model("alertMessage", "A server error occurred during request processing.")
                .model("timestamp", timestamp)
                .model("severity", "critical")
                .model("requestId", event.getRequestId())
                .model("action", event.getAction())
                .model("actorIp", event.getActorIp() != null ? event.getActorIp() : "unknown")
                .model("statusCode", event.getStatusCode())
                .model("httpPath", event.getHttpPath())
                .build();

        emailService.send(emailRequest);
        log.info("Server error alert email sent for requestId={}", event.getRequestId());
    }

    private void evaluateBruteForceRule(AuditEvent event) {
        if (event.getStatusCode() == null || event.getStatusCode() != 401) {
            return;
        }
        if (!"auth:login".equals(event.getAction())) {
            return;
        }

        String clientIp = event.getActorIp();
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }

        String counterKey = BRUTE_FORCE_KEY_PREFIX + clientIp;
        String firedKey = BRUTE_FORCE_FIRED_PREFIX + clientIp;
        int windowMinutes = alertProperties.getFailedAuth().getWindowMinutes();
        int threshold = alertProperties.getFailedAuth().getThreshold();

        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count != null && count == 1) {
            redisTemplate.expire(counterKey, windowMinutes, TimeUnit.MINUTES);
        }

        if (count != null && count >= threshold) {
            // Only fire once per window
            Boolean alreadyFired = redisTemplate.hasKey(firedKey);
            if (Boolean.TRUE.equals(alreadyFired)) {
                return;
            }
            redisTemplate.opsForValue().set(firedKey, "1", windowMinutes, TimeUnit.MINUTES);

            log.warn("Brute-force auth detected: ip={}, attempts={}", clientIp, count);

            String timestamp = event.getTimestamp() != null
                    ? event.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            EmailRequest emailRequest = EmailRequest.builder()
                    .to(alertProperties.getRecipients())
                    .subject("[ALERT] Brute-Force Login Attempt from " + clientIp)
                    .templateName("error-alert")
                    .model("alertType", "Brute-Force Detection")
                    .model("alertTitle", "Excessive Failed Login Attempts")
                    .model("alertMessage",
                            "Multiple failed authentication attempts detected from a single IP address.")
                    .model("timestamp", timestamp)
                    .model("severity", "warning")
                    .model("actorIp", clientIp)
                    .model("failedAttempts", count)
                    .model("windowMinutes", windowMinutes)
                    .model("httpPath", event.getHttpPath())
                    .build();

            emailService.send(emailRequest);
            log.info("Brute-force alert email sent for ip={}", clientIp);
        }
    }
}
