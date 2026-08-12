CREATE TABLE measurements (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipments(id),
    type VARCHAR(32) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_measurements_equipment_id ON measurements(equipment_id);
