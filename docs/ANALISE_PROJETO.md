# Analise do Projeto Voll.med

Este documento consolida o estado atual do repositorio e separa funcionalidades implementadas, riscos tecnicos e proximas fases recomendadas. Para uma avaliacao aprofundada sob a otica de processos clinicos e produto, consulte tambem `docs/ANALISE_EVOLUCAO_CLINICA.md`.

## Resumo Executivo

O Voll.med esta estruturado como monorepo fullstack com backend Spring Boot 3.5.4, frontend React 19, MySQL 8 / Flyway, Docker Compose, JWT/RBAC, IA clinica, integracao segura com o servico proprio `Consultar-Cep` e pipeline de CI/CD para deploy em VPS.

Estado validado nesta revisao:

- Backend completo ate a migration `V25`; proxima migration esperada: `V26`.
- Backend com **206 testes passando** em `./mvnw test`.
- Frontend com **29 testes passando** em `npm test` e `0` vulnerabilidades no `npm audit`.
- Frontend conectado a API real em todas as rotas ativas.
- Integracao CEP implementada ponta a ponta: backend gateway, `X-API-Key` restrita ao backend e autopreenchimento nos formularios de medicos e pacientes.
- Infraestrutura de producao configurada: frontend na Vercel (`vollmed.usenexora.online`) e APIs + MySQL + Caddy HTTPS na VPS (`docker-compose.prod.yml`, `deploy/Caddyfile`, `.github/workflows/deploy.yml` e `docs/DEPLOY_VPS.md`).

## Estrutura Real

```text
.
├── .github/workflows/deploy.yml  # CI (testes + typecheck + build) e CD via SSH na VPS
├── backend/                      # API Spring Boot 3.5.4 + Flyway + JUnit/MockMvc
├── deploy/Caddyfile              # Proxy reverso HTTPS para as APIs na VPS
├── docs/                         # Documentacao tecnica, regras, deploy, analises e backlog
├── frontend/                     # SPA React 19 + Vite 7 + Tailwind v4 + Vitest
│   └── docs/                     # Arquitetura, contratos de API e Design System do frontend
├── .env.production.example       # Template de variaveis para producao na VPS
├── docker-compose.prod.yml       # Stack de producao (MySQL, backend, consultacep, Caddy)
├── docker-compose.yml            # Stack local fullstack (MySQL, backend, frontend Nginx)
├── AGENTS.md                     # Guia rapido do repositorio para agentes
├── CLAUDE.md                     # Contexto detalhado de arquitetura e convencoes
└── README.md                     # Visao geral do projeto
```

## O Que Ja Esta Implementado

### Backend

- Autenticacao JWT em `/auth/login` e rate limiting em `/auth/*` (10 req/IP a cada 15 min).
- RBAC com `ROLE_ADMIN`, `ROLE_FUNCIONARIO`, `ROLE_MEDICO`, `ROLE_AUDITOR` e `ROLE_GESTOR`.
- Cadastro e listagem de usuarios operacionais por `ROLE_ADMIN`.
- Descoberta de medicos vinculaveis via `GET /auth/medicos-disponiveis`.
- Vinculo transacional usuario-medico com bloqueio pessimista (`@Lock(PESSIMISTIC_WRITE)`).
- CRUD de medicos, pacientes, especialidades e convenios com exclusao logica.
- Agendamento e cancelamento de consultas com prioridade, retorno, disponibilidade real, convenio e `canceladoPor`.
- Prontuario eletronico com ownership do medico e janela de edicao de 24h.
- Prescricoes e atestados vinculados a prontuario.
- Disponibilidade medica e vinculos de convenios com medicos e pacientes.
- Auditoria LGPD via AOP para prontuarios, prescricoes e atestados (`V25`) + auditoria JPA.
- IA clinica restrita a `ROLE_MEDICO` (`claude-opus-4-7` e `claude-sonnet-4-6`).
- Gateway seguro para consulta de CEP (`GET /enderecos/cep/{cep}`).

### Frontend

Rotas principais em `frontend/src/App.tsx`:

- `/login` — autenticacao.
- `/` — dashboard operacional (`FuncionarioDashboard` / `MedicoDashboard`).
- `/users` — gestao de usuarios, restrita a `ROLE_ADMIN`.
- `/doctors` — medicos, convenios aceitos, horarios e busca de CEP.
- `/patients` — pacientes, convenios vinculados e busca de CEP.
- `/appointments` — agendamento e cancelamento de consultas.
- `/medical-records` — listagem e criacao de prontuarios.
- `/prescriptions` — consulta e emissao de prescricoes por prontuario.
- `/certificates` — consulta por paciente e emissao de atestados por prontuario.
- `/specialties` — especialidades medicas.
- `/insurance` — cadastro de convenios.
- `/availability` — disponibilidade medica.
- `/audit` — auditoria LGPD para `ROLE_AUDITOR`/`ROLE_GESTOR`.
- `/clinical-ai` — IA clinica para `ROLE_MEDICO`.
- `/404` e rota fallback — pagina nao encontrada.

## Pontos Fortes Atuais

- Separacao clara de perfis, isolando `ROLE_ADMIN` de dados clinicos e operacionais.
- Soft delete preservando historico clinico e integridade referencial.
- Flyway como fonte de verdade do schema (`ddl-auto=validate`).
- Gateway de CEP evitando exposicao de `X-API-Key` no bundle SPA.
- Cobertura automatizada consistente em controllers, services, seguranca, CEP e fluxos criticos do frontend, validada automaticamente no GitHub Actions.
- Stack reproduzivel localmente com Docker Compose e deploy automatizado em VPS com Caddy.

## Riscos Tecnicos e Divergencias Identificadas

- **Endpoints do backend ainda sem fluxo na UI:** edicao/detalhamento/inativacao de prontuario (`PUT /prontuarios`, `GET /prontuarios/{id}`, `DELETE /prontuarios/{id}`), campos `consultaOrigemId` e `canceladoPor` na tela de consultas, e auditoria filtrada por `PRESCRICAO`/`ATESTADO` (`GET /auditoria/{recursoTipo}/{recursoId}`).
- **Busca e indicadores client-side:** os filtros de busca nas listagens atuam apenas sobre a pagina atual carregada (`size=10`), e seletores auxiliares buscam os primeiros `100` registros.
- **Ausencia de testes com MySQL real e E2E:** a suite backend usa H2 com `flyway.enabled=false` (nao testa migrations MySQL nem locks sob concorrencia real) e ainda nao ha testes E2E em navegador.
- **Privacidade clinica e ciclo de conta medica:** `ROLE_FUNCIONARIO` ainda possui leitura operacional completa de prontuarios/prescricoes/atestados, e usuarios `ROLE_MEDICO` continuam autenticando se o cadastro medico for inativado posteriormente.

## Proximas Fases Recomendadas

As pendencias detalhadas estao priorizadas em `docs/BACKLOG.md`:

1. Implementar E2E smoke tests cobrindo login, navegacao por perfil, cadastros com CEP e agendamento, e limpar arquivos legados sem uso no frontend.
2. Fechar lacunas de integridade clinica (validacoes de retorno, duracao/conflito de agenda e elegibilidade do convenio do paciente) e conectar na UI os endpoints ja existentes no backend.
3. Evoluir o ciclo completo de status da consulta e revisar permissoes sensiveis de `ROLE_FUNCIONARIO` e `ROLE_MEDICO` inativo.
4. Adicionar filtros/agregacoes server-side, code splitting no frontend e testes de integracao com MySQL/Testcontainers.
