CREATE TABLE equipments (
    id BIGSERIAL PRIMARY KEY,
    facility_id BIGINT NOT NULL REFERENCES facilities(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL
);

CREATE INDEX idx_equipments_facility_id ON equipments(facility_id);
