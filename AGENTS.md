# AGENTS.md

## Run Commands

```bash
# Start fullstack stack locally (MySQL + backend + frontend)
docker compose --env-file backend/.env up --build

# Start only MySQL for local backend/frontend dev
docker compose --env-file backend/.env up -d db

# Run backend locally
cd backend
./mvnw spring-boot:run

# Run backend tests
cd backend
./mvnw test

# Run frontend dev server, tests, typecheck and build
cd frontend
npm install
npm run dev
npm test
npm run check
npm run build
npm run test:e2e   # Playwright; sobe backend e frontend sozinho (ver docs/TESTES.md)
```

## Architecture

- **Backend**: Spring Boot 3.5.4, Java 17, Maven
- **Frontend**: React 19, TypeScript 5.6, Vite 7, Tailwind CSS v4, shadcn/ui, wouter
- **Database**: MySQL 8.0 on port 3307 locally (`db:3306` inside Docker network)
- **Migrations**: Flyway in `backend/src/main/resources/db/migration/` (`ddl-auto=validate`)
- **Auth**: JWT via `/auth/login` (public); all other endpoints require Bearer token + RBAC (`@PreAuthorize`)
- **Soft deletes**: Clinical/operational entities use `ativo` field, not physical deletes
- **Pagination**: 10 records per page by default (20 in specialties and audit endpoints; 100 in available doctors selector)
- **API Docs**: Swagger UI at `/swagger-ui.html` (enabled in local dev, disabled in production VPS)
- **Production & CI/CD**: Frontend on Vercel (`vollmed.usenexora.online`); APIs + MySQL + Caddy HTTPS reverse proxy on VPS (`docker-compose.prod.yml`, `deploy/Caddyfile`, `.github/workflows/deploy.yml`, see `docs/DEPLOY_VPS.md`)

## Entry Points

- `ApiApplication.java` - main class
- Controllers (14): `AutenticacaoController`, `MedicoController`, `MedicoConvenioController`, `PacientesController`, `ConsultaController`, `DisponibilidadeMedicoController`, `ProntuarioController`, `PrescricaoController`, `AtestadoController`, `ConvenioController`, `ConvenioPacienteController`, `AuditoriaController`, `EspecialidadeController`, `IaController`, `EnderecoController`

## Migrations applied

V1–V25 applied (next: V26)

## Tests

206 tests passing no backend. Run from `backend/`: `./mvnw test`
29 tests passing no frontend. Run from `frontend/`: `npm test`
7 E2E tests (Playwright, `frontend/e2e/`): `npm run test:e2e` from `frontend/`.
CI workflow (`.github/workflows/deploy.yml`) runs backend tests, frontend tests, `npm run check`, `npm run build` (job `test`) and the E2E suite against MySQL 8.0 (job `e2e`) on every push/PR to `main`; deploy requires both.

- Unit/context/security/client: `ApiApplicationTests` (1), `ExclusaoLogicaPersistenciaIntegrationTest` (2), `AgendaDeConsultasTest` (17), `ProntuarioServiceTest` (7), `EspecialidadeServiceTest` (8), `UsuarioServiceTest` (11), `IaServiceTest` (6), `AuditoriaProntuarioAspectTest` (2), `SecurityFillterTest` (3), `ConsultaCepServiceTest` (10), `RestClientCepClientTest` (7)
- Controller (`@WebMvcTest`): `ConsultaControllerTest` (7), `MedicoControllerTest` (8), `PacientesControllerTest` (10), `ProntuarioControllerTest` (21), `PrescricaoControllerTest` (10), `AtestadoControllerTest` (10), `EspecialidadeControllerTest` (10), `AutenticacaoControllerTest` (16), `ConvenioControllerTest` (10), `ConvenioPacienteControllerTest` (6), `MedicoConvenioControllerTest` (6), `DisponibilidadeMedicoControllerTest` (6), `AuditoriaControllerTest` (4), `EnderecoControllerTest` (8)
- Frontend (Vitest, `frontend/`: `npm test`): 29 testes cobrindo `Users.test.tsx` (6), `FuncionarioDashboard.test.tsx` (6), `axios.test.ts` (5), `Doctors.cep.test.tsx` (3), `AuthContext.test.tsx` (3), `Patients.cep.test.tsx` (2), `Login.test.tsx` (2), `cep.test.ts` (2)
- See `docs/TESTES.md` for full strategy

## Gotchas

- `backend/.env` file must exist for local backend configuration and local Docker Compose (`DB_PASSWORD=root`)
- `backend/.env.example` contains development-only defaults; copy it to `backend/.env` and replace secrets outside local dev
- Production VPS uses `.env.production.example` → `/opt/vollmed/app/.env` with `docker-compose.prod.yml` and Caddy stripping `/api` prefix via `handle_path /api/*`
- Fullstack local Docker Compose publishes frontend on `${FRONTEND_PORT:-3000}` and backend on `${BACKEND_PORT:-8080}`
- `JWT_SECRET` must be configured (at least 32 characters)
- `CEP_API_BASE_URL` (default: `http://localhost:8081` locally; `http://consultacep:8080` in prod Compose) e `CEP_API_KEY` configuram o consumo backend-to-backend da API `Consultar-Cep`
- Endpoint `GET /enderecos/cep/{cep}` requer `ROLE_FUNCIONARIO` ou `ROLE_ADMIN`
- Test database uses H2 in-memory with `spring.flyway.enabled=false` and `ddl-auto=create-drop`
- `SecurityFillter` and `RateLimitFilter` have `FilterRegistrationBean` disabling auto-registration — never remove them (Spring Security 6.5+ requirement)
- `ANTHROPIC_API_KEY` required for `/ia/*` endpoints — app starts without it but calls fail at runtime
- IA endpoints are `ROLE_MEDICO` only — do not change to broader roles
- `RATE_LIMIT_MAX_REQUESTS` (default `10`) controls the `/auth/*` rate limit; the E2E suite raises it to `200`. Keep the default in production
