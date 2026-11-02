# Run and check the project

## Docker workflow

Start Docker Desktop and open a terminal at the project root:

```powershell
docker compose up -d --build
docker compose ps
```

Open http://localhost:8080 and use your existing account. The code organization does not reset users,
orders or payments. Keep the existing .env and database volume. Do not remove the volume to rebuild code.

```powershell
docker compose logs --tail=80 app db
docker compose stop
```

## Development commands

Backend, with Java 17+ and Maven available:

```powershell
cd backend
mvn test
```

To run the backend directly, configure DB_URL, DB_USER, DB_PASSWORD and ADMIN_PASSWORD and use
`mvn spring-boot:run`. Compose reads .env; a direct Maven run does not automatically read that file.

Frontend, from a separate terminal:

```powershell
cd frontend
npm ci --install-strategy=shallow
npm start
```

The development proxy sends /api requests to the backend on port 8080.
Use `npm run build` to compile the production interface. Docker installs its own build dependencies.

## Manual acceptance checklist

Use test data in a development database for actions that write records.

- Sign in and confirm that orders load.
- Create an order and check quantity times unit price.
- Generate its BOM, then its PO.
- Advance all stages to Delivered, then generate the invoice.
- Record a partial payment with a receipt reference and check the balance.
- Refresh and confirm the receipt remains in payment history.
- Confirm duplicate references and overpayments are rejected.
- Check Sales, Production and Accounts restrictions using separate accounts.

## Refactor checks

The existing ten backend tests cover workflow, login/roles, CSRF, payment retries, simultaneous
requests and migration preservation. These use H2 in MySQL compatibility mode, not a live MySQL server.
The Angular production build checks component imports and strict template types.
