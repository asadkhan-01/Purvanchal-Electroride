CREATE TABLE vehicles (
 id BIGSERIAL PRIMARY KEY,
 chassis VARCHAR(100) NOT NULL UNIQUE,
 model VARCHAR(160) NOT NULL,
 variant VARCHAR(120) NOT NULL,
 colour VARCHAR(80),
 branch VARCHAR(100) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
 motor_serial VARCHAR(120) NOT NULL UNIQUE,
 battery_serial VARCHAR(120) NOT NULL UNIQUE,
 charger_serial VARCHAR(120) NOT NULL UNIQUE,
 controller_serial VARCHAR(120) NOT NULL UNIQUE,
 transfer_destination VARCHAR(100),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT vehicle_status_valid CHECK (status IN ('AVAILABLE','RESERVED','IN_TRANSIT','SOLD')),
 CONSTRAINT transfer_state_valid CHECK (
   (status = 'IN_TRANSIT' AND transfer_destination IS NOT NULL AND branch <> transfer_destination)
   OR (status <> 'IN_TRANSIT' AND transfer_destination IS NULL)
 )
);
CREATE INDEX idx_vehicle_branch_status ON vehicles(branch, status);
CREATE INDEX idx_vehicle_transfer_destination ON vehicles(transfer_destination);

CREATE TABLE vehicle_movements (
 id BIGSERIAL PRIMARY KEY,
 vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
 event VARCHAR(40) NOT NULL,
 from_branch VARCHAR(100),
 to_branch VARCHAR(100),
 actor_username VARCHAR(80) NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_vehicle_movements_vehicle_id ON vehicle_movements(vehicle_id, id DESC);
