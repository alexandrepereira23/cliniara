# Testes Automatizados — Voll.med

Este documento registra a estrategia de testes, comandos de validacao e pendencias de cobertura do projeto.

## Estado Atual

Validado nesta revisao documental:

- Backend: **231 testes passando** com `./mvnw test` em `backend/`.
- Frontend: **30 testes passando** com `npm test` em `frontend/`.
- Frontend audit: **0 vulnerabilidades** reportadas em `npm audit` (corrigido com seguranca via `npm audit fix`).
- E2E (Playwright + Chromium): **7 testes passando** com `npm run test:e2e` em `frontend/` (validado localmente no modo H2; o modo MySQL + Flyway roda no CI).
- CI/CD (`.github/workflows/deploy.yml`): o job `test` executa `./mvnw --batch-mode test`, `npm test`, `npm run check` e `npm run build`; o job `e2e` (MySQL 8.0 real + Flyway `V1`–`V25`) executa `npm run test:e2e`. O `deploy` na VPS depende dos dois (`needs: [test, e2e]`).

## Comandos

### Backend

Linux/macOS/Git Bash:

```bash
cd backend
./mvnw test
```

Windows PowerShell:

```powershell
Set-Location backend
.\mvnw test
```

Classe especifica:

```bash
cd backend
./mvnw test -Dtest=AgendaDeConsultasTest
```

Multiplas classes:

```bash
cd backend
./mvnw test -Dtest="ConsultaControllerTest,AgendaDeConsultasTest"
```

### Frontend

Linux/macOS/Git Bash:

```bash
cd frontend
npm test
npm run check
npm run build
npm audit
```

Windows PowerShell:

```powershell
Set-Location frontend
npm test
npm run check
npm run build
npm audit
```

`npm test` executa Vitest + Testing Library (ignora `e2e/**`). `npm run check` executa `tsc --noEmit`. `npm run build` executa typecheck incremental (`tsc -b`) e build Vite.

### E2E (Playwright)

```bash
cd frontend
npm install
npx playwright install chromium   # apenas na primeira vez
npm run test:e2e                  # headless
npm run test:e2e:headed           # navegador visivel
npm run test:e2e:ui               # modo interativo
```

`playwright.config.ts` sobe backend (`:8080`) e frontend (`:5173`) automaticamente e reaproveita servidores ja ativos fora do CI. O banco e escolhido assim:

- MySQL acessivel em `DB_HOST:DB_PORT` (padrao `127.0.0.1:3307`, por exemplo via `docker compose --env-file backend/.env up -d db`): `spring-boot:run` com Flyway real.
- Sem MySQL: `spring-boot:test-run` com H2 em memoria (`E2eApplication`). Para forcar um modo, use `E2E_DB_MODE=mysql` ou `E2E_DB_MODE=h2`.

Variaveis uteis: `E2E_ADMIN_LOGIN`/`E2E_ADMIN_PASSWORD` (padrao `admin@vollmed.com`/`admin123`), `CEP_STUB_PORT` (padrao `18081`) e `RATE_LIMIT_MAX_REQUESTS` (padrao `200` no E2E; `10` em producao).

## Estrategia de Testes Backend

### Testes Unitarios e de Service

Usam JUnit 5 e Mockito para validar regras de negocio sem subir o contexto web completo.

| Classe | Testes | Cobertura principal |
|---|---:|---|
| `ApiApplicationTests` | 1 | Inicializacao do contexto Spring Boot com H2 |
| `AgendaDeConsultasTest` | 27 | Agendamento, cancelamento, prioridade, retorno (mesmo paciente/medico, data/30d, reemissao), disponibilidade e elegibilidade de convenio (`PLANO`/`PARTICULAR`) |
| `ProntuarioServiceTest` | 7 | Criacao, ownership, janela de edicao e erros de prontuario |
| `EspecialidadeServiceTest` | 8 | CRUD, duplicidade e inativacao |
| `UsuarioServiceTest` | 11 | Cadastro de usuarios, vinculo medico e conflitos |
| `IaServiceTest` | 6 | Chamadas mockadas para IA e regras de contexto medico |
| `AuditoriaProntuarioAspectTest` | 2 | Auditoria AOP para recursos clinicos |
| `SecurityFillterTest` | 4 | Token JWT, bloqueio imediato de medico inativo (`401`) e preenchimento do `SecurityContext` |
| `ConsultaCepServiceTest` | 10 | Validacao, normalizacao e erros de CEP |
| `RestClientCepClientTest` | 7 | Integracao backend-to-backend com API de CEP |

