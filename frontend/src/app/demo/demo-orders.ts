import { Order } from '../models/order.models';

// Sample records for ?mode=demo only. Database mode never loads these.
export function createDemoOrders(): Order[] {
  return [
    'Tata Engineering',
    'Bharat Industries',
    'Apex Auto Components',
    'Larsen Manufacturing',
    'Precision Works',
    'Bharat Industries',
    'Tata Engineering',
  ].map((customer, i) => ({
    id: 1048 - i,
    customer,
    product: [
      'Precision steel brackets',
      'Industrial pump housing',
      'Aluminium mounting plates',
      'Gear assembly',
    ][i % 4],
    qty: [240, 80, 500, 120, 300, 60, 150][i],
    price: [850, 4200, 620, 2800, 850, 4200, 620][i],
    due: [
      '2026-10-28',
      '2026-10-30',
      '2026-11-02',
      '2026-11-04',
      '2026-10-25',
      '2026-10-22',
      '2026-10-21',
    ][i],
    status: [
      'In production',
      'Confirmed',
      'Materials ready',
      'In production',
      'Ready to ship',
      'Delivered',
      'Delivered',
    ][i],
    paid: i === 6 ? 93000 : i === 5 ? 50000 : 0,
    bom: i !== 1,
    po: i !== 1,
    invoiced: i > 4,
  }));
}
