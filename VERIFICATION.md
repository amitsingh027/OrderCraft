# Verification — login, database mode and payment reliability

Verified on 20 October 2026.

## Automated checks

- Angular production build passed with the updated templates and typed API client.
- Spring Boot compilation passed (Java 17 target, installed JDK 25).
- Backend test suite: **10 tests passed; 0 failures, 0 errors, 0 skipped**.
- Tests cover the full order-to-cash workflow and invalid order inputs.
- Security tests cover anonymous rejection, missing-CSRF rejection, actual session login/logout, backend role enforcement, admin-only account creation, password hashing and duplicate usernames.
- Payment tests cover decimal amounts, same-request replay, altered payloads using an existing request ID, duplicate references, overpayments, invalid metadata, cross-order request-ID reuse and simultaneous identical payment requests. Concurrent retries produce one payment ledger entry.
- Migration tests retain existing orders and payments while adding accounts and payment metadata.

## Browser checks

The compiled Angular app was served by a running Spring Boot server using an isolated H2 test database:

- The default URL showed sign-in rather than seeded demo records.
- Admin login loaded the empty database workspace and exposed Team controls.
- A ₹100.25 payment against a ₹200.50 invoice produced a ₹100.25 balance.
- The payment history showed its receipt reference and recorder, and remained correct after reloading the page.
- Logout and login with a Sales account succeeded. Sales retained order creation and read access, but had no Team entry or payment-recording form.
- A separate disconnected-backend check showed “Workspace unavailable,” exposed a reconnect action, and showed no operational records or edit controls.
- Following the explicit demo link loaded the separate browser demo.

## Limits and deployment status

Tests use H2 in MySQL compatibility mode. Live MySQL and Docker execution remain unverified because those runtimes are not installed here. No existing user database was migrated during this work.

No hosted deployment is included. The earlier Sites publication attempt was blocked by the session approval policy. The updated application and build are delivered locally.

The included dist folder contains the compiled interface. Database mode requires Spring Boot and MySQL. A static server can run the separate demo at `?mode=demo`; it cannot provide login or persistence to MySQL. Follow README.md to configure your first administrator password and start the complete application.
