CREATE TABLE IF NOT EXISTS billing_records (
    id UUID PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL UNIQUE,
    driver_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    distance_km NUMERIC(8,3) NOT NULL,
    duration_min NUMERIC(6,2) NOT NULL,
    surge_multiplier NUMERIC(3,2) NOT NULL DEFAULT 1.0,
    base_fare NUMERIC(8,2) NOT NULL,
    distance_fare NUMERIC(8,2) NOT NULL,
    time_fare NUMERIC(8,2) NOT NULL,
    total_fare NUMERIC(8,2) NOT NULL,
    commission NUMERIC(8,2) NOT NULL,
    driver_payout NUMERIC(8,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    status VARCHAR(20) NOT NULL DEFAULT 'CALCULATED',   -- CALCULATED | CHARGED | REFUNDED
    stripe_payment_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    charged_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_billing_trip_id ON billing_records(trip_id);
CREATE INDEX IF NOT EXISTS idx_billing_driver_id ON billing_records(driver_id);
CREATE INDEX IF NOT EXISTS idx_billing_client_id ON billing_records(client_id);
CREATE INDEX IF NOT EXISTS idx_billing_status ON billing_records(status);
CREATE INDEX IF NOT EXISTS idx_billing_created_at ON billing_records(created_at DESC);
