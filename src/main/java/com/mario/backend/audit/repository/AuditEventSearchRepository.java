package com.mario.backend.audit.repository;

import com.mario.backend.audit.document.AuditEventDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventSearchRepository extends ElasticsearchRepository<AuditEventDocument, String> {
}
