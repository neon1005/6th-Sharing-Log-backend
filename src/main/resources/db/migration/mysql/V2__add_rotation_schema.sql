ALTER TABLE sharing_groups ADD COLUMN public_id VARCHAR(36) NULL;
ALTER TABLE sharing_groups ADD COLUMN time_zone_id VARCHAR(40) NULL;
ALTER TABLE sharing_groups ADD COLUMN week_starts_on VARCHAR(10) NULL;

UPDATE sharing_groups
SET public_id = LOWER(UUID())
WHERE public_id IS NULL;

UPDATE sharing_groups
SET time_zone_id = 'Asia/Seoul'
WHERE time_zone_id IS NULL;

UPDATE sharing_groups
SET week_starts_on = 'MONDAY'
WHERE week_starts_on IS NULL;

ALTER TABLE sharing_groups
    MODIFY COLUMN public_id VARCHAR(36) NOT NULL;
ALTER TABLE sharing_groups
    MODIFY COLUMN time_zone_id VARCHAR(40) NOT NULL;
ALTER TABLE sharing_groups
    MODIFY COLUMN week_starts_on VARCHAR(10) NOT NULL;
ALTER TABLE sharing_groups
    ADD CONSTRAINT uk_sharing_groups_public_id UNIQUE (public_id);

ALTER TABLE group_members ADD COLUMN public_id VARCHAR(36) NULL;
ALTER TABLE group_members ADD COLUMN status VARCHAR(20) NULL;
ALTER TABLE group_members ADD COLUMN left_at DATETIME(6) NULL;

UPDATE group_members
SET public_id = LOWER(UUID())
WHERE public_id IS NULL;

UPDATE group_members
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE group_members
    MODIFY COLUMN public_id VARCHAR(36) NOT NULL;
ALTER TABLE group_members
    MODIFY COLUMN status VARCHAR(20) NOT NULL;
ALTER TABLE group_members
    ADD CONSTRAINT uk_group_members_public_id UNIQUE (public_id);

CREATE TABLE chores (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    group_id BIGINT NOT NULL,
    created_by_membership_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    eligibility_mode VARCHAR(30) NOT NULL,
    due_time TIME(6) NOT NULL,
    weekly_due_day VARCHAR(10) NULL,
    biweekly_anchor_date DATE NULL,
    active BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chores_public_id UNIQUE (public_id),
    CONSTRAINT fk_chores_group
        FOREIGN KEY (group_id) REFERENCES sharing_groups (id),
    CONSTRAINT fk_chores_created_by_membership
        FOREIGN KEY (created_by_membership_id) REFERENCES group_members (id),
    INDEX idx_chores_group_active (group_id, active),
    UNIQUE INDEX idx_chores_public_id (public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chore_eligible_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chore_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chore_eligible_members_chore_member
        UNIQUE (chore_id, membership_id),
    CONSTRAINT fk_chore_eligible_members_chore
        FOREIGN KEY (chore_id) REFERENCES chores (id),
    CONSTRAINT fk_chore_eligible_members_membership
        FOREIGN KEY (membership_id) REFERENCES group_members (id),
    INDEX idx_chore_eligible_members_member (membership_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chore_occurrences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    chore_id BIGINT NOT NULL,
    frequency_snapshot VARCHAR(20) NOT NULL,
    time_zone_id_snapshot VARCHAR(40) NOT NULL,
    period_start DATE NOT NULL,
    period_end_exclusive DATE NOT NULL,
    due_at DATETIME(6) NOT NULL,
    eligibility_snapshot_version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    attention_reason VARCHAR(60) NULL,
    attention_since DATETIME(6) NULL,
    last_decision_at DATETIME(6) NULL,
    current_assignment_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chore_occurrences_public_id UNIQUE (public_id),
    CONSTRAINT uk_chore_occurrences_chore_period
        UNIQUE (chore_id, period_start),
    CONSTRAINT uk_chore_occurrences_current_assignment
        UNIQUE (current_assignment_id),
    CONSTRAINT fk_chore_occurrences_chore
        FOREIGN KEY (chore_id) REFERENCES chores (id),
    INDEX idx_chore_occurrences_status_due (status, due_at),
    INDEX idx_chore_occurrences_current_assignment (current_assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE occurrence_eligible_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurrence_id BIGINT NOT NULL,
    snapshot_version INTEGER NOT NULL,
    membership_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_occurrence_eligible_members_snapshot_member
        UNIQUE (occurrence_id, snapshot_version, membership_id),
    CONSTRAINT fk_occurrence_eligible_members_occurrence
        FOREIGN KEY (occurrence_id) REFERENCES chore_occurrences (id),
    CONSTRAINT fk_occurrence_eligible_members_membership
        FOREIGN KEY (membership_id) REFERENCES group_members (id),
    INDEX idx_occurrence_eligible_members_member (membership_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chore_assignment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurrence_id BIGINT NOT NULL,
    assignee_membership_id BIGINT NOT NULL,
    sequence_number INTEGER NOT NULL,
    trigger_type VARCHAR(40) NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    end_reason VARCHAR(40) NULL,
    active_marker INTEGER NULL,
    algorithm_version VARCHAR(30) NOT NULL,
    decision_seed BIGINT NOT NULL,
    candidate_snapshot LONGTEXT NOT NULL,
    decision_summary VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assignment_attempts_occurrence_sequence
        UNIQUE (occurrence_id, sequence_number),
    CONSTRAINT uk_assignment_attempts_one_active
        UNIQUE (occurrence_id, active_marker),
    CONSTRAINT fk_assignment_attempts_occurrence
        FOREIGN KEY (occurrence_id) REFERENCES chore_occurrences (id),
    CONSTRAINT fk_assignment_attempts_assignee
        FOREIGN KEY (assignee_membership_id) REFERENCES group_members (id),
    INDEX idx_assignment_attempts_assignee (assignee_membership_id),
    INDEX idx_assignment_attempts_occurrence_end (occurrence_id, ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rotation_decision_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurrence_id BIGINT NOT NULL,
    decision_sequence INTEGER NOT NULL,
    trigger_type VARCHAR(40) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    no_candidate_reason VARCHAR(60) NULL,
    selected_membership_id BIGINT NULL,
    algorithm_version VARCHAR(30) NOT NULL,
    decision_seed BIGINT NOT NULL,
    candidate_snapshot LONGTEXT NOT NULL,
    decision_summary VARCHAR(500) NOT NULL,
    decided_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rotation_decision_logs_occurrence_sequence
        UNIQUE (occurrence_id, decision_sequence),
    CONSTRAINT fk_rotation_decision_logs_occurrence
        FOREIGN KEY (occurrence_id) REFERENCES chore_occurrences (id),
    CONSTRAINT fk_rotation_decision_logs_selected_member
        FOREIGN KEY (selected_membership_id) REFERENCES group_members (id),
    INDEX idx_rotation_decision_logs_occurrence (occurrence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
