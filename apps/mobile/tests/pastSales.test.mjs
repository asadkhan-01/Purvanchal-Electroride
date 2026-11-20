import assert from 'node:assert/strict';
import test from 'node:test';
import { previewTotals } from '../billingRules.ts';

test('preview matches server rounding for GST-inclusive prices', () => {
  const t = previewTotals(47619.05, 5, false, 0);
  assert.equal(t.cgst, 1190.48); assert.equal(t.sgst, 1190.48);
  assert.equal(t.roundOff, -0.01); assert.equal(t.grandTotal, 50000);
  const i = previewTotals(95000, 18, true, 5000);
  assert.equal(i.igst, 17100); assert.equal(i.total, 112100); assert.equal(i.grandTotal, 107100);
});
