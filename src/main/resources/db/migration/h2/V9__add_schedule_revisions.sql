ALTER TABLE chores
    ADD COLUMN schedule_revision BIGINT DEFAULT 0 NOT NULL;

ALTER TABLE chore_occurrences
    ADD COLUMN schedule_revision_snapshot BIGINT DEFAULT 0 NOT NULL;

ALTER TABLE chore_occurrences
    DROP CONSTRAINT uk_chore_occurrences_chore_period;

ALTER TABLE chore_occurrences
    ADD CONSTRAINT uk_chore_occurrences_chore_period
        UNIQUE (chore_id, schedule_revision_snapshot, period_start);
