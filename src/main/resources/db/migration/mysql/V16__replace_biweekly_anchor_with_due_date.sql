ALTER TABLE chores
    CHANGE COLUMN biweekly_anchor_date biweekly_due_date DATE NULL;

UPDATE chores
SET biweekly_due_date = DATE_ADD(biweekly_due_date, INTERVAL 13 DAY)
WHERE frequency = 'BIWEEKLY'
  AND biweekly_due_date IS NOT NULL;
