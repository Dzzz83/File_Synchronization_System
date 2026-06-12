ALTER TABLE shared_folder_requests ADD COLUMN requested_permission VARCHAR(20);
UPDATE shared_folder_requests SET requested_permission = 'READ' WHERE requested_permission IS NULL;
ALTER TABLE shared_folder_requests ALTER COLUMN requested_permission SET NOT NULL;