ALTER TABLE group_members ADD COLUMN version BIGINT;

UPDATE group_members
SET version = 0
WHERE version IS NULL;

ALTER TABLE group_members ALTER COLUMN version SET NOT NULL;

ALTER TABLE chore_assignment_attempts
    ADD COLUMN actor_note VARCHAR(500);
