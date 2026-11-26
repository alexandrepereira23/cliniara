# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) and AI coding assistants when working with code in this repository.

## Commands

```bash
# Subir stack fullstack local (MySQL + backend + frontend) — rodar da raiz do projeto
docker compose --env-file backend/.env up --build

# Subir apenas o banco para desenvolvimento local
docker compose --env-file backend/.env up -d db

# Executar o backend localmente — rodar de dentro de backend/
cd backend && ./mvnw spring-boot:run

# Build completo do backend (usar após mudanças em @Value ou configuração)
cd backend && ./mvnw clean package

# Rodar todos os testes do backend (231 testes)
cd backend && ./mvnw test

# Rodar uma classe de teste específica no backend
cd backend && ./mvnw test -Dtest=NomeDaClasseTest
```

## Variáveis de ambiente (.env)

Em desenvolvimento local, a aplicação lê `.env` via `spring.config.import=optional:file:.env[.properties]`. Como os comandos do backend são executados dentro de `backend/`, o arquivo esperado é `backend/.env` (copiado de `backend/.env.example`). Para produção na VPS, o modelo é `.env.production.example` na raiz.

O padrão do projeto é: variável no `.env` → mapeada em `application.properties` → lida via `@Value("${propriedade.spring}")`.

| Variável no .env | Propriedade Spring | Descrição |
|------------------|--------------------|-----------|
| `DB_HOST` | *(interpolada na URL JDBC)* | Host do MySQL (padrão: `localhost`; no Compose: `db`) |
| `DB_PORT` | *(interpolada na URL JDBC)* | Porta do MySQL (padrão: `3307` no host; `3306` na rede Docker) |
| `DB_NAME` | *(interpolada na URL JDBC)* | Nome do banco (padrão: `vollmed_api`) |
| `DB_USER` | `spring.datasource.username` | Usuário do MySQL (padrão: `root`) |
| `DB_PASSWORD` | `spring.datasource.password` | Senha do MySQL (padrão local: `root`) |
| `BACKEND_PORT` / `FRONTEND_PORT` | *(usadas pelo `docker-compose.yml`)* | Portas publicadas no host local (`8080` e `3000`) |
| `JWT_SECRET` | `api.security.token.secret` | Chave JWT (mín. 32 chars) — `openssl rand -base64 32` |
| `TOKEN_EXPIRACAO_HORAS` | `api.security.token.expiracao-horas` | Expiração do JWT (padrão: `2`) |
| `ADMIN_LOGIN` | `admin.login` | Login do admin inicial (padrão: `admin@vollmed.com`) |
| `ADMIN_PASSWORD` | `admin.password` | Senha do admin inicial — **obrigatório para criar o admin** |
| `ANTHROPIC_API_KEY` | `anthropic.api.key` | Chave da Anthropic; app inicia sem ela, mas chamadas `/ia/*` falham em runtime |
| `CEP_API_BASE_URL` | `cep.api.base-url` | URL base do serviço `Consultar-Cep` (padrão local: `http://localhost:8081`; VPS: `http://consultacep:8080`) |
| `CEP_API_KEY` | `cep.api.key` | Chave enviada no header `X-API-Key` apenas nas chamadas backend-to-backend |
| `RATE_LIMIT_MAX_REQUESTS` | `api.security.rate-limit.max-tentativas` | Limite de requisições por IP em 15 min em `/auth/*` (padrão: `10`; a suíte E2E usa `200`) |

> **Importante:** Nunca usar `@Value("${NOME_VARIAVEL_ENV}")` diretamente no código Java. Sempre mapear em `application.properties` primeiro e usar `@Value("${propriedade.spring}")`.

## Arquitetura

### Camadas do Backend

