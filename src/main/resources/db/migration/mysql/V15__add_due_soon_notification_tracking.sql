ALTER TABLE chore_occurrences ADD COLUMN due_soon_24h_notified_at DATETIME(6) NULL;
ALTER TABLE chore_occurrences ADD COLUMN due_soon_3h_notified_at DATETIME(6) NULL;
