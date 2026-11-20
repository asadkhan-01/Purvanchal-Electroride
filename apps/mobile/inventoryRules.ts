import type { CurrentUser } from './api/enquiries';
import type { Vehicle } from './api/inventory';

export function canManageBranch(user: Pick<CurrentUser, 'role' | 'branch'>, branch: string): boolean {
  return user.role === 'ADMIN' || (user.role === 'BRANCH_MANAGER' && user.branch === branch);
}

export function canReceiveVehicle(user: Pick<CurrentUser, 'role' | 'branch'>,
  vehicle: Pick<Vehicle, 'status' | 'transferDestination'>): boolean {
  return vehicle.status === 'IN_TRANSIT' && !!vehicle.transferDestination
    && canManageBranch(user, vehicle.transferDestination);
}

export function isRegistrationComplete(vehicle: {
  chassis: string; model: string; variant: string; branch: string;
  motorSerial: string; batterySerials: string[]; chargerSerial: string; controllerSerial: string;
}): boolean {
  const batteries = vehicle.batterySerials.map(b => b.trim().toUpperCase());
  return [vehicle.chassis, vehicle.model, vehicle.variant, vehicle.branch, vehicle.motorSerial,
    vehicle.chargerSerial, vehicle.controllerSerial].every(value => value.trim().length > 0)
    && batteries.length > 0 && batteries.every(b => b.length > 0) && new Set(batteries).size === batteries.length;
}

export function allowedAction(user: Pick<CurrentUser, 'role' | 'branch'>,
  vehicle: Pick<Vehicle, 'branch' | 'status' | 'transferDestination'>,
  action: 'reserve' | 'release' | 'transfer' | 'receive'): boolean {
  if (action === 'receive') return canReceiveVehicle(user, vehicle);
  if (!canManageBranch(user, vehicle.branch)) return false;
  if (action === 'release') return vehicle.status === 'RESERVED';
  return vehicle.status === 'AVAILABLE';
}
