CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NULL,
    attempt INT NOT NULL DEFAULT 0,
    error_message TEXT NULL
);
