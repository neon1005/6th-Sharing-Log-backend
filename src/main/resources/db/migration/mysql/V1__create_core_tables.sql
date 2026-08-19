CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    password VARCHAR(255) NULL,
    nickname VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_provider_user_id UNIQUE (provider, provider_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sharing_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sharing_groups_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE group_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_members_group_user UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_members_group
        FOREIGN KEY (group_id) REFERENCES sharing_groups (id),
    CONSTRAINT fk_group_members_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_group_members_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE group_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_invitations_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_group_invitations_group
        FOREIGN KEY (group_id) REFERENCES sharing_groups (id),
    CONSTRAINT fk_group_invitations_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    INDEX idx_group_invitations_group_id (group_id),
    INDEX idx_group_invitations_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
