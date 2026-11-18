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
| `CAMPUSHUB_DB_URL` | `./data/campushub` | Set by the deploy to `/opt/campushub/data/campushub` (kept across deploys). |
| `CAMPUSHUB_DEMO_DATA` | `true` | From the GitHub variable `CAMPUSHUB_DEMO_DATA` (`true`/`false`, default `true`). |
| `CAMPUSHUB_COOKIE_SECURE` | `false` | From the GitHub variable `CAMPUSHUB_COOKIE_SECURE`; set `true` once the site has HTTPS. |
| `CAMPUSHUB_OTP_FIXED_CODE` | `123456` | Every verification code is this; set it in `app.env` (empty value) for random codes. |

To rotate the JWT secret (logs everyone out): delete its line from `app.env` and deploy again.
