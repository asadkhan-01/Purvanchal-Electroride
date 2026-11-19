import assert from 'node:assert/strict';
import test from 'node:test';
import { bookingPayload } from '../bookingRules.ts';

test('enquiry booking contains only enquiry id and chassis', () => {
  assert.deepEqual(bookingPayload('enquiry', ' CH-001 ', 7, 'Ignored', '9876543210'), {
    enquiryId: 7, chassis: 'CH-001',
  });
});
test('direct booking contains customer information without enquiry id', () => {
  assert.deepEqual(bookingPayload('direct', 'CH-002', null, ' Test Buyer ', '9876543210'), {
    chassis: 'CH-002', customerName: 'Test Buyer', customerPhone: '9876543210',
  });
});
test('direct booking rejects malformed phone and missing customer', () => {
  assert.throws(() => bookingPayload('direct', 'CH-002', null, 'Buyer', '123'), /10-digit/);
  assert.throws(() => bookingPayload('direct', 'CH-002', null, ' ', '9876543210'), /customer/);
});
test('enquiry booking requires an existing enquiry id', () => {
  assert.throws(() => bookingPayload('enquiry', 'CH-001', null, '', ''), /existing enquiry/);
  assert.throws(() => bookingPayload('enquiry', 'CH-001', -1, '', ''), /existing enquiry/);
});
test('both workflows require chassis selection', () => {
  assert.throws(() => bookingPayload('direct', '', null, 'Buyer', '9876543210'), /chassis/);
});
