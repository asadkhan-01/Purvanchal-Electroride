-- Extend the existing V5 schema without changing an already-applied Flyway migration.
ALTER TABLE bookings ALTER COLUMN enquiry_id DROP NOT NULL;
ALTER TABLE bookings ALTER COLUMN prior_enquiry_stage DROP NOT NULL;
ALTER TABLE bookings ADD COLUMN customer_name VARCHAR(160);
ALTER TABLE bookings ADD COLUMN customer_phone VARCHAR(20);
ALTER TABLE bookings ADD COLUMN salesperson VARCHAR(120);

-- Preserve customer and ownership details for all existing enquiry-based bookings.
UPDATE bookings b SET
  customer_name = e.customer_name,
  customer_phone = e.phone,
  salesperson = e.salesperson
FROM enquiries e WHERE b.enquiry_id = e.id;

ALTER TABLE bookings ALTER COLUMN customer_name SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN customer_phone SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN salesperson SET NOT NULL;

ALTER TABLE bookings ADD CONSTRAINT booking_customer_name_nonblank
  CHECK (length(trim(customer_name)) > 0);
ALTER TABLE bookings ADD CONSTRAINT booking_customer_phone_valid
  CHECK (customer_phone ~ '^[0-9]{10}$');
ALTER TABLE bookings ADD CONSTRAINT booking_origin_consistent
  CHECK (
    (enquiry_id IS NULL AND prior_enquiry_stage IS NULL)
    OR (enquiry_id IS NOT NULL AND prior_enquiry_stage IS NOT NULL)
  );
CREATE INDEX idx_bookings_branch_salesperson ON bookings(branch, salesperson);
