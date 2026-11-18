# CampusHub Backend

Spring Boot 4.1 (Java 21) REST API for the CampusHub frontend. Everything is under `/api`.

## Running
```bash
./mvnw spring-boot:run   # http://localhost:8080, H2 file database in ./data
./mvnw verify            # all tests (in-memory database)
```
Demo accounts are created on start-up (password `Demo1234`): `ayse.yilmaz@metu.edu.tr` (student),
`burak.celik@metu.edu.tr` (not verified yet), `zeynep.demir@metu.edu.tr` (University Admin).
No e-mail is sent: verification codes are written to the log and `123456` always works.
The frontend's `npm run dev` proxies `/api` to this server.

Deployment (EC2 + nginx, GitHub Actions): see [`deploy/README.md`](deploy/README.md).

## Code layout
Classic layers, one package each (`src/main/java/com/campushub/`):

| Package | What goes there | Examples |
|---|---|---|
| `controller` | HTTP endpoints only: read the request, call a service, return a DTO | `AuthController`, `SessionController`, `EventController`, `MeController` |
| `service` | Business rules and transactions | `RegistrationService`, `VerificationService`, `OtpService`, `SessionService`, `EventService` |
| `repository` | Spring Data JPA repositories | `UserRepository`, `EventRepository`, `RefreshTokenRepository` |
| `model` | JPA entities and enums (tables come from Flyway, `src/main/resources/db/migration`) | `User`, `Event`, `OtpCode`, `RefreshToken`, `Role`, `AccountStatus` |
| `dto` | Request and response records: the JSON the frontend sends and receives | `RegisterRequest`, `AuthSession`, `CampusEvent`, `EventDetail`, `PageResponse` |
| `exception` | Error contract: `ErrorCode`, `ApiException`, and the handler that turns every error into Problem Details | `ApiExceptionHandler`, `Problems` |
| `security` | Helpers around the signed-in user and the refresh cookie | `CurrentUser`, `RefreshCookies` |
| `validation` | Custom Bean Validation constraints | `StrongPassword` |
| `config` | Spring configuration and `campushub.*` settings | `SecurityConfig`, `JwtConfig`, `CampushubProperties` |
| `util` | Small stateless helpers | `Emails` |
| `demo` | Demo accounts and events (`campushub.demo-data.enabled`) | `DemoData`, `DemoEvents` |

Rules of thumb:
- Controllers never use repositories directly; services never build HTTP responses.
- Entities (`model`) never leave the service layer as JSON: map them to a `dto` record.
- Throw `ApiException(ErrorCode.X, "text for the user")` for expected errors; the handler does the rest.

## API contract
The frontend (`campushub-frontend`) defines the contract this API follows:
- Errors: RFC 9457 Problem Details with `code` and `fieldErrors` — frontend README "API errors";
  the codes in `exception/ErrorCode.java` must match `src/lib/errorCodes.ts`.
- Sessions: 15-minute access token (JWT) + 7-day rotating refresh token in an httpOnly cookie —
  frontend README "Authentication".
- Roles and paging — frontend README "Roles" and "Paging".

## Settings
`src/main/resources/application.properties`; on the server they come from `/opt/campushub/app.env`,
which the deploy workflow (`.github/workflows/deploy.yml`) keeps up to date. Nothing has to be set by hand:

| Variable | Default | On the server |
|---|---|---|
| `CAMPUSHUB_JWT_SECRET` | dev-only value | Access-token signing key. **Generated once on the instance** by the first deploy (`openssl rand`), kept afterwards; it never goes through GitHub or the SSM command history. |
| `CAMPUSHUB_DB_URL` | `./data/campushub` | From the GitHub variable `CAMPUSHUB_DB_URL`; default: H2 file `/opt/campushub/data/campushub`. See "Database". |
| `CAMPUSHUB_DB_USERNAME` | `sa` | From the GitHub variable `CAMPUSHUB_DB_USERNAME` (default `sa`, for H2). |
| `CAMPUSHUB_DB_PASSWORD` | empty | Read **on the instance** from the SSM parameter named in the GitHub variable `CAMPUSHUB_DB_PASSWORD_PARAMETER`; no parameter = no password (H2). |
| `CAMPUSHUB_DEMO_DATA` | `true` | From the GitHub variable `CAMPUSHUB_DEMO_DATA` (`true`/`false`, default `true`). |
| `CAMPUSHUB_COOKIE_SECURE` | `false` | From the GitHub variable `CAMPUSHUB_COOKIE_SECURE`; set `true` once the site has HTTPS. |
| `CAMPUSHUB_OTP_FIXED_CODE` | `123456` | Every verification code is this; set it in `app.env` (empty value) for random codes. |

To rotate the JWT secret (logs everyone out): delete its line from `app.env` and deploy again.

## Database
Default: H2 in a file on the instance. The schema comes from Flyway migrations written in PostgreSQL-compatible
SQL, and the PostgreSQL driver is already included, so switching to **AWS RDS PostgreSQL** is configuration only:

1. **RDS:** create a PostgreSQL instance in the same region/VPC, database name `campushub`. In its security group
   allow port 5432 **only from the EC2 instance's security group** (no public access).
2. **Password:** Systems Manager → Parameter Store → create `/campushub/db-password`, type **SecureString**.
   The EC2 instance role needs `ssm:GetParameter` on that parameter (and `kms:Decrypt` if you use your own KMS key):
   ```json
   { "Effect": "Allow", "Action": "ssm:GetParameter",
     "Resource": "arn:aws:ssm:<REGION>:<ACCOUNT_ID>:parameter/campushub/db-password" }
   ```
   The password is read by the instance during the deploy, so it never appears in GitHub or in the SSM command history.
3. **GitHub variables** (backend repo, Settings → Secrets and variables → Actions → Variables):
   | Variable | Example |
   |---|---|
   | `CAMPUSHUB_DB_URL` | `jdbc:postgresql://<endpoint>.rds.amazonaws.com:5432/campushub?sslmode=require` |
   | `CAMPUSHUB_DB_USERNAME` | `campushub` |
   | `CAMPUSHUB_DB_PASSWORD_PARAMETER` | `/campushub/db-password` |
4. **Deploy** (push to `main` or run the workflow by hand). Flyway creates the tables on the empty database;
   with demo data on, the demo accounts and events are created too.

Data in the old H2 file is **not** copied over; the file stays in `/opt/campushub/data`. Removing the three
variables switches back to it.
