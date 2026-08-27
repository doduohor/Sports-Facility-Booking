CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_no_overlapping_facility_time
    EXCLUDE USING gist (
      facility_id WITH =,
      tsrange(start_time, end_time, '[)') WITH &&
  );