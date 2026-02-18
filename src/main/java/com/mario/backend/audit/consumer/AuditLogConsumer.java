package com.mario.backend.audit.consumer;

import com.mario.backend.audit.config.RabbitMQConfig;
import com.mario.backend.audit.entity.AuditLog;
import com.mario.backend.audit.event.AuditEvent;
import com.mario.backend.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogRepository auditLogRepository;

    @RabbitListener(queues = RabbitMQConfig.AUDIT_PERSIST_QUEUE)
    public void consume(AuditEvent event) {
        log.debug("Persisting audit event: action={}, requestId={}", event.getAction(), event.getRequestId());

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

        auditLogRepository.save(auditLog);
    }
}
