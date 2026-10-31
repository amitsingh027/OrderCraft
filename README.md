# OrderCraft — Amit Kumar Singh

Manufacturing order management built with Angular, Java 17 / Spring Boot and MySQL. Includes orders, a quantity-based BOM, draft purchase orders, production and delivery stages, invoicing, partial payments, customer search and CSV export.

## What changed

- Database mode is now the default. The app starts at sign-in, and connection failures clear operational data and block changes. Live records and credentials are never saved to browser localStorage.
- Demo mode is explicitly selected with `?mode=demo`. It has separate browser storage and does not call or authenticate to the backend.
- Spring Security provides session login, logout, CSRF protection, password hashing and backend-enforced roles. Administrators create team accounts through the Team screen.
- Every new payment requires a receipt/reference and a UUID request ID. Retrying the same request returns the existing result; changed payloads with a reused ID and duplicate references for the same order are rejected. Payments and order activity record the acting username.
- Financial amounts display two decimals. Payment history shows amount, reference, recorder and timestamp.
- Flyway versioned migrations replace startup schema creation. Existing data can be upgraded without deleting orders or payments.

## Start with Docker

1. Install Docker with Compose.
2. Copy `.env.example` to `.env`. Choose an `ADMIN_PASSWORD` with at least 12 characters and at most 72 UTF-8 bytes. Set `DB_PASSWORD` and `ROOT_PASSWORD` too.
3. Run `docker compose up --build` from this directory.
4. Open **http://localhost:8080/** and sign in with `ADMIN_USERNAME` (default `admin`) and your chosen password.
5. Open **Team** to create accounts for other roles.

The initial admin account is created only when the users table is empty. Changing ADMIN_PASSWORD later does not overwrite existing accounts. There are no built-in shared login credentials. Docker binds the app to localhost; the database is only exposed to the app container. Database records persist in the named volume.

For an HTTPS deployment set `SESSION_COOKIE_SECURE=true`. Sessions expire after 30 minutes of inactivity. Session cookies are HttpOnly and SameSite=Strict. Password recovery, account disabling and role editing are not yet included.

## Role permissions

| Role | Allowed changes |
| --- | --- |
| Admin | All operations and team account creation |
| Sales | Create customer orders |
| Production | Generate BOMs and purchase orders; advance production/delivery |
| Accounts | Generate invoices and record payments |

All authenticated roles can read orders, material documents, invoices, payment history, customers and activity. The API enforces these rules independently of the visible buttons.

## Upgrade an existing original OrderCraft database

Back up the database first. For an original database created before Flyway was introduced, set `BASELINE_EXISTING_DATABASE=true` for the first upgraded startup. This records the original schema as version 1 and applies version 2. Return the setting to false after successful migration. Fresh databases should leave this false.

Existing payments remain intact; their newly added reference, request ID and recorder fields are null and are displayed as legacy entries. The first upgraded startup also needs ADMIN_PASSWORD if no user accounts exist. Do not delete the MySQL volume to perform this upgrade.

## Local development in VS Code

Backend: configure DB_URL, DB_USER, DB_PASSWORD and ADMIN_PASSWORD for your MySQL database, then run `mvn spring-boot:run` in `backend`. Java 17+ and Maven are required.

Frontend: run `npm ci --install-strategy=shallow`, then `npm start` in `frontend`. The Angular development proxy forwards `/api` to `http://127.0.0.1:8080`. Open the printed local URL and sign in. Add `?mode=demo` only when you deliberately want the separate browser demo.

Build the frontend with `npm run build`; test the backend with `mvn test`. Docker packages the Angular build and serves it from Spring Boot. The included `dist` is the compiled interface; a static server alone can run demo mode but cannot provide login or database functionality.

## API

- `GET /api/auth/csrf` returns a token and header name. Include that header on all state-changing requests, including login/logout.
- `POST /api/auth/login` accepts URL-encoded username and password; retain the session cookie. Fetch a fresh CSRF token after successful login.
- `GET /api/auth/me` returns the signed-in username and role. `POST /api/auth/logout` invalidates the session.
- `GET/POST /api/admin/users` lists/creates accounts (Admin only). Creation body: username, password, role.
- `GET /api/orders`; `POST /api/orders` with customer, product, qty, price and due (ISO date).
- `GET /api/orders/{id}`; `POST /api/orders/{id}/{action}`, where action is bom, po, advance, invoice or payment.
- Payment body: `{"amount":100.25,"reference":"BANK-2026-001"}` with `Idempotency-Key: <UUID>`. Keep the same key and body when retrying an uncertain payment result. Other action bodies may be `{}`.
- `GET /api/orders/{id}/payments` returns the payment ledger. `GET /api/orders/{id}/documents` returns all documents. `GET /api/activity` returns the latest 100 order events.

References are normalized to uppercase. Use 3–100 characters: letters, digits, dots, underscores, slashes or hyphens. A reference is unique within its order. A request ID is unique across the payment ledger. The backend serializes changes with an order-row lock and uses decimal monetary arithmetic. A different reference represents a different receipt; check the payment history before recording a second receipt.

## Remaining scope

The material recipe is still a demonstration: 2 kg steel and 4 fasteners per unit, with a draft procurement estimate of ₹250 per ordered unit. Product-specific recipes, inventory allocation, goods receipts, tax configuration, PDF invoices, partial deliveries and order editing/cancellation remain future work. A purchase order is an internal draft; no supplier messages or transfers of funds are sent.

See `VERIFICATION.md` for the checks performed and their limitations.
