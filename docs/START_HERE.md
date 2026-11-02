# Understanding OrderCraft

Start here when reading the project or preparing to explain it in a viva.
The application keeps a customer order, its manufacturing documents, and its receipts connected.

## Read the code in this order

1. `frontend/src/app/models/order.models.ts`: learn the data fields first.
2. `frontend/src/app/app.component.html`: find the button or form you see on screen.
3. `frontend/src/app/app.component.ts`: find the method called by that button.
4. `frontend/src/app/core/api-client.service.ts`: see how the browser contacts the server.
5. `backend/src/main/java/com/ordercraft/orders/OrderController.java`: match the URL to a Java method.
6. `backend/src/main/java/com/ordercraft/orders/OrderService.java`: read its rules and SQL updates.
7. `backend/src/main/resources/db/migration/`: see which tables receive the data.
8. `backend/src/test/java/com/ordercraft/WorkflowTest.java`: follow a complete example through API tests.

Paths in this guide are relative to the project folder. Use Ctrl+P in VS Code to open a file,
and Ctrl+Shift+F to search for a method such as `generateBom`.

## One request from beginning to end

Suppose you click Generate BOM for order 1:

```text
app.component.html: (click)="act(o, 'bom')"
    -> AppComponent.act(order, 'bom')
    -> ApiClient.post('/orders/1/bom', {})
    -> POST /api/orders/1/bom
    -> SecurityConfig checks login, role and CSRF
    -> OrderController.action(...)
    -> OrderService.action(...)
    -> generateBom(...)
    -> INSERT bom_lines and UPDATE customer_orders
    -> updated order JSON returns to Angular
```

The controller translates HTTP requests into method calls. The service decides whether the action
is allowed and changes the database. The frontend displays the result. In this small project,
SQL stays in the service alongside the related rule, rather than adding a repository abstraction
that would only forward the same calls.

## Understand the order fields

| Field                   | Meaning                                                    |
| ----------------------- | ---------------------------------------------------------- |
| `id`                    | Database identifier; the UI displays it with the OC prefix |
| `qty`                   | Number of finished units                                   |
| `price`                 | Price per finished unit, not the total                     |
| `status`                | Current production/delivery stage                          |
| `paid`                  | Sum recorded as received                                   |
| `bom`, `po`, `invoiced` | Whether the corresponding document has been generated      |

Total = qty * price. Outstanding = total - paid.
Production status stays Delivered even when the invoice becomes paid.

## Important methods in the service

| Method                | Responsibility                                                |
| --------------------- | ------------------------------------------------------------- |
| `create`              | Insert a validated order and its creation event               |
| `action`              | Start one transaction, lock the order and dispatch its action |
| `generateBom`         | Create the demonstration material requirements once           |
| `createPurchaseOrder` | Require a BOM and insert one draft PO                         |
| `advanceStatus`       | Move to the next production/delivery stage                    |
| `createInvoice`       | Require delivery and insert one invoice                       |
| `recordPayment`       | Check the receipt, retry key and balance before saving        |
| `audit`               | Save who performed the action                                 |

## Concepts to explain in your own words

- **Dependency injection:** Spring/Angular provide a class's dependencies; constructors or `inject()` request them.
- **REST endpoint:** an HTTP method and URL, such as GET /api/orders, that performs an operation.
- **DTO or record:** a named shape carrying request or response data. It is not a database connection.
- **Validation:** reject bad input before changing data. `@Valid` applies rules on CreateOrderRequest.
- **Transaction:** save the related updates together, or roll them back on failure.
- **Row lock:** FOR UPDATE serializes simultaneous writes to the same order.
- **Idempotency:** repeating the same payment request returns its saved result without charging it twice.
- **CSRF token:** a session-associated token required on writes, including login and logout.
- **Migration:** a versioned SQL file that changes the database schema.

## Login and roles

`security/SecurityConfig.java` contains the route permissions. `accounts/AccountService.java`
loads accounts, hashes new passwords, and creates the first administrator on an empty user table.
`accounts/AccountController.java` exposes current-user, CSRF and team-management endpoints.
The frontend hides unavailable controls, but the backend enforces the actual restrictions.

Sales creates orders. Production handles BOM, PO and delivery stages. Accounts handles invoices
and payments. Admin can do all of these and create team accounts. All authenticated roles can read records.

## Database mode versus demo mode

Normal startup uses the backend and MySQL. `?mode=demo` uses sample records from
`app/demo/demo-orders.ts` and browser storage. Never describe demo storage as MySQL persistence.
The component's `api` flag chooses the mode. Failed database requests lead to login/offline UI,
not a silent switch to demo records.

## What remains simple on purpose

The BOM uses 2 kg steel and 4 fasteners per unit. The PO estimate is INR 250 per unit.
There is no inventory reservation, supplier integration, tax calculation or payment gateway.
The main Angular component coordinates a small single-page workspace; its sections are marked
with comments. Feature components would be a sensible next step if the screens grow further.

## Small exercises to build confidence

1. Find where the Create order button calls `create()`.
2. Follow the route used to read payment history.
3. Explain why a payment retry is checked before testing the remaining balance.
4. Find both the frontend and backend checks that restrict invoice creation.
5. Read the order-to-cash test and calculate its expected balance yourself.

Use the code and tests to verify each answer. Describe the actual scope honestly when presenting it.
