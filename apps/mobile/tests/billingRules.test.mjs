import assert from 'node:assert/strict';
import test from 'node:test';
import { validatePayment, validateInvoice } from '../billingRules.ts';

const pay = (o) => ({ amount: '1000', mode: 'UPI', reference: 'UTR1', financeCompany: '', receivedOn: '2026-11-20', ...o });

test('valid UPI and cash payments pass', () => {
  assert.equal(validatePayment(pay(), 5000), null);
  assert.equal(validatePayment(pay({ mode: 'CASH', reference: '' }), 5000), null);
});

test('payment amount, reference, finance company and date are checked', () => {
  assert.match(validatePayment(pay({ amount: '10.001' }), 5000), /two decimals/);
  assert.match(validatePayment(pay({ amount: '0' }), 5000), /positive/);
  assert.match(validatePayment(pay({ amount: '5000.01' }), 5000), /balance/);
  assert.match(validatePayment(pay({ reference: ' ' }), 5000), /reference/);
  assert.match(validatePayment(pay({ mode: 'FINANCE', reference: 'LN1' }), 5000), /finance company/);
  assert.match(validatePayment(pay({ receivedOn: 'invalid' }), 5000), /DD\/MM\/YYYY/);
});

test('invoice address, GSTIN and rate are checked', () => {
  assert.equal(validateInvoice({ customerAddress: 'Lanka, Varanasi', customerGstin: '', gstRate: '5' }), null);
  assert.equal(validateInvoice({ customerAddress: 'x', customerGstin: '09abcde1234f1z5', gstRate: '18' }), null);
  assert.match(validateInvoice({ customerAddress: ' ', customerGstin: '', gstRate: '5' }), /address/);
  assert.match(validateInvoice({ customerAddress: 'x', customerGstin: 'ABC', gstRate: '5' }), /GSTIN/);
  assert.match(validateInvoice({ customerAddress: 'x', customerGstin: '', gstRate: '30' }), /GST rate/);
  const ok = { customerAddress: 'x', customerGstin: '', gstRate: '5' };
  assert.match(validateInvoice({ ...ok, customerRelation: 'S/o', customerGuardianName: ' ' }), /guardian/);
  assert.match(validateInvoice({ ...ok, idType: 'PAN', idNumber: '' }), /ID number/);
  assert.match(validateInvoice({ ...ok, aadhaarLast4: '123412341234' }), /last 4/);
  assert.match(validateInvoice({ ...ok, subsidy: '10.555' }), /Subsidy/);
  assert.equal(validateInvoice({ ...ok, customerRelation: 'S/o', customerGuardianName: 'Test', idType: 'PAN', idNumber: 'X1', aadhaarLast4: '1234', subsidy: '5000' }), null);
});

test('CSV export quotes commas and quotes, and neutralises spreadsheet formulas', async () => {
  const { toCsv } = await import('../billingRules.ts');
  const csv = toCsv(['Name', 'Amount'], [['Kumar, Test', 50000], ['Say "hi"', 1], ['=HYPERLINK("x")', null]]);
  assert.ok(csv.startsWith('﻿Name,Amount\r\n'));
  assert.ok(csv.includes('"Kumar, Test",50000'));
  assert.ok(csv.includes('"Say ""hi""",1'));
  assert.ok(csv.includes(`"'=HYPERLINK(""x"")",`));
});
