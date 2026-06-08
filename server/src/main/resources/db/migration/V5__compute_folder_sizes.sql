-- V5__compute_folder_sizes.sql
-- This script computes the total size of all folders by summing the sizes of their files
-- (including nested files) and updates the size column.

-- First, ensure all folders have size 0 initially
UPDATE file_metadata SET size = 0 WHERE is_directory = TRUE AND (size IS NULL OR size != 0);

-- Recursive CTE to compute folder sizes
WITH RECURSIVE folder_tree AS (
    -- Anchor: get all files (non-directories) with their parent folder
    SELECT
        id,
        parent_id::text AS parent_id,   -- cast UUID to text for joining
        size,
        is_directory
    FROM file_metadata
    WHERE is_directory = FALSE

    UNION ALL

    -- Recursive: add folders that are parents of already collected nodes
    SELECT
        f.id,
        f.parent_id::text,
        f.size,
        f.is_directory
    FROM file_metadata f
    JOIN folder_tree ft ON ft.parent_id = f.id   -- f.id is text, ft.parent_id is text
    WHERE f.is_directory = TRUE
),
folder_totals AS (
    -- Sum sizes per folder
    SELECT
        parent_id AS folder_id,
        COALESCE(SUM(size), 0) AS total_size
    FROM folder_tree
    WHERE parent_id IS NOT NULL
    GROUP BY parent_id
)
-- Update folder sizes
UPDATE file_metadata
SET size = ft.total_size
FROM folder_totals ft
WHERE file_metadata.id = ft.folder_id   -- both are text now
  AND file_metadata.is_directory = TRUE;