- **`controller/`** — 14 controllers REST (`AutenticacaoController`, `MedicoController`, `MedicoConvenioController`, `PacientesController`, `ConsultaController`, `DisponibilidadeMedicoController`, `ProntuarioController`, `PrescricaoController`, `AtestadoController`, `ConvenioController`, `ConvenioPacienteController`, `AuditoriaController`, `EspecialidadeController`, `IaController`, `EnderecoController`)
- **`domain/<entidade>/`** — entidades JPA, repositories, enums, DTOs (records) e serviços de domínio (`AgendaDeConsultas`, `AutenticacaoService`)
- **`service/`** — serviços de aplicação (`UsuarioService`, `MedicoService`, `PacienteService`, `ConsultaService`, `EspecialidadeService`, `ConvenioService`, `ConvenioPacienteService`, `ProntuarioService`, `PrescricaoService`, `AtestadoService`, `AuditoriaProntuarioService`, `IaService`, `ConsultaCepService`)
- **`infra/security/`** — filtro JWT (`SecurityFillter`), rate limiting (`RateLimitFilter`), configuração Spring Security e CORS (`SecurityConfigurations`), geração/validação JWT (`TokenService`), criação idempotente do admin inicial (`AdminInitializer`)
- **`infra/aop/`** — auditoria LGPD automática via AspectJ (`AuditoriaProntuarioAspect`) sobre `ProntuarioService`, `PrescricaoService` e `AtestadoService`
- **`infra/cep/`** — cliente HTTP `CepClient` / `RestClientCepClient` (ACL para o serviço externo `Consultar-Cep`)
- **`infra/exception/`** — handler global de erros (`TratadorDeErros`)
- **`Exception/`** — exceções customizadas de domínio e integração (`ValidacaoException` → 400, `ConflitoException` → 409, `CepInvalidoException` → 400, `CepNaoEncontradoException` → 404, `RespostaInvalidaCepException` → 502, `ServicoCepIndisponivelException` → 503)

### Fluxo de autenticação

1. `POST /auth/login` → `AutenticacaoController` → Spring Security autentica → `TokenService` gera JWT
2. Cada requisição passa por `RateLimitFilter` → `SecurityFillter` (valida JWT e injeta `Usuario` no `SecurityContext`)
3. `RateLimitFilter` atua apenas em `/auth/*` — máx. 10 req/IP em 15 min → HTTP 429

### Registro de filtros customizados (Spring Security 6.5+)

`SecurityFillter` e `RateLimitFilter` são `@Component`, mas **não devem ser registrados automaticamente como Servlet filters** pelo Spring Boot — apenas a security chain deve gerenciá-los. Por isso, `SecurityConfigurations` desabilita o auto-registro:

```java
@Bean
public FilterRegistrationBean<SecurityFillter> securityFillterRegistration(SecurityFillter f) {
    var reg = new FilterRegistrationBean<>(f);
    reg.setEnabled(false);
    return reg;
}
```

**Nunca remover esses `FilterRegistrationBean`** — sem eles, o Spring Security 6.5+ lança `IllegalArgumentException: does not have a registered order`.

### Perfis de usuário

- `ROLE_ADMIN` — administração técnica e usuários via `POST /auth/cadastro`, `GET /auth/usuarios`, `GET /auth/medicos-disponiveis` e `GET /enderecos/cep/{cep}`; não acessa dados clínicos nem cadastros operacionais por padrão, inclusive `GET /medicos`
- `ROLE_FUNCIONARIO` — operação da clínica, cadastros, agenda, convênios, consulta de CEP e leitura operacional de prescrições (`GET /prescricoes/paciente/{pacienteId}`) e atestados; sem acesso a prontuários (`/prontuarios/**` retorna `403`)
- `ROLE_MEDICO` — atendimento clínico; vê apenas dados assistenciais vinculados ao médico logado e acessa IA clínica (`/ia/*`); se o médico for inativado, login retorna `403` e JWTs ativos retornam `401` imediatamente
- `ROLE_AUDITOR` / `ROLE_GESTOR` — leitura ampla, inativação administrativa de prontuário e auditoria LGPD (`/auditoria/**`), sempre com rastreabilidade

### Admin inicial (AdminInitializer)

Ao iniciar, `AdminInitializer` (implementa `ApplicationRunner`) verifica se existe algum `ROLE_ADMIN` no banco. Se não existir e `ADMIN_PASSWORD` estiver configurado, cria o admin automaticamente. O processo é idempotente.

### Banco de dados

