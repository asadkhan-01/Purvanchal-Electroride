CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    enquiry_id BIGINT NOT NULL REFERENCES enquiries(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
    branch VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    prior_enquiry_stage VARCHAR(30) NOT NULL,
    created_by VARCHAR(80) NOT NULL,
    confirmed_by VARCHAR(80),
    cancelled_by VARCHAR(80),
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT booking_status_valid CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);
-- Guarantees no two live bookings can reserve the same chassis or enquiry,
-- including when requests race across different server instances.
CREATE UNIQUE INDEX ux_bookings_live_vehicle ON bookings(vehicle_id)
    WHERE status IN ('PENDING', 'CONFIRMED');
CREATE UNIQUE INDEX ux_bookings_live_enquiry ON bookings(enquiry_id)
    WHERE status IN ('PENDING', 'CONFIRMED');
CREATE INDEX idx_bookings_branch ON bookings(branch);
CREATE TABLE booking_events (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
    event VARCHAR(40) NOT NULL,
    actor_username VARCHAR(80) NOT NULL,
    details VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_booking_events_booking ON booking_events(booking_id, id DESC);
