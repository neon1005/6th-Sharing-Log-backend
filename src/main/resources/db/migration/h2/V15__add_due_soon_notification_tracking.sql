ALTER TABLE chore_occurrences ADD COLUMN due_soon_24h_notified_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE chore_occurrences ADD COLUMN due_soon_3h_notified_at TIMESTAMP(6) WITH TIME ZONE;
