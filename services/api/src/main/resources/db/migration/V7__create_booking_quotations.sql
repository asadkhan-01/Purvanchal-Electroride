-- Quotation is an internal, tax-exclusive estimate, NOT a GST invoice.
-- Exactly one immutable quotation per booking in this initial milestone.
CREATE TABLE booking_quotations (
 id BIGSERIAL PRIMARY KEY,
 booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE RESTRICT,
 vehicle_price NUMERIC(14,2) NOT NULL CHECK (vehicle_price > 0),
 discount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
 accessories NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (accessories >= 0),
 other_charges NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (other_charges >= 0),
 subtotal_excluding_tax NUMERIC(14,2) NOT NULL CHECK (subtotal_excluding_tax > 0),
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED')),
 created_by VARCHAR(80) NOT NULL,
 approved_by VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 approved_at TIMESTAMPTZ,
 CONSTRAINT quotation_discount_valid CHECK (discount <= vehicle_price),
 CONSTRAINT quotation_subtotal_consistent CHECK (
   subtotal_excluding_tax = vehicle_price - discount + accessories + other_charges
 ),
 CONSTRAINT quotation_approval_consistent CHECK (
   (status = 'DRAFT' AND approved_by IS NULL AND approved_at IS NULL)
   OR (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
 )
);
