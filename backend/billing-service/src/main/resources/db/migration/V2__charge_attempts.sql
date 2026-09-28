ALTER TABLE billing_records
  ADD COLUMN IF NOT EXISTS charge_attempts INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_charge_error TEXT,
  ADD COLUMN IF NOT EXISTS last_attempted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_billing_status_not_charged
  ON billing_records(created_at)
  WHERE status = 'CALCULATED';
