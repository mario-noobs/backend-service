package com.mario.backend.audit.event;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuditActionMapper {

    private AuditActionMapper() {
    }

    public record AuditActionMapping(String action, String targetType, String targetId) {
    }

    private record MappingRule(Pattern pattern, String method, String action,
                               String targetType, int targetIdGroup) {
    }

    private static final List<MappingRule> RULES = new ArrayList<>();

    static {
        // Auth endpoints
        rule("POST", "/api/v1/user/authenticate", "auth:login", "auth", -1);
        rule("POST", "/api/v1/user/register", "auth:register", "auth", -1);
        rule("POST", "/api/v1/user/logout", "auth:logout", "auth", -1);
        rule("POST", "/api/v1/user/refresh", "auth:refresh", "auth", -1);
        rule("POST", "/api/v1/user/forgot-password", "auth:forgot_password", "auth", -1);
        rule("POST", "/api/v1/user/reset-password", "auth:reset_password", "auth", -1);
        rule("POST", "/api/v1/user/accept-invitation", "auth:accept_invitation", "auth", -1);

        // Profile endpoints
        rule("POST", "/profile", "profile:read", "profile", -1);
        rule("PUT", "/profile", "profile:update", "profile", -1);

        // Face endpoints
        rule("POST", "/api/v1/face/register-identity", "face:register", "face", -1);
        rule("POST", "/api/v1/face/recognize-identity", "face:recognize", "face", -1);
        rule("POST", "/api/v1/face/delete-identity", "face:delete", "face", -1);
        rule("GET", "/api/v1/face/is-registered", "face:check", "face", -1);

        // Audit endpoints
        rule("GET", "/api/v1/audit/all", "audit:list", "audit", -1);
        ruleWithId("GET", "/api/v1/audit/user/(\\d+)", "audit:read", "audit", 1);

        // Admin user endpoints
        rule("GET", "/api/v1/admin/users", "user:list", "user", -1);
        ruleWithId("GET", "/api/v1/admin/users/(\\d+)", "user:read", "user", 1);
        ruleWithId("PUT", "/api/v1/admin/users/(\\d+)/status", "user:update_status", "user", 1);
        ruleWithId("PUT", "/api/v1/admin/users/(\\d+)", "user:update", "user", 1);
        rule("POST", "/api/v1/admin/users/invite", "user:invite", "user", -1);

        // RBAC endpoints (catch-all for admin rbac)
        ruleWithId(".*", "/api/v1/admin/rbac/.*", "rbac:manage", "rbac", -1);
    }

    private static void rule(String method, String path, String action,
                             String targetType, int targetIdGroup) {
        RULES.add(new MappingRule(
                Pattern.compile("^" + Pattern.quote(path) + "$"),
                method, action, targetType, targetIdGroup));
    }

    private static void ruleWithId(String method, String pathRegex, String action,
                                   String targetType, int targetIdGroup) {
        RULES.add(new MappingRule(
                Pattern.compile("^" + pathRegex + "$"),
                method, action, targetType, targetIdGroup));
    }

    public static AuditActionMapping resolve(String httpMethod, String httpPath) {
        for (MappingRule rule : RULES) {
            if (!rule.method().equals(".*") && !rule.method().equalsIgnoreCase(httpMethod)) {
                continue;
            }
            Matcher matcher = rule.pattern().matcher(httpPath);
            if (matcher.matches()) {
                String targetId = null;
                if (rule.targetIdGroup() > 0 && rule.targetIdGroup() <= matcher.groupCount()) {
                    targetId = matcher.group(rule.targetIdGroup());
                }
                return new AuditActionMapping(rule.action(), rule.targetType(), targetId);
            }
        }
        // Default fallback
        return new AuditActionMapping(
                "http:" + httpMethod.toLowerCase() + ":" + httpPath,
                null,
                null);
    }
}
