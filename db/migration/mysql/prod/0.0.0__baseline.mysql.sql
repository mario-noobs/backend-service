-- =============================================
-- Baseline Schema: Face Recognition System
-- =============================================
-- This is the initial database schema as of 2026-02-17.
-- Liquibase uses this as the starting point for all migrations.
--
-- For FRESH databases: this creates all tables and seeds RBAC data.
-- For EXISTING databases: run `liquibase changelog-sync` to skip execution.
-- =============================================

-- Ensure proper character set
ALTER DATABASE backend_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant privileges
GRANT ALL PRIVILEGES ON backend_db.* TO 'root'@'%';
FLUSH PRIVILEGES;

-- =============================================
-- RBAC Tables
-- =============================================

CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    service VARCHAR(30),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    is_default BIT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- User & Auth Tables
-- =============================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(30) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    status VARCHAR(20) DEFAULT 'activated',
    role_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auths (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    auth_type VARCHAR(20) DEFAULT 'email_password',
    email VARCHAR(255) NOT NULL UNIQUE,
    salt VARCHAR(40),
    password VARCHAR(100),
    facebook_id VARCHAR(35),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Face Recognition Tables
-- =============================================

CREATE TABLE IF NOT EXISTS face_features (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    feature_vector TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS face_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    bucket_name VARCHAR(100),
    object_name VARCHAR(255),
    image_hash VARCHAR(40),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Audit Table
-- =============================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(100),
    user_id BIGINT,
    method VARCHAR(10),
    path VARCHAR(500),
    status_code INT,
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    request_body TEXT,
    response_body TEXT,
    duration_ms BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- Indexes
-- =============================================

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_auths_email ON auths(email);
CREATE INDEX idx_auths_user_id ON auths(user_id);
CREATE INDEX idx_face_features_user_id ON face_features(user_id);
CREATE INDEX idx_face_images_image_hash ON face_images(image_hash);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- =============================================
-- Seed Permissions
-- =============================================

INSERT INTO permissions (name, description, service) VALUES
-- Face service
('face:register', 'Register a face identity', 'face'),
('face:recognize', 'Recognize a face identity', 'face'),
('face:delete', 'Delete a face identity', 'face'),
('face:check', 'Check if face is registered', 'face'),
-- User service
('user:read_self', 'Read own profile', 'user'),
('user:update_self', 'Update own profile', 'user'),
('user:read_any', 'Read any user profile', 'user'),
('user:update_any', 'Update any user profile', 'user'),
('user:delete_any', 'Delete any user', 'user'),
('user:list', 'List all users', 'user'),
-- Audit service
('audit:read_self', 'Read own audit logs', 'audit'),
('audit:read_all', 'Read all audit logs', 'audit'),
-- RBAC management
('rbac:manage_roles', 'Create, update, delete roles', 'rbac'),
('rbac:assign_roles', 'Assign roles to users', 'rbac'),
('rbac:manage_permissions', 'Manage role permissions', 'rbac');

-- =============================================
-- Seed Roles
-- =============================================

INSERT INTO roles (name, description, is_default) VALUES
('SUPERADMIN', 'Full system access', 0),
('PREMIUM_USER', 'Premium user with full AI service access', 0),
('BASIC_USER', 'Basic user with limited access', 1);

-- =============================================
-- Seed Role-Permission Mappings
-- =============================================

-- SUPERADMIN: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPERADMIN';

-- PREMIUM_USER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'PREMIUM_USER'
  AND p.name IN (
    'user:read_self', 'user:update_self',
    'face:register', 'face:recognize', 'face:delete', 'face:check',
    'audit:read_self'
  );

-- BASIC_USER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'BASIC_USER'
  AND p.name IN (
    'user:read_self', 'user:update_self',
    'face:check',
    'audit:read_self'
  );
