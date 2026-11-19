import type { BookingRequest } from './api/bookings';

export function bookingPayload(
  mode: 'direct' | 'enquiry',
  chassis: string,
  enquiryId: number | null,
  customerName: string,
  customerPhone: string,
): BookingRequest {
  if (!chassis.trim()) throw new Error('Select an available chassis.');
  if (mode === 'enquiry') {
    if (enquiryId === null || !Number.isSafeInteger(enquiryId) || enquiryId <= 0)
      throw new Error('Select an existing enquiry.');
    return { enquiryId, chassis: chassis.trim() };
  }
  const name = customerName.trim();
  const phone = customerPhone.trim();
  if (!name || name.length > 160 || !/^[0-9]{10}$/.test(phone))
    throw new Error('Enter customer name and a 10-digit mobile number.');
  return { chassis: chassis.trim(), customerName: name, customerPhone: phone };
}
