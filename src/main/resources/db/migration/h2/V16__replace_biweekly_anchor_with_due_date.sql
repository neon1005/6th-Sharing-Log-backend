ALTER TABLE chores
    RENAME COLUMN biweekly_anchor_date TO biweekly_due_date;

UPDATE chores
SET biweekly_due_date = DATEADD('DAY', 13, biweekly_due_date)
WHERE frequency = 'BIWEEKLY'
  AND biweekly_due_date IS NOT NULL;
