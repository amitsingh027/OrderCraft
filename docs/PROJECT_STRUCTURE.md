# Project structure

```text
OrderCraft/
  backend/
    pom.xml                         Maven dependencies and Java target
    src/main/java/com/ordercraft/
      OrderCraftApplication.java    Spring Boot entry point
      accounts/                     Login account loading and team management
      common/ApiErrors.java         Consistent API error messages
      security/SecurityConfig.java  Route permissions and session security
      orders/
        OrderController.java        HTTP routes only
        OrderService.java           Workflow rules, transactions and SQL
        model/                      Request and response records
    src/main/resources/
      application.properties        Database and session configuration
      db/migration/                 Applied Flyway migrations
      schema.sql                    Original schema reference, not startup initialization
    src/test/                       Workflow, security, payment and migration tests
  frontend/
    angular.json                    Build configuration and stylesheet paths
    package.json                    Frontend dependencies and commands
    package-lock.json               Reproducible dependency versions
    src/
      main.ts                       Angular entry point
      index.html                    Browser document containing app-root
      app/
        app.component.ts            Screen state and user actions
        app.component.html          Workspace views and forms
        core/api-client.service.ts  Fetch, session cookies, errors and CSRF
        models/order.models.ts      Shared TypeScript data shapes
        demo/demo-orders.ts         Sample data for explicit demo mode
      styles/
        styles.css                  Workspace layout and visual styles
        access.css                  Login, team and payment-history styles
  docs/                             Reading guide and manual checks
  Dockerfile                        Build Angular, package Java, run application
  compose.yaml                      Local application and MySQL services
  .env.example                      Configuration template without passwords
  .env                              Your private local values, ignored by Git
  .editorconfig                     Editor whitespace conventions
  .prettierrc.json                   Code formatting preferences
```

## Generated files

`node_modules`, `.angular`, `frontend/dist` and `backend/target` are generated output.
Do not edit or commit them. A top-level `dist` directory may remain from the earlier static preview;
the Docker build uses a fresh frontend build instead. Logs are local diagnostics, not source code.

## Adding a change

For a new backend rule, start in OrderService and add a meaningful behavioral test. Change the
controller only if the HTTP contract changes. Update TypeScript models if the response shape changes.
For a visual change, start in the HTML template and styles. Change component methods only when behavior changes.

Do not edit an already applied Flyway migration just to reformat it. Create a new migration for schema changes.
The current refactor preserves migration bytes and all existing API URLs.
