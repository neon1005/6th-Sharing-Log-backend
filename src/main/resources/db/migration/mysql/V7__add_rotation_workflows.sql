ALTER TABLE chore_occurrences
    ADD COLUMN chore_name_snapshot VARCHAR(100) NULL;

UPDATE chore_occurrences occurrence
JOIN chores chore
    ON chore.id = occurrence.chore_id
SET occurrence.chore_name_snapshot = chore.name;

ALTER TABLE chore_occurrences
    MODIFY COLUMN chore_name_snapshot VARCHAR(100) NOT NULL;

ALTER TABLE chore_assignment_attempts
    ADD COLUMN completion_revoked_at DATETIME(6) NULL,
    ADD COLUMN completion_revoked_by_membership_id BIGINT NULL,
    ADD COLUMN completion_revocation_note VARCHAR(500) NULL,
    ADD CONSTRAINT fk_assignment_attempts_completion_revoked_by
        FOREIGN KEY (completion_revoked_by_membership_id)
        REFERENCES group_members (id),
    ADD INDEX idx_assignment_attempts_completion_revoked_by
        (completion_revoked_by_membership_id);

CREATE TABLE substitute_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    occurrence_id BIGINT NOT NULL,
    requester_assignment_id BIGINT NOT NULL,
    eligibility_snapshot_version INTEGER NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    accepted_assignment_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    last_response_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    active_marker INTEGER NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_substitute_requests_public_id UNIQUE (public_id),
    CONSTRAINT uk_substitute_requests_occurrence_active
        UNIQUE (occurrence_id, active_marker),
    CONSTRAINT uk_substitute_requests_accepted_assignment
        UNIQUE (accepted_assignment_id),
    CONSTRAINT fk_substitute_requests_occurrence
        FOREIGN KEY (occurrence_id) REFERENCES chore_occurrences (id),
    CONSTRAINT fk_substitute_requests_requester_assignment
        FOREIGN KEY (requester_assignment_id) REFERENCES chore_assignment_attempts (id),
    CONSTRAINT fk_substitute_requests_accepted_assignment
        FOREIGN KEY (accepted_assignment_id) REFERENCES chore_assignment_attempts (id),
    INDEX idx_substitute_requests_occurrence_status (occurrence_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE substitute_request_recipients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    member_activation_generation BIGINT NOT NULL,
    response_status VARCHAR(20) NOT NULL,
    responded_at DATETIME(6) NULL,
    accepted_marker INTEGER NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_substitute_recipients_request_member
        UNIQUE (request_id, membership_id),
    CONSTRAINT uk_substitute_recipients_one_accepted
        UNIQUE (request_id, accepted_marker),
    CONSTRAINT fk_substitute_recipients_request
        FOREIGN KEY (request_id) REFERENCES substitute_requests (id),
    CONSTRAINT fk_substitute_recipients_membership
        FOREIGN KEY (membership_id) REFERENCES group_members (id),
    INDEX idx_substitute_recipients_member_status
        (membership_id, response_status, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
