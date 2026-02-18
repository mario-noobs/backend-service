package com.mario.backend.audit.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "audit-logs")
public class AuditEventDocument {

    @Id
    private String id;

    @Field(name = "request_id", type = FieldType.Keyword)
    private String requestId;

    @Field(name = "actor_id", type = FieldType.Long)
    private Long actorId;

    @Field(name = "actor_email", type = FieldType.Keyword)
    private String actorEmail;

    @Field(name = "actor_ip", type = FieldType.Ip)
    private String actorIp;

    @Field(name = "actor_agent", type = FieldType.Text)
    private String actorAgent;

    @Field(name = "actor_role", type = FieldType.Keyword)
    private String actorRole;

    @Field(name = "action", type = FieldType.Keyword)
    private String action;

    @Field(name = "http_method", type = FieldType.Keyword)
    private String httpMethod;

    @Field(name = "http_path", type = FieldType.Keyword)
    private String httpPath;

    @Field(name = "target_type", type = FieldType.Keyword)
    private String targetType;

    @Field(name = "target_id", type = FieldType.Keyword)
    private String targetId;

    @Field(name = "outcome", type = FieldType.Keyword)
    private String outcome;

    @Field(name = "status_code", type = FieldType.Integer)
    private Integer statusCode;

    @Field(name = "duration_ms", type = FieldType.Long)
    private Long durationMs;

    @Field(name = "timestamp", type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime timestamp;
}
