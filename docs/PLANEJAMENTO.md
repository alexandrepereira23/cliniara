# Planejamento de Evolucao — Cliniara

Este documento organiza as fases do projeto. Pendencias detalhadas e melhorias futuras ficam centralizadas em `docs/BACKLOG.md`.

## Fases Concluidas

### 1. Base da API

- Medicos, pacientes, consultas e usuarios.
- Autenticacao JWT.
- Migrations Flyway iniciais.
- Exclusao logica para entidades clinicas/operacionais relevantes.

### 2. Regras Clinicas Principais

- Prontuario eletronico.
- Disponibilidade real de medicos.
- Prescricoes.
- Triagem por prioridade.
- Retorno de consulta.
- Atestados.
- Convenios e relacionamento com pacientes/medicos.
- Auditoria LGPD e auditoria JPA.
- Especialidades como tabela.

### 3. RBAC e Ownership

- Perfis `ROLE_ADMIN`, `ROLE_FUNCIONARIO`, `ROLE_MEDICO`, `ROLE_AUDITOR` e `ROLE_GESTOR`.
- `ROLE_ADMIN` focado em administracao tecnica e usuarios.
- `ROLE_MEDICO` limitado aos proprios dados assistenciais.
- `ROLE_AUDITOR`/`ROLE_GESTOR` para leitura ampla e auditoria.
- Usuario medico vinculado a medico ativo ainda sem usuario.
- `GET /auth/medicos-disponiveis` para o cadastro de usuario medico.
- Bloqueio pessimista para evitar vinculo concorrente do mesmo medico.

### 4. Frontend Operacional

- React 19 + TypeScript + Vite.
- Login, dashboard e modulos operacionais conectados a API real.
- Rotas para medicos, pacientes, consultas, prontuarios, prescricoes, atestados, especialidades, convenios, disponibilidade, auditoria, usuarios e IA clinica.
- Guardas de navegacao por perfil para UX.

### 5. IA Clinica

- `POST /ia/pre-diagnostico`.
- `POST /ia/gerar-laudo`.
- `GET /ia/resumo-historico/{pacienteId}`.
- Restricao a `ROLE_MEDICO`.
- Uso da Anthropic API via `RestClient`.

### 6. Integracao CEP

- `GET /enderecos/cep/{cep}` protegido por JWT.
- Backend como gateway seguro para o `Consultar-Cep`.
- `X-API-Key` restrito ao backend.
- Variaveis `CEP_API_BASE_URL` e `CEP_API_KEY`.
- Autopreenchimento nos cadastros de medicos e pacientes.
- Testes backend e frontend cobrindo a integracao.

### 7. Deploy, Infraestrutura VPS e CI/CD

- Docker Compose fullstack local com MySQL, backend e frontend (`docker-compose.yml`).
- Migração das APIs (`backend` + `consultacep`) e MySQL do Railway para VPS Linux com `docker-compose.prod.yml` e terminação HTTPS automática no Caddy (`deploy/Caddyfile`, documentado em `docs/DEPLOY_VPS.md`).
- Pipeline de CI/CD em `.github/workflows/deploy.yml` validando testes backend, testes frontend, typecheck e build antes do deploy automatizado via SSH.
- Frontend publicado na Vercel (`https://vollmed.usenexora.online`).
- CORS configurado para ambientes locais, domínio oficial e previews da Vercel.

### 8. Documentacao, Backlog e Hardening de Dependencias

- Atualizacao e alinhamento da documentacao tecnica (`AGENTS.md`, `CLAUDE.md`, `docs/ENDPOINTS.md`, `docs/TESTES.md`, `docs/ANALISE_PROJETO.md`, `docs/PLANEJAMENTO.md`, `docs/DECISOES_TECNICAS.md`, `docs/REGRAS_DE_NEGOCIO.md`, `docs/DEPLOY_VPS.md`, `docs/ANALISE_EVOLUCAO_CLINICA.md` e `frontend/docs/*`).
- Marcacao de `docs/EVOLUCAO_ARQUITETURA.md` como documento historico.
- Consolidacao de `docs/BACKLOG.md` como fonte centralizada de pendencias reais de codigo, integridade e UX.
- Mitigacao e correcao das vulnerabilidades do frontend via `npm audit fix` (0 vulnerabilidades remanescentes).
- Correcao de aderencia no backend: handler dedicado para `ResponseStatusException` em `TratadorDeErros`, persistencia transacional em `DisponibilidadeMedicoController` / `MedicoConvenioController` e `@ParameterObject` em todos os endpoints paginados (206 testes backend passando).

### 9. E2E Smoke Tests

- Suite Playwright + Chromium em `frontend/e2e/` (7 testes): login, RBAC por perfil, cadastro de medico e paciente com busca de CEP, disponibilidade, agendamento com validacao de horario e vinculo de usuario medico.
- Stub HTTP local do `Consultar-Cep` exigindo `X-API-Key`, validando o gateway backend-to-backend.
- Limite do `RateLimitFilter` configuravel por `RATE_LIMIT_MAX_REQUESTS` (padrao `10`; `200` no E2E).
- Job `e2e` no GitHub Actions com MySQL 8.0 + Flyway, requisito do `deploy` na VPS. Pendente: primeira execucao real no CI.

### 10. Integridade Clinica, Elegibilidade e Privacidade

