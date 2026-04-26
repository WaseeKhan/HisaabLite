ALTER TABLE shops
MODIFY COLUMN plan_type ENUM('FREE', 'BASIC', 'PREMIUM', 'ENTERPRISE', 'PRO') NULL;

ALTER TABLE users
MODIFY COLUMN current_plan ENUM('FREE', 'BASIC', 'PREMIUM', 'ENTERPRISE', 'PRO') NULL;

ALTER TABLE upgrade_requests
MODIFY COLUMN current_plan ENUM('FREE', 'BASIC', 'PREMIUM', 'ENTERPRISE', 'PRO') NOT NULL;

ALTER TABLE upgrade_requests
MODIFY COLUMN requested_plan ENUM('FREE', 'BASIC', 'PREMIUM', 'ENTERPRISE', 'PRO') NOT NULL;

UPDATE subscription_plans
SET plan_name = 'PRO'
WHERE plan_name = 'PREMIUM'
  AND NOT EXISTS (
      SELECT 1
      FROM (
          SELECT plan_name
          FROM subscription_plans
          WHERE plan_name = 'PRO'
      ) existing_pro
  );

DELETE FROM subscription_plans
WHERE plan_name IN ('PREMIUM', 'ENTERPRISE');

UPDATE shops
SET plan_type = 'PRO'
WHERE plan_type IN ('PREMIUM', 'ENTERPRISE');

UPDATE users
SET current_plan = 'PRO'
WHERE current_plan IN ('PREMIUM', 'ENTERPRISE');

UPDATE upgrade_requests
SET current_plan = 'PRO'
WHERE current_plan IN ('PREMIUM', 'ENTERPRISE');

UPDATE upgrade_requests
SET requested_plan = 'PRO'
WHERE requested_plan IN ('PREMIUM', 'ENTERPRISE');

ALTER TABLE shops
MODIFY COLUMN plan_type ENUM('FREE', 'BASIC', 'PRO') NULL;

ALTER TABLE users
MODIFY COLUMN current_plan ENUM('FREE', 'BASIC', 'PRO') NULL;

ALTER TABLE upgrade_requests
MODIFY COLUMN current_plan ENUM('FREE', 'BASIC', 'PRO') NOT NULL;

ALTER TABLE upgrade_requests
MODIFY COLUMN requested_plan ENUM('FREE', 'BASIC', 'PRO') NOT NULL;
