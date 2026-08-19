ALTER TABLE chore_occurrences
    ADD CONSTRAINT fk_chore_occurrences_current_assignment
    FOREIGN KEY (current_assignment_id)
    REFERENCES chore_assignment_attempts (id);
