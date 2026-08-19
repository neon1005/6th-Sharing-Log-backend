CREATE TABLE idempotency_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    normalized_uri VARCHAR(2048) NOT NULL,
    uri_hash VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body LONGTEXT NULL,
    response_etag VARCHAR(255) NULL,
    response_location VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_records_actor_method_uri_key
        UNIQUE (actor_user_id, http_method, uri_hash, idempotency_key),
    CONSTRAINT fk_idempotency_records_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    INDEX idx_idempotency_records_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
