CREATE TABLE delivery_orders (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    customer_reference VARCHAR(100) NOT NULL,
    sender_name VARCHAR(120) NOT NULL,
    sender_postal_code VARCHAR(10) NOT NULL,
    sender_country_code VARCHAR(2) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    recipient_postal_code VARCHAR(10) NOT NULL,
    recipient_country_code VARCHAR(2) NOT NULL,
    weight_in_grams INTEGER NOT NULL CHECK (weight_in_grams > 0),
    delivery_type VARCHAR(20) NOT NULL,
    price_amount NUMERIC(10, 2),
    price_currency VARCHAR(3),
    rejection_code VARCHAR(64),
    rejection_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_delivery_orders_created_at ON delivery_orders (created_at);