- Validacoes completas de retorno (`consultaOrigemId`): mesmo paciente, mesmo medico (com auto-atribuicao se omitido e bloqueio se inativo), `data > origem`, janela de 30 dias e liberacao de novo retorno apos cancelamento do anterior.
- Elegibilidade de convenio no agendamento: `TipoConvenio.PLANO` exige vinculo ativo e carteirinha dentro da validade em `convenio_pacientes`; `TipoConvenio.PARTICULAR` dispensa carteirinha; reassociacao reativa vinculo inativo sem violar unicidade.
- Disponibilidade medica: validacao de `horaInicio < horaFim` e bloqueio de intervalos sobrepostos no mesmo `diaSemana`.
- Privacidade clinica de `ROLE_FUNCIONARIO`: bloqueio em `/prontuarios/**` (`403`) e restricao de `/medical-records` a `ROLE_MEDICO`, criacao de `GET /prescricoes/paciente/{pacienteId}` e adequacao de `Prescriptions.tsx`, `Certificates.tsx` e `FuncionarioDashboard.tsx` sem expor diagnosticos.
- Bloqueio imediato de conta `ROLE_MEDICO` com medico inativado: `403` no login (`DisabledException`) e `401` em JWTs ativos no `SecurityFillter` (231 testes backend, 30 Vitest, 7 E2E).

### 11. Alinhamento Backend/UI, Governanca de Auditoria e Limpeza de Legados

- `DadosListagemConsulta` enriquecido com `idMedico`, `idPaciente` e `consultaOrigemId` (retrocompativel, sem migration).
- `MedicalRecords.tsx`: detalhamento (`GET /prontuarios/{id}`), edicao pelo medico dentro da janela de 24h (`PUT /prontuarios`) e inativacao administrativa por `ROLE_AUDITOR`/`ROLE_GESTOR` (`DELETE /prontuarios/{id}`).
- `Appointments.tsx`: agendamento de retorno (`consultaOrigemId` com vinculo automatico ao medico original) e seletor `canceladoPor` (`PACIENTE` | `CLINICA`) no cancelamento.
- `Audit.tsx`: exibicao de `recursoTipo`/`recursoId` na trilha por prontuario e consulta direta por recurso clinico (`GET /auditoria/{recursoTipo}/{recursoId}`).
- Navegacao somente-leitura alinhada para `ROLE_AUDITOR`/`ROLE_GESTOR` (mantendo `/audit` como home inicial), ajustes de modo leitura em `Doctors.tsx`/`Patients.tsx` e remocao dos 4 arquivos legados de template (`mockData.ts`, `Home.tsx`, `ManusDialog.tsx`, `Map.tsx`).
- Cobertura expandida para **231 testes backend**, **39 testes Vitest** e **9 testes E2E Playwright** (incluindo fluxo clinico completo e auditoria/inativacao por `ROLE_AUDITOR`).

## Fase Atual — Ciclo Completo da Consulta e Escalabilidade

Entregas prioritarias desta fase (detalhes em `docs/BACKLOG.md`):

- Ciclo completo de estados da consulta (`V26`: Agendada -> Confirmada -> Check-in -> Em atendimento -> Concluida / Cancelada / Ausente) e tratamento de consultas futuras de medico inativado.
- Filtros/agregacoes server-side e code splitting (`React.lazy`) no frontend.

## Proximas Fases Sugeridas

### 1. Otimizacao e Code Splitting do Frontend

- Avaliar lazy loading de rotas.
- Reduzir chunks grandes do build Vite.
- Medir impacto com `npm run build`.

### 2. Ciclo Completo da Consulta

Estados sugeridos:

- Agendada.
- Confirmada.
- Check-in.
- Em atendimento.
- Concluida.
- Cancelada.
- Paciente ausente.
- Reagendada.

### 3. Permissoes Sensiveis

- Definir se `ROLE_FUNCIONARIO` pode ver prontuarios, prescricoes e atestados completos.
- Avaliar visoes resumidas sem conteudo clinico sensivel.
- Garantir auditoria em todo acesso sensivel.

### 4. UX e Auditoria

- Melhorar estados de loading/erro.
- Criar tela dedicada de acesso negado.
- Ampliar filtros e listagens.
- Evoluir relatorios e trilhas de auditoria.

### 5. Evolucoes Futuras da IA

- Melhorar prompts e avaliacao de qualidade.
- Adicionar limites, observabilidade e tratamento de indisponibilidade.
- Avaliar logs/auditoria das chamadas sem armazenar dados sensiveis indevidos.

## Resumo de Migrations

| Migration | Descricao | Status |
|---|---|---|
| `V1`-`V11` | Base do sistema | Aplicado |
| `V12` | Prontuarios | Aplicado |
| `V13` | Disponibilidade medica | Aplicado |
| `V14` | Prescricoes | Aplicado |
| `V15` | Prioridade e tipo de consulta | Aplicado |
| `V16` | Retorno e cancelado por | Aplicado |
| `V17` | Atestados | Aplicado |
| `V18` | Convenios | Aplicado |
| `V19` | Auditoria de prontuario | Aplicado |
| `V20` | Auditoria JPA | Aplicado |
| `V21` | Especialidades como tabela | Aplicado |
| `V22` | Convenios aceitos por medico | Aplicado |
| `V23` | Telefone de pacientes como `VARCHAR(20)` | Aplicado |
| `V24` | Unicidade em `medicos.usuario_id` | Aplicado |
| `V25` | Auditoria por tipo/id de recurso clinico | Aplicado |

Proxima migration: `V26`.
