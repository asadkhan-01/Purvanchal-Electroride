-- Past sales recorded by a manager from the paper invoice book. They keep the original paper
-- invoice number and dates, and never consume an app invoice/receipt number.
ALTER TABLE bookings ADD COLUMN recorded_from_paper BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE invoices ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'APP' CHECK (source IN ('APP', 'PAPER'));
ALTER TABLE invoices ADD COLUMN paper_book_number VARCHAR(20);
ALTER TABLE invoices ADD COLUMN paper_invoice_number VARCHAR(40);
ALTER TABLE invoices ADD CONSTRAINT invoice_paper_consistent CHECK (
  (source = 'APP' AND paper_invoice_number IS NULL AND paper_book_number IS NULL)
  OR (source = 'PAPER' AND length(trim(coalesce(paper_invoice_number, ''))) > 0));
CREATE UNIQUE INDEX ux_invoices_paper_number ON invoices (branch, coalesce(paper_book_number, ''), paper_invoice_number)
  WHERE source = 'PAPER';
