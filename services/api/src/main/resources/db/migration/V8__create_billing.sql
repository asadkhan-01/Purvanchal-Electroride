-- Sales billing: payment receipts, GST tax invoices and vehicle delivery.
-- Amounts are INR with two decimals. GST treatment must be reviewed by the dealership's accountant.

-- A booking now completes with delivery (vehicle handed over, stock SOLD).
ALTER TABLE bookings DROP CONSTRAINT booking_status_valid;
ALTER TABLE bookings ADD CONSTRAINT booking_status_valid
  CHECK (status IN ('PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED'));
ALTER TABLE bookings ADD COLUMN delivered_by VARCHAR(80);
ALTER TABLE bookings ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE bookings ADD CONSTRAINT booking_delivery_consistent CHECK (
  (status = 'DELIVERED' AND delivered_by IS NOT NULL AND delivered_at IS NOT NULL)
  OR (status <> 'DELIVERED' AND delivered_by IS NULL AND delivered_at IS NULL)
);

-- Gapless per-branch, per-financial-year numbering for invoices (INV) and receipts (RCT).
CREATE TABLE document_sequences (
  doc_type VARCHAR(10) NOT NULL,
  branch VARCHAR(100) NOT NULL,
  financial_year VARCHAR(9) NOT NULL,
  last_number INTEGER NOT NULL CHECK (last_number > 0),
  PRIMARY KEY (doc_type, branch, financial_year)
);

-- One immutable tax invoice per booking, snapshotting customer, vehicle and quotation figures.
CREATE TABLE invoices (
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE RESTRICT,
  invoice_number VARCHAR(40) NOT NULL UNIQUE,
  branch VARCHAR(100) NOT NULL,
  invoice_date DATE NOT NULL,
  customer_name VARCHAR(160) NOT NULL,
  customer_phone VARCHAR(20) NOT NULL,
  customer_address VARCHAR(300) NOT NULL CHECK (length(trim(customer_address)) > 0),
  customer_gstin VARCHAR(15) CHECK (customer_gstin IS NULL OR customer_gstin ~ '^[0-9]{2}[A-Z0-9]{13}$'),
  customer_relation VARCHAR(3) CHECK (customer_relation IN ('S/o', 'D/o', 'W/o', 'C/o')),
  customer_guardian_name VARCHAR(160),
  id_type VARCHAR(20) CHECK (id_type IN ('PAN', 'DRIVING_LICENCE', 'VOTER_ID', 'PASSPORT', 'OTHER')),
  id_number VARCHAR(40),
  -- Only the last four Aadhaar digits are kept (UIDAI masking); never the full number.
  aadhaar_last4 VARCHAR(4) CHECK (aadhaar_last4 ~ '^[0-9]{4}$'),
  financier VARCHAR(120),
  dealer_gstin VARCHAR(15),
  dealer_address VARCHAR(300),
  dealer_phone VARCHAR(20),
  chassis VARCHAR(100) NOT NULL,
  model VARCHAR(160) NOT NULL,
  variant VARCHAR(120) NOT NULL,
  colour VARCHAR(80),
  motor_serial VARCHAR(120) NOT NULL,
  battery_serials VARCHAR(800) NOT NULL,
  charger_serial VARCHAR(120) NOT NULL,
  controller_serial VARCHAR(120) NOT NULL,
  vehicle_price NUMERIC(14,2) NOT NULL,
  discount NUMERIC(14,2) NOT NULL,
  accessories NUMERIC(14,2) NOT NULL,
  other_charges NUMERIC(14,2) NOT NULL,
  taxable_value NUMERIC(14,2) NOT NULL CHECK (taxable_value > 0),
  gst_rate NUMERIC(5,2) NOT NULL CHECK (gst_rate >= 0 AND gst_rate <= 28),
  tax_type VARCHAR(10) NOT NULL CHECK (tax_type IN ('CGST_SGST', 'IGST')),
  cgst NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (cgst >= 0),
  sgst NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (sgst >= 0),
  igst NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (igst >= 0),
  round_off NUMERIC(4,2) NOT NULL CHECK (round_off > -1 AND round_off < 1),
  subsidy NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (subsidy >= 0),
  grand_total NUMERIC(14,2) NOT NULL CHECK (grand_total > 0),
  issued_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT invoice_taxable_consistent CHECK (
    taxable_value = vehicle_price - discount + accessories + other_charges),
  CONSTRAINT invoice_tax_split_consistent CHECK (
    (tax_type = 'CGST_SGST' AND igst = 0 AND cgst = sgst)
    OR (tax_type = 'IGST' AND cgst = 0 AND sgst = 0)),
  CONSTRAINT invoice_total_consistent CHECK (
    grand_total = taxable_value + cgst + sgst + igst + round_off - subsidy),
  CONSTRAINT invoice_id_consistent CHECK ((id_type IS NULL) = (id_number IS NULL)),
  CONSTRAINT invoice_relation_consistent CHECK ((customer_relation IS NULL) = (customer_guardian_name IS NULL))
);
CREATE INDEX idx_invoices_branch_date ON invoices(branch, invoice_date);

-- Money received against a booking. Receipts are never deleted; a mistaken or refunded
-- receipt is VOIDED with a reason by a manager.
CREATE TABLE payment_receipts (
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
  receipt_number VARCHAR(40) NOT NULL UNIQUE,
  branch VARCHAR(100) NOT NULL,
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  mode VARCHAR(20) NOT NULL CHECK (mode IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'CHEQUE', 'FINANCE')),
  reference VARCHAR(100),
  finance_company VARCHAR(120),
  received_on DATE NOT NULL,
  notes VARCHAR(300),
  status VARCHAR(10) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED', 'VOIDED')),
  recorded_by VARCHAR(80) NOT NULL,
  voided_by VARCHAR(80),
  void_reason VARCHAR(300),
  voided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT receipt_reference_required CHECK (mode = 'CASH' OR length(trim(coalesce(reference, ''))) > 0),
  CONSTRAINT receipt_finance_company_required CHECK (
    mode <> 'FINANCE' OR length(trim(coalesce(finance_company, ''))) > 0),
  CONSTRAINT receipt_void_consistent CHECK (
    (status = 'RECEIVED' AND voided_by IS NULL AND void_reason IS NULL AND voided_at IS NULL)
    OR (status = 'VOIDED' AND voided_by IS NOT NULL AND void_reason IS NOT NULL AND voided_at IS NOT NULL))
);
CREATE INDEX idx_receipts_booking ON payment_receipts(booking_id);
CREATE INDEX idx_receipts_branch_date ON payment_receipts(branch, received_on);
