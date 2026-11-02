import { Component, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiClient, ApiError } from './core/api-client.service';
import { AuditEvent, Order, Payment, Role, User } from './models/order.models';
import { createDemoOrders } from './demo/demo-orders';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
})
export class AppComponent {
  // Dependencies and screen state
  private client = inject(ApiClient);
  readonly api = new URLSearchParams(location.search).get('mode') !== 'demo';
  state: 'loading' | 'login' | 'ready' | 'offline' = 'loading';
  user: User | null = null;
  Math = Math;
  view = 'Overview';
  filter = 'All orders';
  query = '';
  showNew = false;
  selected: Order | null = null;
  payment = 0;
  paymentReference = '';
  busy = false;
  error = '';
  success = '';
  lastLoaded = new Date();
  loginUsername = '';
  loginPassword = '';
  members: User[] = [];
  newUser: { username: string; password: string; role: Role } = {
    username: '',
    password: '',
    role: 'SALES',
  };
  ledger: Payment[] = [];
  ledgerLoading = false;
  private demoPayments: Record<string, Payment[]> = {};
  private pendingPayment: {
    orderId: number;
    amount: number;
    reference: string;
    key: string;
  } | null = null;
  stages = ['Confirmed', 'Materials ready', 'In production', 'Ready to ship', 'Delivered'];
  descriptions: Record<string, string> = {
    Orders: 'Manage customer orders from confirmation through delivery.',
    'Bill of materials': 'The right materials, in the right quantities.',
    'Purchase orders': 'Keep procurement connected to production.',
    'Invoices & payments': 'Turn delivered orders into collected revenue.',
    Customers: 'The relationships behind every order.',
    Team: 'Give each teammate the access they need.',
  };
  orders: Order[] = [];
  activity: string[] = [];
  draft = {
    customer: '',
    product: 'Precision steel brackets',
    qty: 100,
    price: 850,
    due: new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10),
  };

  // Startup: database mode and browser-only demo mode stay separate.
  constructor() {
    if (this.api) {
      void this.initialize();
      return;
    }
    this.user = { username: 'Demo workspace', role: 'ADMIN' };
    try {
      const saved = JSON.parse(localStorage.getItem('oc-demo-v2') || 'null');
      this.orders = Array.isArray(saved?.orders) ? saved.orders : createDemoOrders();
      this.activity = Array.isArray(saved?.activity) ? saved.activity : [];
      this.demoPayments = saved?.payments || {};
    } catch {
      this.orders = createDemoOrders();
    }
    this.state = 'ready';
  }

  @HostListener('document:keydown.escape')
  closeDialogs() {
    if (!this.busy) {
      this.selected = null;
      this.showNew = false;
    }
  }

  get navigation() {
    const items = [
      ['Overview', '◫'],
      ['Orders', '▤'],
      ['Bill of materials', '◇'],
      ['Purchase orders', '▱'],
      ['Invoices & payments', '▧'],
      ['Customers', '♙'],
      ['Activity', '◷'],
    ];
    if (this.api && this.user?.role === 'ADMIN') items.push(['Team', '♙']);
    return items;
  }
  navigate(view: string) {
    this.view = view;
    this.filter = 'All orders';
    this.query = '';
    this.error = '';
    this.success = '';
  }
  can(action: string): boolean {
    if (this.state !== 'ready' || !this.user) return false;
    if (this.user.role === 'ADMIN') return true;
    const roles: Record<string, Role> = {
      create: 'SALES',
      bom: 'PRODUCTION',
      po: 'PRODUCTION',
      advance: 'PRODUCTION',
      invoice: 'ACCOUNTS',
      payment: 'ACCOUNTS',
    };
    return roles[action] === this.user.role;
  }
  private clearWorkspace() {
    this.orders = [];
    this.activity = [];
    this.members = [];
    this.ledger = [];
    this.selected = null;
    this.showNew = false;
    this.user = null;
  }
  private fail(error: unknown) {
    this.error = error instanceof Error ? error.message : 'The action could not be completed.';
    if (error instanceof ApiError && (error.status === 0 || error.status === 401)) {
      this.clearWorkspace();
      this.state = error.status === 401 ? 'login' : 'offline';
    }
  }
  // Session and workspace loading
  async initialize() {
    this.state = 'loading';
    this.error = '';
    this.clearWorkspace();
    try {
      this.user = await this.client.get<User>('/auth/me');
      const [orders, events, members] = await Promise.all([
        this.client.get<Order[]>('/orders'),
        this.client.get<AuditEvent[]>('/activity'),
        this.user.role === 'ADMIN' ? this.client.get<User[]>('/admin/users') : Promise.resolve([]),
      ]);
      this.orders = orders;
      this.activity = events.map((e) => this.eventText(e));
      this.members = members;
      this.lastLoaded = new Date();
      this.state = 'ready';
      if (this.view === 'Team' && this.user.role !== 'ADMIN') this.view = 'Overview';
    } catch (error) {
      this.fail(error);
      if (this.state === 'loading') {
        this.clearWorkspace();
        this.state = 'offline';
      }
    }
  }
  async login() {
    if (this.busy) return;
    this.busy = true;
    this.error = '';
    try {
      await this.client.login(this.loginUsername.trim(), this.loginPassword);
      this.loginPassword = '';
      await this.initialize();
    } catch (error) {
      this.fail(error);
    } finally {
      this.loginPassword = '';
      this.busy = false;
    }
  }
  async logout() {
    if (this.busy) return;
    this.busy = true;
    this.error = '';
    try {
      await this.client.logout();
      this.clearWorkspace();
      this.pendingPayment = null;
      this.state = 'login';
    } catch (error) {
      this.fail(error);
    } finally {
      this.busy = false;
    }
  }
  private eventText(e: AuditEvent) {
    return (
      new Date(e.recordedAt).toLocaleString('en-IN') +
      ' · ' +
      (e.actor || 'Legacy user') +
      ' · ' +
      e.action +
      ' · OC-' +
      e.orderId
    );
  }
  async createUser() {
    if (this.busy || this.user?.role !== 'ADMIN' || !this.api) return;
    this.busy = true;
    this.error = '';
    this.success = '';
    try {
      const member = await this.client.post<User>('/admin/users', this.newUser);
      this.members = [...this.members, member];
      this.success = 'Account created for ' + member.username + '. Share their password privately.';
      this.newUser = { username: '', password: '', role: 'SALES' };
    } catch (error) {
      this.fail(error);
    } finally {
      this.newUser.password = '';
      this.busy = false;
    }
  }

  // Values derived from the current orders for the dashboard and lists
  money(v: number) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(v);
  }
  balance(o: Order) {
    return (Math.round(o.qty * o.price * 100) - Math.round(o.paid * 100)) / 100;
  }
  get total() {
    return this.orders.reduce((a, o) => a + o.qty * o.price, 0);
  }
  get active() {
    return this.orders.filter((o) => o.status !== 'Delivered').length;
  }
  get production() {
    return this.count('In production');
  }
  get completed() {
    return this.count('Delivered');
  }
  get outstanding() {
    return this.orders.filter((o) => o.invoiced).reduce((a, o) => a + this.balance(o), 0);
  }
  get unpaid() {
    return this.orders.filter((o) => o.invoiced && this.balance(o) > 0).length;
  }
  get attention() {
    return this.orders.filter((o) => !o.bom || (o.invoiced && this.balance(o) > 0));
  }
  count(s: string) {
    return this.orders.filter((o) => o.status === s).length;
  }
  get filtered() {
    return this.orders.filter(
      (o) =>
        (this.filter === 'All orders' || o.status === this.filter) &&
        JSON.stringify(o).toLowerCase().includes(this.query.toLowerCase()),
    );
  }
  get documentOrders() {
    return this.orders.filter((o) =>
      this.view === 'Bill of materials'
        ? o.bom
        : this.view === 'Purchase orders'
          ? o.po
          : o.invoiced,
    );
  }
  get customers() {
    return [...new Set(this.orders.map((o) => o.customer))];
  }
  customerCount(c: string) {
    return this.orders.filter((o) => o.customer === c).length;
  }

  // Dialogs and order entry
  openNew() {
    if (!this.can('create')) return;
    this.error = '';
    this.success = '';
    this.draft.customer = '';
    this.showNew = true;
  }
  async openOrder(o: Order) {
    this.selected = o;
    this.error = '';
    this.success = '';
    this.ledger = [];
    const pending = this.pendingPayment?.orderId === o.id ? this.pendingPayment : null;
    this.payment = pending?.amount || 0;
    this.paymentReference = pending?.reference || '';
    await this.loadLedger(o);
  }
  private async loadLedger(o: Order) {
    if (!o.invoiced) return;
    this.ledgerLoading = true;
    try {
      const result = this.api
        ? await this.client.get<Payment[]>('/orders/' + o.id + '/payments')
        : this.demoPayments[o.id] || [];
      if (this.selected?.id === o.id) this.ledger = result;
    } catch (error) {
      this.fail(error);
    } finally {
      this.ledgerLoading = false;
    }
  }
  private saveDemo(message: string) {
    this.activity.unshift(new Date().toLocaleString('en-IN') + ' · ' + message);
    this.lastLoaded = new Date();
    if (!this.api) {
      try {
        localStorage.setItem(
          'oc-demo-v2',
          JSON.stringify({
            orders: this.orders,
            activity: this.activity,
            payments: this.demoPayments,
          }),
        );
      } catch {
        this.error =
          'The demo changed in memory, but browser storage is unavailable. These changes may be lost when you leave.';
      }
    }
  }
  async create() {
    if (this.busy || !this.can('create')) return;
    this.error = '';
    if (
      !this.draft.customer.trim() ||
      !this.draft.due ||
      !Number.isInteger(this.draft.qty) ||
      this.draft.qty < 1 ||
      this.draft.qty > 1000000 ||
      !Number.isFinite(this.draft.price) ||
      this.draft.price < 1 ||
      this.draft.price > 100000000 ||
      Math.abs(this.draft.price * 100 - Math.round(this.draft.price * 100)) > 0.0001
    ) {
      this.error =
        'Enter a customer, valid date, whole quantity and price with at most two decimals.';
      return;
    }
    this.busy = true;
    try {
      const o: Order = this.api
        ? await this.client.post('/orders', this.draft)
        : {
            ...this.draft,
            customer: this.draft.customer.trim(),
            id: Math.max(1000, ...this.orders.map((o) => o.id)) + 1,
            status: 'Confirmed',
            paid: 0,
            bom: false,
            po: false,
            invoiced: false,
          };
      this.orders.unshift(o);
      this.saveDemo('Created order OC-' + o.id);
      this.showNew = false;
      await this.openOrder(o);
    } catch (error) {
      this.fail(error);
    } finally {
      this.busy = false;
    }
  }
  // Workflow actions. Keep pendingPayment unchanged when retrying an uncertain result.
  async act(o: Order, action: string) {
    if (this.busy || !this.can(action)) return;
    this.error = '';
    this.success = '';
    const reference = this.paymentReference.trim().toUpperCase();
    if (action === 'payment') {
      const replay =
        this.pendingPayment?.orderId === o.id &&
        this.pendingPayment?.amount === this.payment &&
        this.pendingPayment?.reference === reference;
      if (
        !Number.isFinite(this.payment) ||
        this.payment <= 0 ||
        Math.abs(this.payment * 100 - Math.round(this.payment * 100)) > 0.0001 ||
        (!replay && Math.round(this.payment * 100) > Math.round(this.balance(o) * 100)) ||
        !reference.match(/^[A-Z0-9][A-Z0-9._/-]{2,99}$/)
      ) {
        this.error =
          'Enter a positive amount with at most two decimals, within the balance, and a valid 3–100 character receipt/reference.';
        return;
      }
      if (!replay)
        this.pendingPayment = {
          orderId: o.id,
          amount: this.payment,
          reference,
          key: crypto.randomUUID(),
        };
    }
    this.busy = true;
    try {
      if (this.api) {
        Object.assign(
          o,
          await this.client.post<Order>(
            '/orders/' + o.id + '/' + action,
            action === 'payment' ? { amount: this.payment, reference } : {},
            action === 'payment' ? this.pendingPayment!.key : undefined,
          ),
        );
      } else {
        if (action === 'bom') o.bom = true;
        if (action === 'po' && o.bom) o.po = true;
        if (action === 'advance' && o.po)
          o.status = this.stages[Math.min(4, this.stages.indexOf(o.status) + 1)];
        if (action === 'invoice' && o.status === 'Delivered') o.invoiced = true;
        if (action === 'payment' && o.invoiced) {
          const entries = this.demoPayments[o.id] || [];
          if (entries.some((p) => p.reference === reference))
            throw new Error('This reference is already recorded for this order.');
          o.paid = (Math.round(o.paid * 100) + Math.round(this.payment * 100)) / 100;
          this.demoPayments[o.id] = [
            {
              id: Date.now(),
              amount: this.payment,
              reference,
              recordedBy: 'Demo user',
              recordedAt: new Date().toISOString(),
            },
            ...entries,
          ];
        }
      }
      this.saveDemo(action + ' · OC-' + o.id);
      if (action === 'payment') {
        this.pendingPayment = null;
        this.payment = 0;
        this.paymentReference = '';
      }
      await this.loadLedger(o);
    } catch (error) {
      this.fail(error);
    } finally {
      this.busy = false;
    }
  }
  // Export the visible order list; escape values that spreadsheet apps treat as formulas.
  exportCsv() {
    const rows = [
      ['Order', 'Customer', 'Product', 'Quantity', 'Value', 'Due date', 'Status'],
      ...this.filtered.map((o) => [
        'OC-' + o.id,
        o.customer,
        o.product,
        o.qty,
        (o.qty * o.price).toFixed(2),
        o.due,
        o.status,
      ]),
    ];
    const csv = rows
      .map((r) =>
        r
          .map(
            (v) =>
              '"' +
              String(v)
                .replace(/"/g, '""')
                .replace(/^[=+@-]/, "'") +
              '"',
          )
          .join(','),
      )
      .join('\r\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    a.download = 'ordercraft-orders.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }
}