- `ddl-auto=validate` — Hibernate valida o schema mas não o altera; toda mudança estrutural exige migration Flyway
- Migrations em `backend/src/main/resources/db/migration/` com prefixo `V{n}__descricao.sql`
- Última migration aplicada: `V25` — próxima será `V26`
- Exclusão lógica via campo `ativo` em médicos, pacientes, consultas, prontuários, especialidades, convênios, vínculos de convênio e disponibilidades

### Regras de negócio de consultas (AgendaDeConsultas)

- Clínica funciona Seg–Sáb, 07h–19h
- Antecedência mínima de agendamento conforme prioridade: `ROTINA` (30 min, padrão), `PRIORITARIO` (10 min), `URGENCIA` (sem restrição); cancelamento exige mínimo de 24h
- Paciente não pode ter duas consultas normais no mesmo dia (retorno via `consultaOrigemId` ignora a cota diária; exige mesmo paciente, mesmo médico — auto-atribuído se omitido —, data estritamente posterior até 30 dias e no máximo 1 retorno ativo por consulta)
- Médico pode ser omitido (em consultas normais) — sistema escolhe aleatoriamente um médico ativo com disponibilidade real cadastrada e livre no horário; disponibilidade exige `horaInicio < horaFim` e sem sobreposição ativa no mesmo `diaSemana`
- Quando `convenioId` é informado, valida existência/atividade do convênio, aceite pelo médico e, para `TipoConvenio.PLANO`, vínculo ativo e validade da carteirinha do paciente (`TipoConvenio.PARTICULAR` não exige carteirinha)

## Frontend

Monorepo — frontend SPA em `frontend/`.

### Stack

- React 19 + TypeScript 5.6 + Vite 7
- TailwindCSS v4 (via `@tailwindcss/vite`, tokens em `src/index.css`)
- **shadcn/ui** — componentes sobre Radix UI (`components/ui/`)
- **wouter** — roteamento client-side leve
- Axios (client HTTP com interceptor JWT em `src/api/axios.ts` e base default `/api`)
- React Hook Form + Zod v4, framer-motion, recharts, sonner, Lucide React
- Vitest + Testing Library (39 testes automatizados)

### Comandos

```bash
cd frontend

# Instalar dependências
npm install

# Dev server (http://localhost:5173 — proxy /api -> http://localhost:8080)
npm run dev

# Executar testes automatizados (39 testes)
npm test

# Testes E2E com Playwright (9 testes; sobe backend e frontend sozinho, ver docs/TESTES.md)
npm run test:e2e

# Type check
npm run check

# Build de produção
npm run build

# Auditoria de vulnerabilidades
npm audit
```

### Estrutura

```text
frontend/src/
├── api/               # axios.ts, auth.ts, cep.ts, medicos.ts, pacientes.ts, consultas.ts,
│                      # prontuarios.ts, prescricoes.ts, atestados.ts, especialidades.ts,
│                      # convenios.ts, medicoConvenios.ts, convenioPaciente.ts, disponibilidade.ts,
│                      # auditoria.ts, ia.ts (+ testes unitários axios.test.ts, cep.test.ts)
├── contexts/          # AuthContext.tsx (+ AuthContext.test.tsx), ThemeContext.tsx
├── hooks/             # useAuth.ts, useMobile.tsx, useComposition.ts, usePersistFn.ts
├── lib/               # authSession.ts, rbac.ts, utils.ts
├── components/
│   ├── dashboard/     # FuncionarioDashboard.tsx (+ test), MedicoDashboard.tsx
│   ├── ui/            # shadcn/ui + Pagination, Spinner
│   └── DashboardLayout.tsx, PageHeader.tsx, Badge.tsx, EmptyState.tsx, SearchInput.tsx, ErrorBoundary.tsx
├── pages/             # Login, Dashboard, Users, Doctors, Patients, Appointments,
│                      # MedicalRecords, Prescriptions, Certificates, Specialties,
│                      # Insurance, Availability, Audit, ClinicalAI, NotFound
│                      # (+ testes Login, Users, Doctors.cep, Patients.cep, MedicalRecords, Appointments, Audit)
├── types/             # auth.ts, api.ts, auditoria.ts
└── App.tsx            # router wouter + guardas por perfil (AdminRoute, DashboardRoute, NonAdminRoute, ProntuarioRoute, AuditorRoute, MedicoRoute)
```

