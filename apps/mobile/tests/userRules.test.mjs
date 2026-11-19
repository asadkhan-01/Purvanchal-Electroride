import assert from 'node:assert/strict';
import test from 'node:test';
import { validateNewUser, userRoles } from '../userRules.ts';

const base = {
  username: 'demo-sales', displayName: 'Demo Salesperson',
  password: 'test-only-Password-123', role: 'SALESPERSON', branch: 'Varanasi',
};
test('all three account roles are available', () => {
  assert.deepEqual([...userRoles], ['ADMIN', 'BRANCH_MANAGER', 'SALESPERSON']);
});
test('salesperson and branch manager require an assigned branch', () => {
  for (const role of ['SALESPERSON', 'BRANCH_MANAGER']) {
    assert.match(validateNewUser({ ...base, role, branch: null }), /Branch/);
  }
});
test('admin can be assigned all-branch scope', () => {
  assert.equal(validateNewUser({ ...base, role: 'ADMIN', branch: null }), null);
});
test('valid manager and salesperson accounts pass validation', () => {
  assert.equal(validateNewUser(base), null);
  assert.equal(validateNewUser({ ...base, role: 'BRANCH_MANAGER' }), null);
});
test('reject short password, malformed username, and missing display name', () => {
  assert.match(validateNewUser({ ...base, password: 'short' }), /12/);
  assert.match(validateNewUser({ ...base, username: 'bad user' }), /Username/);
  assert.match(validateNewUser({ ...base, displayName: '' }), /display name/);
});
