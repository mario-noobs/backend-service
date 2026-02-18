package com.mario.backend.audit.publisher;

import com.mario.backend.audit.config.RabbitMQConfig;
import com.mario.backend.audit.entity.AuditLog;
import com.mario.backend.audit.event.AuditEvent;
import com.mario.backend.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AuditService auditService;

    public void publish(AuditEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.AUDIT_EXCHANGE, "", event);
            log.debug("Audit event published to RabbitMQ: action={}, requestId={}",
                    event.getAction(), event.getRequestId());
        } catch (AmqpException e) {
            log.warn("RabbitMQ unavailable, falling back to direct DB save: {}", e.getMessage());
            fallbackToDirectSave(event);
        }
    }

    private void fallbackToDirectSave(AuditEvent event) {
        AuditLog auditLog = AuditLog.builder()
                .requestId(event.getRequestId())
                .userId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .actorRole(event.getActorRole())
                .action(event.getAction())
                .targetType(event.getTargetType())
                .targetId(event.getTargetId())
                .outcome(event.getOutcome())
                .method(event.getHttpMethod())
                .path(event.getHttpPath())
                .statusCode(event.getStatusCode())
                .clientIp(event.getActorIp())
                .userAgent(event.getActorAgent())
                .durationMs(event.getDurationMs())
                .build();

        auditService.saveAuditLog(auditLog);
    }
}