### Testes de Controller (`@WebMvcTest`)

Validam rotas, serializacao, status HTTP e autorizacao por `@PreAuthorize`.

| Classe | Testes | Cobertura principal |
|---|---:|---|
| `AtestadoControllerTest` | 10 | Emissao/listagem/detalhamento e permissoes |
| `AuditoriaControllerTest` | 4 | Auditoria LGPD restrita a auditor/gestor |
| `AutenticacaoControllerTest` | 17 | Login (incluindo `403` para conta inativa), cadastro/listagem de usuarios, medicos disponiveis e verificacao reflexiva de `@ParameterObject` |
| `ConsultaControllerTest` | 7 | Agendar, listar e cancelar consultas por role |
| `ConvenioControllerTest` | 10 | CRUD de convenios e RBAC |
| `ConvenioPacienteControllerTest` | 6 | Vinculo de convenio ao paciente |
| `DisponibilidadeMedicoControllerTest` | 9 | Disponibilidade medica, validacao de intervalo/sobreposicao, permissoes e persistencia na inativacao |
| `EnderecoControllerTest` | 8 | `GET /enderecos/cep/{cep}` e permissoes |
| `EspecialidadeControllerTest` | 10 | CRUD de especialidades e permissoes |
| `MedicoControllerTest` | 8 | CRUD de medicos e restricoes por role |
| `MedicoConvenioControllerTest` | 6 | Convenios aceitos pelo medico, desvinculo e erro 404 padronizado |
| `PacientesControllerTest` | 10 | CRUD/listagem filtrada de pacientes |
| `PrescricaoControllerTest` | 15 | Criacao, detalhamento, listagem por prontuario e por paciente (`GET /prescricoes/paciente/{pacienteId}`) e permissoes |
| `ProntuarioControllerTest` | 24 | Criacao, listagem, edicao, inativacao, bloqueio de `ROLE_FUNCIONARIO` (`403`) e mapeamento de `ResponseStatusException` (403/404/409/422) |

## Estrategia de Testes Frontend

Arquivos atuais de teste:

| Arquivo | Testes | Cobertura principal |
|---|---:|---|
| `frontend/src/api/cep.test.ts` | 2 | Cliente de CEP chama `/enderecos/cep/{cep}` e propaga erro |
| `frontend/src/api/axios.test.ts` | 5 | Interceptor JWT, 401, 403 e login |
| `frontend/src/contexts/AuthContext.test.tsx` | 3 | Persistencia de sessao, limpeza em 401 e logout |
| `frontend/src/components/dashboard/FuncionarioDashboard.test.tsx` | 6 | Dashboard, agenda do dia, loading, erro e retry |
| `frontend/src/pages/Doctors.cep.test.tsx` | 3 | Busca de CEP no cadastro de medico |
| `frontend/src/pages/Patients.cep.test.tsx` | 2 | Busca de CEP e payload sanitizado no cadastro de paciente |
| `frontend/src/pages/Login.test.tsx` | 3 | Token invalido, erro 401 de credenciais e 403 de conta inativa |
| `frontend/src/pages/Users.test.tsx` | 6 | Cadastro de usuarios e seletor de medicos disponiveis |

## Configuracoes de Teste

### Backend

