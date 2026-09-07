-- V2: add stripe_customer_id for billing integration (Slice 16)

ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_users_stripe_customer_id ON users(stripe_customer_id);
