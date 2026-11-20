-- Branches and the address printed on their invoices.
CREATE TABLE branches (
  name VARCHAR(100) PRIMARY KEY,
  invoice_address VARCHAR(300) NOT NULL CHECK (length(trim(invoice_address)) > 0),
  active BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO branches (name, invoice_address) VALUES
  ('Atraoli-Ghazipur', 'Atraoli (Maharana Pratap ke Bagal Me) Ghazipur'),
  ('Naini-Prayagraj', 'LIC Campus, Mewala Ki Bagiya Naini, Prayagraj');

-- One-time go-live cleanup requested by the owner: remove all test/demo records created
-- during development. Administrator accounts are kept so the system can still be signed in to.
-- (Flyway runs this once per database; on a new empty database it deletes nothing.)
DELETE FROM payment_receipts;
DELETE FROM invoices;
DELETE FROM booking_quotations;
DELETE FROM booking_events;
DELETE FROM bookings;
DELETE FROM enquiry_activities;
DELETE FROM enquiries;
DELETE FROM vehicle_movements;
DELETE FROM vehicle_batteries;
DELETE FROM vehicles;
DELETE FROM document_sequences;
DELETE FROM app_users WHERE role <> 'ADMIN';
