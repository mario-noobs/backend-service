-- Enrich audit_logs with actor/action/target/outcome fields
ALTER TABLE audit_logs
  ADD COLUMN actor_email  VARCHAR(255) DEFAULT NULL AFTER user_id,
  ADD COLUMN actor_role   VARCHAR(50)  DEFAULT NULL AFTER actor_email,
  ADD COLUMN action       VARCHAR(100) DEFAULT NULL AFTER actor_role,
  ADD COLUMN target_type  VARCHAR(50)  DEFAULT NULL AFTER action,
  ADD COLUMN target_id    VARCHAR(100) DEFAULT NULL AFTER target_type,
  ADD COLUMN outcome      VARCHAR(20)  DEFAULT NULL AFTER target_id;

CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_outcome ON audit_logs(outcome);
CREATE INDEX idx_audit_logs_target_type ON audit_logs(target_type);
