package com.mario.backend.audit.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // Actor
    private Long actorId;
    private String actorEmail;
    private String actorIp;
    private String actorAgent;
    private String actorRole;

    // Action
    private String action;
    private String httpMethod;
    private String httpPath;

    // Target
    private String targetType;
    private String targetId;

    // Outcome
    private String outcome;
    private Integer statusCode;

    // Context
    private String requestId;
    private Long durationMs;
    private LocalDateTime timestamp;
}