### Rotas

| Path | Página | Guarda no Frontend |
|------|--------|--------------------|
| `/login` | `Login` | Pública |
| `/` | `Dashboard` | `DashboardRoute` (`ROLE_ADMIN` → `/users`; `AUDITOR`/`GESTOR` → `/audit`) |
| `/users` | `Users` | `AdminRoute` (apenas `ROLE_ADMIN`) |
| `/doctors` | `Doctors` | `NonAdminRoute` |
| `/patients` | `Patients` | `NonAdminRoute` |
| `/appointments` | `Appointments` | `NonAdminRoute` |
| `/medical-records` | `MedicalRecords` | `ProntuarioRoute` (`ROLE_MEDICO`, `ROLE_AUDITOR`, `ROLE_GESTOR`) |
| `/prescriptions` | `Prescriptions` | `NonAdminRoute` |
| `/certificates` | `Certificates` | `NonAdminRoute` |
| `/specialties` | `Specialties` | `NonAdminRoute` |
| `/insurance` | `Insurance` | `NonAdminRoute` |
| `/availability` | `Availability` | `NonAdminRoute` |
| `/audit` | `Audit` | `AuditorRoute` (`ROLE_AUDITOR` / `ROLE_GESTOR`) |
| `/clinical-ai` | `ClinicalAI` | `MedicoRoute` (`ROLE_MEDICO`) |
| `/404` / fallback | `NotFound` | Pública |

### Estado atual das páginas

Todas as páginas das rotas ativas estão conectadas à API real, e os artefatos legados de template sem uso (`mockData.ts`, `Home.tsx`, `ManusDialog.tsx`, `Map.tsx`) foram removidos.

## Deploy, Infraestrutura VPS e CORS

- **Frontend (Produção):** publicado na Vercel (`https://vollmed.usenexora.online`), com `VITE_API_BASE_URL=https://api-vollmed.usenexora.online/api`.
- **Backend + CEP + MySQL (Produção na VPS):** orquestrados via `docker-compose.prod.yml` e `deploy/Caddyfile` (Caddy termina HTTPS e faz `handle_path /api/*` para `backend:8080`). Swagger fica desabilitado em produção (`SPRINGDOC_SWAGGER_UI_ENABLED=false`). Ver detalhes em `docs/DEPLOY_VPS.md`.
- **CI/CD:** `.github/workflows/deploy.yml` roda testes backend (`./mvnw test`), testes frontend (`npm test`), typecheck (`npm run check`) e build (`npm run build`), realizando deploy via SSH na VPS em pushes na branch `main`.
- **CORS:** `SecurityConfigurations` libera `localhost` (`3000`, `5173`, `5174`), `https://vollmed.usenexora.online` e domínios Vercel do projeto (`https://voll-*.vercel.app`).

## Referências Documentais

- `docs/ENDPOINTS.md` — referência completa de rotas, payloads e códigos HTTP
- `docs/REGRAS_DE_NEGOCIO.md` — regras clínicas/operacionais e matriz RBAC
- `docs/DECISOES_TECNICAS.md` — decisões arquiteturais, gotchas e pendências técnicas conhecidas
- `docs/TESTES.md` — estratégia e cobertura das suítes backend/frontend
- `docs/DEPLOY_VPS.md` — guia operacional de deploy em VPS com Caddy e GitHub Actions
- `docs/PLANEJAMENTO.md` e `docs/BACKLOG.md` — fases concluídas e pendências priorizadas
- `docs/ANALISE_PROJETO.md` e `docs/ANALISE_EVOLUCAO_CLINICA.md` — diagnóstico técnico e roadmap de evolução clínica
- `frontend/docs/ARCHITECTURE.md`, `frontend/docs/API_CONTRATOS.md` e `frontend/docs/DESIGN_SYSTEM.md` — documentação dedicada do frontend