`backend/src/test/resources/application.properties` usa H2 em memoria e desabilita Flyway:

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=create-drop
api.security.token.secret=testSecretKeyForTestingPurposesAtLeast32Chars
```

Flyway fica desabilitado nos testes porque algumas migrations usam sintaxe especifica de MySQL. O schema de teste e criado pelo Hibernate a partir das entidades JPA.

### `@WebMvcTest`

- Usar `@Import(MethodSecurityTestConfig.class)` para ativar `@EnableMethodSecurity` no slice web.
- Usar `@MockBean(JpaMetamodelMappingContext.class)` por causa do `@EnableJpaAuditing` global.
- Autenticar com `.with(user(new Usuario(...)))` quando o controller recebe `@AuthenticationPrincipal Usuario`.
- Adicionar `.with(csrf())` em POST/PUT/DELETE dos testes de controller.

## Testes de Integracao

| Classe | Testes | Cobertura principal |
|---|---:|---|
| `ExclusaoLogicaPersistenciaIntegrationTest` | 4 | Persistencia real (`@SpringBootTest` + H2) de exclusao logica em disponibilidade e medico-convenio, bloqueio imediato (`401`/`403`) apos inativacao de medico e reativacao de vinculo `convenio_pacientes` sem violar unicidade |

A suite atual usa H2 para contexto Spring e mocks para clientes externos. A integracao CEP possui testes unitarios/service/client, mas nao sobe um servidor real do `Consultar-Cep`. O lock pessimista do vinculo medico-usuario tambem esta coberto por teste unitario, nao por uma corrida real em MySQL.

## Testes E2E (Playwright)

Specs em `frontend/e2e/`, executados em serie (1 worker) contra o mesmo banco:

| Arquivo | Testes | Cobertura principal |
|---|---:|---|
| `auth-rbac.spec.ts` | 2 | Login com credenciais invalidas; `ROLE_ADMIN` redirecionado para `/users`, sidebar restrita, bloqueio de `/doctors` e logout |
| `clinic-smoke.spec.ts` | 5 | Admin cria funcionario; funcionario cria especialidade e medico com busca de CEP; disponibilidade e paciente com busca de CEP; agendamento (bloqueio fora da disponibilidade e sucesso no horario valido); admin vincula usuario medico e `ROLE_MEDICO` ve a consulta e o menu `IA clinica` |

A busca de CEP usa um stub HTTP local (`e2e/support/cepStubServer.ts`) no lugar do `Consultar-Cep`. O stub exige o header `X-API-Key`, entao os testes tambem comprovam que o backend injeta a chave na chamada backend-to-backend. Os dados de cada execucao (e-mails, CRM, CPF valido, especialidade) sao gerados por `e2e/support/testData.ts`, o que permite repetir a suite no mesmo banco.

## Pendencias de Teste

- Criar testes de integracao com MySQL real / Testcontainers validando concorrencia real no vinculo medico-usuario e agendamento (as migrations Flyway `V1`–`V25` ja sao exercitadas pelo job `e2e` do CI).
- Ampliar o E2E para `ROLE_AUDITOR`/`ROLE_GESTOR` (auditoria LGPD), prontuario, prescricao e atestado.
- Cobrir fluxo completo de consulta quando os estados adicionais forem implementados.
- Validar o job `e2e` do GitHub Actions na primeira execucao real (so o modo H2 foi rodado localmente).

## Troubleshooting Docker Local

Erro `SQL State: 08S01 / Communications link failure` no `backend-voll` geralmente indica corrida de inicializacao com o MySQL. O Compose usa healthcheck SQL real e retries do Flyway para mitigar.

Erro `SQL State: HY000 / Error Code: 1130` ou `Access denied for user 'root'@'localhost'` pode indicar volume MySQL antigo com senha/grants incompatíveis com o `.env` atual. Em ambiente local descartavel:

```bash
docker compose --env-file backend/.env down -v
docker compose --env-file backend/.env up --build
```

Nao use `down -v` se houver dados locais que precisam ser preservados.
