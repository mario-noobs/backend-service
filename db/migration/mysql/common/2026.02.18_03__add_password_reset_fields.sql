-- Add password reset token fields to auths table
ALTER TABLE auths
  ADD COLUMN reset_token        VARCHAR(100) DEFAULT NULL,
  ADD COLUMN reset_token_expiry DATETIME     DEFAULT NULL;

CREATE INDEX idx_auths_reset_token ON auths(reset_token);
