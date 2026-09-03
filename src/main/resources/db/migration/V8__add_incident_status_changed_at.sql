ALTER TABLE incidents ADD COLUMN status_changed_at TIMESTAMP;

UPDATE incidents
SET status_changed_at = created_at
WHERE status_changed_at IS NULL;

ALTER TABLE incidents ALTER COLUMN status_changed_at SET NOT NULL;
