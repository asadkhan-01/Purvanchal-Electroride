import assert from 'node:assert/strict';
import test from 'node:test';
import { allowedAction, canManageBranch, canReceiveVehicle, isRegistrationComplete } from '../inventoryRules.ts';

const admin = { role: 'ADMIN', branch: null };
const manager = { role: 'BRANCH_MANAGER', branch: 'Varanasi' };
const otherManager = { role: 'BRANCH_MANAGER', branch: 'Ghazipur' };
const salesperson = { role: 'SALESPERSON', branch: 'Varanasi' };
const available = { status: 'AVAILABLE', branch: 'Varanasi', transferDestination: null };
const reserved = { ...available, status: 'RESERVED' };
const incoming = { status: 'IN_TRANSIT', branch: 'Ghazipur', transferDestination: 'Varanasi' };

test('admin can manage any branch; managers only their assigned branch', () => {
  assert.equal(canManageBranch(admin, 'Ghazipur'), true);
  assert.equal(canManageBranch(manager, 'Varanasi'), true);
  assert.equal(canManageBranch(manager, 'Ghazipur'), false);
  assert.equal(canManageBranch(salesperson, 'Varanasi'), false);
});

test('only authorized managers can reserve, release and transfer', () => {
  assert.equal(allowedAction(manager, available, 'reserve'), true);
  assert.equal(allowedAction(manager, reserved, 'release'), true);
  assert.equal(allowedAction(manager, reserved, 'transfer'), false);
  assert.equal(allowedAction(manager, incoming, 'reserve'), false);
  assert.equal(allowedAction(otherManager, available, 'reserve'), false);
  assert.equal(allowedAction(salesperson, available, 'reserve'), false);
});

test('only destination manager or admin can receive in-transit stock', () => {
  assert.equal(canReceiveVehicle(manager, incoming), true);
  assert.equal(canReceiveVehicle(admin, incoming), true);
  assert.equal(canReceiveVehicle(otherManager, incoming), false);
  assert.equal(canReceiveVehicle(salesperson, incoming), false);
  assert.equal(canReceiveVehicle(manager, available), false);
});

test('registration requires all serials and chassis details', () => {
  const valid = {
    chassis: 'TEST-1', model: 'EV', variant: 'Standard', branch: 'Varanasi',
    motorSerial: 'M1', batterySerial: 'B1', chargerSerial: 'C1', controllerSerial: 'CTL1',
  };
  assert.equal(isRegistrationComplete(valid), true);
  assert.equal(isRegistrationComplete({ ...valid, batterySerial: ' ' }), false);
  assert.equal(isRegistrationComplete({ ...valid, chassis: '' }), false);
});
