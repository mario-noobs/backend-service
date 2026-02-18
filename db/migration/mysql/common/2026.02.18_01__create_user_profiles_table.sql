CREATE TABLE IF NOT EXISTS user_profiles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    -- Personal
    gender          ENUM('male', 'female', 'other', 'prefer_not_to_say') DEFAULT NULL,
    date_of_birth   DATE DEFAULT NULL,
    avatar_url      VARCHAR(500) DEFAULT NULL,
    display_name    VARCHAR(100) DEFAULT NULL,
    bio             TEXT DEFAULT NULL,
    -- Address
    address_line_1  VARCHAR(255) DEFAULT NULL,
    address_line_2  VARCHAR(255) DEFAULT NULL,
    city            VARCHAR(128) DEFAULT NULL,
    state           VARCHAR(128) DEFAULT NULL,
    postal_code     VARCHAR(40) DEFAULT NULL,
    country         VARCHAR(2) DEFAULT NULL COMMENT 'ISO 3166-1 alpha-2',
    -- Timestamps
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
