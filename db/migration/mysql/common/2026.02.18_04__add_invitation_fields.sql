-- Add invitation token fields to auths table
ALTER TABLE auths
  ADD COLUMN invitation_token        VARCHAR(100) DEFAULT NULL,
  ADD COLUMN invitation_token_expiry DATETIME     DEFAULT NULL;

CREATE INDEX idx_auths_invitation_token ON auths(invitation_token);
