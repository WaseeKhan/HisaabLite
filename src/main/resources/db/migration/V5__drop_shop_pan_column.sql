SET @pan_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'shops'
      AND index_name = 'uk_shops_pan_number'
);

SET @drop_pan_index_sql := IF(
    @pan_index_exists > 0,
    'ALTER TABLE shops DROP INDEX uk_shops_pan_number',
    'SELECT 1'
);

PREPARE drop_pan_index_stmt FROM @drop_pan_index_sql;
EXECUTE drop_pan_index_stmt;
DEALLOCATE PREPARE drop_pan_index_stmt;

SET @pan_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'shops'
      AND column_name = 'pan_number'
);

SET @drop_pan_column_sql := IF(
    @pan_column_exists > 0,
    'ALTER TABLE shops DROP COLUMN pan_number',
    'SELECT 1'
);

PREPARE drop_pan_column_stmt FROM @drop_pan_column_sql;
EXECUTE drop_pan_column_stmt;
DEALLOCATE PREPARE drop_pan_column_stmt;
