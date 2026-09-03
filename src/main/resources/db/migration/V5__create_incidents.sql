 CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL REFERENCES facilities(id),
    equipment_id BIGINT NOT NULL REFERENCES equipments(id),
    measurement_id BIGINT NOT NULL REFERENCES measurements(id),
    type VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    measurement_type VARCHAR(32) NOT NULL,
    measurement_unit VARCHAR(32) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_incidents_facility_id ON incidents(facility_id);
CREATE INDEX idx_incidents_equipment_id ON incidents(equipment_id);
CREATE INDEX idx_incidents_measurement_id ON incidents(measurement_id);
