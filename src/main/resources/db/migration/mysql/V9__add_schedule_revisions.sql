ALTER TABLE chores
    ADD COLUMN schedule_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE chore_occurrences
    ADD COLUMN schedule_revision_snapshot BIGINT NOT NULL DEFAULT 0;

ALTER TABLE chore_occurrences
    DROP INDEX uk_chore_occurrences_chore_period,
    ADD CONSTRAINT uk_chore_occurrences_chore_period
        UNIQUE (chore_id, schedule_revision_snapshot, period_start);
