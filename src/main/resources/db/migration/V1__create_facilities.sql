CREATE TABLE facilities(
    id BIGSERIAL primary key,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL
);