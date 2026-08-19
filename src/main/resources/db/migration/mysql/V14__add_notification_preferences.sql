ALTER TABLE users ADD COLUMN due_soon_push_enabled BIT NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN chore_completed_push_enabled BIT NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN notice_push_enabled BIT NOT NULL DEFAULT 0;
