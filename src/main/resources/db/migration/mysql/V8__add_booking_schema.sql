CREATE TABLE spaces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    group_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    active BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_spaces_public_id UNIQUE (public_id),
    CONSTRAINT uk_spaces_group_name UNIQUE (group_id, name),
    CONSTRAINT fk_spaces_group
        FOREIGN KEY (group_id) REFERENCES sharing_groups (id),
    INDEX idx_spaces_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    space_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    reservation_date DATE NOT NULL,
    start_time TIME(6) NOT NULL,
    end_time TIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reservations_public_id UNIQUE (public_id),
    CONSTRAINT fk_reservations_space
        FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_reservations_member
        FOREIGN KEY (member_id) REFERENCES group_members (id),
    INDEX idx_reservations_space_date (space_id, reservation_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
