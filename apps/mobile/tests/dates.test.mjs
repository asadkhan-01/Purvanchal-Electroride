import assert from 'node:assert/strict';
import test from 'node:test';
import { parseDmy, toDmy, toDmyTime } from '../dates.ts';

test('dd/mm/yyyy input is parsed to ISO and validated', () => {
  assert.equal(parseDmy('13/10/2026'), '2026-10-13');
  assert.equal(parseDmy('3/1/2026'), '2026-01-03');
  assert.equal(parseDmy('03-01-2026'), '2026-01-03');
  assert.equal(parseDmy('03.01.2026'), '2026-01-03');
  assert.equal(parseDmy('31/02/2026'), null);
  assert.equal(parseDmy('2026-10-13'), null);
  assert.equal(parseDmy('13/13/2026'), null);
  assert.equal(parseDmy(''), null);
});

test('ISO dates and timestamps display as dd/mm/yyyy in Indian time', () => {
  assert.equal(toDmy('2026-10-13'), '13/10/2026');
  assert.equal(toDmy(null), '');
  assert.equal(toDmyTime('2026-10-13T20:00:00Z'), '14/10/2026, 01:30');
});
