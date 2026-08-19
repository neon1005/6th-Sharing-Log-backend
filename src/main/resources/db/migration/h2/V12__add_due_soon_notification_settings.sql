ALTER TABLE group_members
    ADD COLUMN daily_due_soon_hours INTEGER NOT NULL DEFAULT 5;

ALTER TABLE group_members
    ADD COLUMN weekly_due_soon_hours INTEGER NOT NULL DEFAULT 5;

ALTER TABLE group_members
    ADD COLUMN biweekly_due_soon_hours INTEGER NOT NULL DEFAULT 5;
