// Shared shapes returned by the backend API.
export type Role = 'ADMIN' | 'SALES' | 'PRODUCTION' | 'ACCOUNTS';
export type User = { username: string; role: Role };
export type Order = {
  id: number;
  customer: string;
  product: string;
  qty: number;
  price: number;
  due: string;
  status: string;
  paid: number;
  bom: boolean;
  po: boolean;
  invoiced: boolean;
};
export type Payment = {
  id: number;
  amount: number;
  reference: string | null;
  recordedBy: string | null;
  recordedAt: string;
};
export type AuditEvent = {
  id: number;
  orderId: number;
  action: string;
  actor: string | null;
  recordedAt: string;
};
