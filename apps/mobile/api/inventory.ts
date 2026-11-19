import { request } from './enquiries';

export type VehicleStatus = 'AVAILABLE' | 'RESERVED' | 'IN_TRANSIT' | 'SOLD';
export type Vehicle = {
  id: number;
  chassis: string;
  model: string;
  variant: string;
  colour: string | null;
  branch: string;
  status: VehicleStatus;
  motorSerial: string;
  batterySerial: string;
  chargerSerial: string;
  controllerSerial: string;
  transferDestination: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};
export type VehicleMovement = {
  id: number;
  vehicleId: number;
  event: string;
  fromBranch: string | null;
  toBranch: string | null;
  actorUsername: string;
  occurredAt: string | null;
};
export type NewVehicle = Pick<Vehicle, 'chassis' | 'model' | 'variant' | 'colour' | 'branch'
  | 'motorSerial' | 'batterySerial' | 'chargerSerial' | 'controllerSerial'>;

const vehiclePath = (chassis: string) => '/api/v1/inventory/' + encodeURIComponent(chassis);
export const inventoryApi = {
  list: () => request<Vehicle[]>('/api/v1/inventory'),
  get: (chassis: string) => request<Vehicle>(vehiclePath(chassis)),
  movements: (chassis: string) => request<VehicleMovement[]>(vehiclePath(chassis) + '/movements'),
  register: (vehicle: NewVehicle) => request<Vehicle>('/api/v1/inventory',
    { method: 'POST', body: JSON.stringify(vehicle) }),
  reserve: (chassis: string) => request<Vehicle>(vehiclePath(chassis) + '/reserve', { method: 'POST' }),
  release: (chassis: string) => request<Vehicle>(vehiclePath(chassis) + '/release', { method: 'POST' }),
  transfer: (chassis: string, destination: string) => request<Vehicle>(vehiclePath(chassis) + '/transfer',
    { method: 'POST', body: JSON.stringify({ destination }) }),
  receive: (chassis: string) => request<Vehicle>(vehiclePath(chassis) + '/receive', { method: 'POST' }),
};
