ALTER TABLE subscription_plans
ADD COLUMN annual_price DOUBLE NULL,
ADD COLUMN annual_discount_percent DOUBLE NULL;

UPDATE subscription_plans
SET annual_discount_percent = CASE
        WHEN price IS NULL OR price <= 0 THEN 0
        ELSE 17
    END
WHERE annual_discount_percent IS NULL;

UPDATE subscription_plans
SET annual_price = CASE
        WHEN price IS NULL OR price <= 0 THEN 0
        ELSE ROUND((price * 12) * (1 - (annual_discount_percent / 100)), 2)
    END
WHERE annual_price IS NULL;

UPDATE subscription_plans
SET duration_in_days = 365
WHERE duration_in_days IS NULL OR duration_in_days < 365;

ALTER TABLE upgrade_requests
ADD COLUMN requested_annual_price DOUBLE NULL,
ADD COLUMN requested_duration_in_days INT NULL;
