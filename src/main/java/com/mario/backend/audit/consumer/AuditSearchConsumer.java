package com.mario.backend.audit.consumer;

import com.mario.backend.audit.config.RabbitMQConfig;
import com.mario.backend.audit.document.AuditEventDocument;
import com.mario.backend.audit.event.AuditEvent;
import com.mario.backend.audit.repository.AuditEventSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditSearchConsumer {

    private final AuditEventSearchRepository searchRepository;

    @RabbitListener(queues = RabbitMQConfig.AUDIT_SEARCH_QUEUE)
    public void consume(AuditEvent event) {
        log.debug("Indexing audit event to Elasticsearch: action={}, requestId={}",
                event.getAction(), event.getRequestId());

        AuditEventDocument document = AuditEventDocument.builder()
                .requestId(event.getRequestId())
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .actorIp(event.getActorIp())
                .actorAgent(event.getActorAgent())
                .actorRole(event.getActorRole())
                .action(event.getAction())
                .httpMethod(event.getHttpMethod())
                .httpPath(event.getHttpPath())
                .targetType(event.getTargetType())
                .targetId(event.getTargetId())
                .outcome(event.getOutcome())
                .statusCode(event.getStatusCode())
                .durationMs(event.getDurationMs())
                .timestamp(event.getTimestamp())
                .build();

        searchRepository.save(document);
    }
}
