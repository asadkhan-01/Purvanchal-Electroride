-- A vehicle can carry several battery packs; each pack serial is unique across all stock.
CREATE TABLE vehicle_batteries (
  vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
  position INTEGER NOT NULL CHECK (position >= 0 AND position < 6),
  serial VARCHAR(120) NOT NULL UNIQUE CHECK (length(trim(serial)) > 0),
  PRIMARY KEY (vehicle_id, position)
);

INSERT INTO vehicle_batteries (vehicle_id, position, serial)
SELECT id, 0, battery_serial FROM vehicles;

ALTER TABLE vehicles DROP COLUMN battery_serial;
