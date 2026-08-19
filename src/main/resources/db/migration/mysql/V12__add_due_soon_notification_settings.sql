ALTER TABLE group_members
    ADD COLUMN daily_due_soon_hours INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN weekly_due_soon_hours INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN biweekly_due_soon_hours INTEGER NOT NULL DEFAULT 5;
