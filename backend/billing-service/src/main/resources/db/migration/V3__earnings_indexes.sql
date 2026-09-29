CREATE INDEX IF NOT EXISTS idx_billing_driver_charged_at
    ON billing_records(driver_id, charged_at DESC)
    WHERE status = 'CHARGED';

CREATE INDEX IF NOT EXISTS idx_billing_driver_created_at
    ON billing_records(driver_id, created_at DESC);
