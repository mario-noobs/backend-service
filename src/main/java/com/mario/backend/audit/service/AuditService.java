package com.mario.backend.audit.service;

import com.mario.backend.audit.document.AuditEventDocument;
import com.mario.backend.audit.dto.AuditLogResponse;
import com.mario.backend.audit.dto.PageResponse;
import com.mario.backend.audit.entity.AuditLog;
import com.mario.backend.audit.repository.AuditLogRepository;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Async
    public void saveAuditLog(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    public PageResponse<AuditLogResponse> getAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditLogs = auditLogRepository.findAll(pageable);

        return buildPageResponse(auditLogs);
    }

    public PageResponse<AuditLogResponse> getAuditLogsByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditLogs = auditLogRepository.findByUserId(userId, pageable);

        return buildPageResponse(auditLogs);
    }

    public PageResponse<AuditLogResponse> searchAuditLogs(String query, String action,
                                                           String targetType, String outcome,
                                                           LocalDateTime from, LocalDateTime to,
                                                           int page, int size) {
        BoolQuery.Builder boolBuilder = QueryBuilders.bool();

        if (query != null && !query.isBlank()) {
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .query(query)
                    .fields("actor_email", "actor_agent", "http_path", "request_id")));
        }

        if (action != null && !action.isBlank()) {
            boolBuilder.filter(f -> f.term(t -> t.field("action").value(action)));
        }

        if (targetType != null && !targetType.isBlank()) {
            boolBuilder.filter(f -> f.term(t -> t.field("target_type").value(targetType)));
        }

        if (outcome != null && !outcome.isBlank()) {
            boolBuilder.filter(f -> f.term(t -> t.field("outcome").value(outcome)));
        }

        if (from != null || to != null) {
            boolBuilder.filter(f -> f.range(r -> {
                r.field("timestamp");
                if (from != null) {
                    r.gte(co.elastic.clients.json.JsonData.of(from.toString()));
                }
                if (to != null) {
                    r.lte(co.elastic.clients.json.JsonData.of(to.toString()));
                }
                return r;
            }));
        }

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(boolBuilder.build()))
                .withSort(s -> s.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<AuditEventDocument> searchHits =
                elasticsearchOperations.search(searchQuery, AuditEventDocument.class);

        List<AuditLogResponse> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::mapDocumentToResponse)
                .collect(Collectors.toList());

        long totalHits = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalHits / size);

        return PageResponse.<AuditLogResponse>builder()
                .content(content)
                .totalElements(totalHits)
                .totalPages(totalPages)
                .page(page)
                .size(size)
                .build();
    }

    private PageResponse<AuditLogResponse> buildPageResponse(Page<AuditLog> page) {
        List<AuditLogResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<AuditLogResponse>builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }

    private AuditLogResponse mapToResponse(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .requestId(auditLog.getRequestId())
                .userId(auditLog.getUserId())
                .actorEmail(auditLog.getActorEmail())
                .actorRole(auditLog.getActorRole())
                .action(auditLog.getAction())
                .targetType(auditLog.getTargetType())
                .targetId(auditLog.getTargetId())
                .outcome(auditLog.getOutcome())
                .method(auditLog.getMethod())
                .path(auditLog.getPath())
                .statusCode(auditLog.getStatusCode())
                .clientIp(auditLog.getClientIp())
                .userAgent(auditLog.getUserAgent())
                .durationMs(auditLog.getDurationMs())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    private AuditLogResponse mapDocumentToResponse(AuditEventDocument doc) {
        return AuditLogResponse.builder()
                .requestId(doc.getRequestId())
                .userId(doc.getActorId())
                .actorEmail(doc.getActorEmail())
                .actorRole(doc.getActorRole())
                .action(doc.getAction())
                .targetType(doc.getTargetType())
                .targetId(doc.getTargetId())
                .outcome(doc.getOutcome())
                .method(doc.getHttpMethod())
                .path(doc.getHttpPath())
                .statusCode(doc.getStatusCode())
                .clientIp(doc.getActorIp())
                .userAgent(doc.getActorAgent())
                .durationMs(doc.getDurationMs())
                .createdAt(doc.getTimestamp())
                .build();
    }
}
