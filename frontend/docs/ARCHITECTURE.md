# Arquitetura Frontend — Cliniara

Frontend SPA em React 19 + TypeScript 5.6 + Vite 7. A comunicação com o backend Spring Boot é feita via Axios com interceptor JWT e proxy `/api`.

## Stack

- React 19
- TypeScript 5.6
- Vite 7
- TailwindCSS v4 via `@tailwindcss/vite`
- shadcn/ui sobre Radix UI (`src/components/ui/`)
- wouter para roteamento client-side
- Axios para cliente HTTP
- React Hook Form + Zod v4 para formulários e validação
- Sonner para notificações (toasts)
- Recharts para gráficos e Framer Motion para transições pontuais
- Lucide React para ícones
- Vitest + Testing Library para testes automatizados (`39` testes passando)

## Estrutura Real

```text
frontend/src/
├── api/               # Instância Axios, interceptors e clientes HTTP por domínio
├── components/        # Layout, dashboards por perfil, componentes compartilhados e shadcn/ui
│   ├── dashboard/     # FuncionarioDashboard.tsx, MedicoDashboard.tsx
│   └── ui/            # Primitivos Radix/shadcn + Pagination e Spinner
├── contexts/          # AuthContext, ThemeContext
├── hooks/             # useAuth, useMobile, useComposition, usePersistFn
├── lib/               # authSession, rbac, utils
├── pages/             # Páginas de rota e testes de integração de tela
├── test/              # setup.ts e utilitários de token para Vitest
├── types/             # Tipos de auth, contratos de API e auditoria
├── App.tsx            # Router wouter + guardas por perfil + providers
├── const.ts
├── index.css          # Tailwind v4 (@theme inline) + variáveis CSS (:root e .dark)
└── main.tsx
```

## Proxy `/api` e Ambientes de Execução

A instância Axios (`src/api/axios.ts`) define `baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api'`:

- **Desenvolvimento local (`npm run dev`):** o Vite (`vite.config.ts`, porta `5173`) faz proxy de `/api/*` para `http://localhost:8080`, removendo o prefixo `/api`.
- **Docker Compose local (`docker-compose.yml`):** o container `frontend` usa Nginx (`frontend/nginx.conf`) servindo o bundle estático e redirecionando `location /api/` para `http://backend:8080/`.
- **Produção (Vercel + VPS):** publicado na Vercel (`frontend/vercel.json` faz rewrite SPA para `/index.html`) com `VITE_API_BASE_URL=https://api-vollmed.usenexora.online/api`, onde o Caddy na VPS faz `handle_path /api/*` para `backend:8080`.

## Módulos de API

Arquivos atuais em `frontend/src/api/`:

- `axios.ts` — instância Axios com injeção de `Authorization: Bearer <token>` (omitido em `POST /auth/login`) e limpeza automática de sessão em HTTP `401`.
- `auth.ts` — login, cadastro/listagem de usuários e médicos disponíveis para vínculo (`GET /auth/medicos-disponiveis`, restrito a `ROLE_ADMIN`).
- `cep.ts` — consulta de endereço por CEP via gateway seguro do backend (`GET /enderecos/cep/{cep}`), consumida em `Doctors.tsx` e `Patients.tsx`.
- `medicos.ts` — CRUD e listagem paginada de médicos.
- `pacientes.ts` — CRUD e listagem paginada de pacientes.
- `consultas.ts` — agendamento, listagem paginada e cancelamento de consultas.
- `prontuarios.ts` — listagem geral/por paciente, detalhamento, criação, edição (24h) e inativação administrativa de prontuários.
- `prescricoes.ts` — criação, detalhamento e listagem de prescrições por paciente (`GET /prescricoes/paciente/{pacienteId}`) e por prontuário.
- `atestados.ts` — emissão, detalhamento e histórico de atestados por paciente.
- `especialidades.ts` — CRUD e listagem de especialidades.
- `convenios.ts` — CRUD e listagem de convênios.
- `disponibilidade.ts` — cadastro, listagem e inativação de horários de médicos.
- `medicoConvenios.ts` — vínculo, listagem e desvínculo de convênios aceitos por médicos.
- `convenioPaciente.ts` — associação, listagem paginada e remoção de convênios de pacientes.
- `auditoria.ts` — consulta paginada de logs de auditoria LGPD por prontuário e por recurso clínico (`PRONTUARIO`, `PRESCRICAO`, `ATESTADO`).
- `ia.ts` — pré-diagnóstico, geração de laudo e resumo histórico (`ROLE_MEDICO`).

## Autenticação e Sessão

- `lib/authSession.ts` e `AuthContext` gerenciam o token JWT em `localStorage`, validando expiração (`exp`) e extraindo `sub` (login) e `role`.
- `useAuth` expõe `user`, `isAuthenticated`, `login` e `logout`.
- Resposta `401` em qualquer rota autenticada (exceto `POST /auth/login`) limpa a sessão armazenada, notifica ouvintes e redireciona para `/login`.

## Rotas e Guardas de Navegação

Rotas atuais em `src/App.tsx`:

| Path | Página | Guarda |
|------|--------|--------|
| `/login` | `Login` | Pública |
| `/` | `Dashboard` | `DashboardRoute` (`ROLE_ADMIN` → `/users`; `ROLE_AUDITOR`/`ROLE_GESTOR` → `/audit`) |
| `/users` | `Users` | `AdminRoute` — apenas `ROLE_ADMIN` (demais perfis redirecionam para `/`) |
| `/doctors` | `Doctors` | `NonAdminRoute` |
| `/patients` | `Patients` | `NonAdminRoute` |
| `/appointments` | `Appointments` | `NonAdminRoute` |
| `/medical-records` | `MedicalRecords` | `ProntuarioRoute` — `ROLE_MEDICO`, `ROLE_AUDITOR` e `ROLE_GESTOR` |
| `/prescriptions` | `Prescriptions` | `NonAdminRoute` |
| `/certificates` | `Certificates` | `NonAdminRoute` |
| `/specialties` | `Specialties` | `NonAdminRoute` |
| `/insurance` | `Insurance` | `NonAdminRoute` |
| `/availability` | `Availability` | `NonAdminRoute` |
| `/audit` | `Audit` | `AuditorRoute` — apenas `ROLE_AUDITOR` / `ROLE_GESTOR` |
| `/clinical-ai` | `ClinicalAI` | `MedicoRoute` — apenas `ROLE_MEDICO` |
| `/404` / fallback | `NotFound` | Pública |

A segurança efetiva é aplicada pelo backend (`@PreAuthorize` + filtros de ownership nos services). As guardas do frontend e `src/lib/rbac.ts` (`canWrite`, `canInactivateProntuario`, `canReadPatientConvenios`, etc.) atuam apenas para UX e exibição condicional de botões.

## RBAC na Sidebar (`DashboardLayout.tsx`)

- `ROLE_ADMIN`: vê apenas `/users`.
- `ROLE_AUDITOR` e `ROLE_GESTOR`: iniciam em `/audit` e veem todos os módulos de leitura clínica/operacional (`/audit`, `/medical-records`, `/prescriptions`, `/certificates`, `/appointments`, `/patients`, `/doctors`, `/availability`, `/specialties`, `/insurance`), ocultando `/`, `/users` e `/clinical-ai`.
- `ROLE_MEDICO`: vê módulos clínicos/agenda (incluindo `/clinical-ai`), ocultando `/users`, `/specialties`, `/insurance` e `/audit`.
- `ROLE_FUNCIONARIO`: vê módulos operacionais, `/prescriptions` e `/certificates` (sem expor prontuários/diagnósticos), ocultando `/medical-records`, `/users`, `/audit` e `/clinical-ai`.

## Estado Atual das Páginas

Todas as páginas listadas no roteador consomem exclusivamente a API real (artefatos legados de template foram removidos):

- `Dashboard` (`FuncionarioDashboard` e `MedicoDashboard`), `Users`, `Doctors`, `Patients`, `Appointments`, `MedicalRecords`, `Prescriptions`, `Certificates`, `Specialties`, `Insurance`, `Availability`, `Audit`, `ClinicalAI`.

## Testes Automatizados

Configurados via `vitest.config.ts` (`jsdom` + `@testing-library/react`), somando **39 testes** em 11 arquivos:

- `src/api/axios.test.ts` (5), `src/api/cep.test.ts` (2), `src/contexts/AuthContext.test.tsx` (3), `src/components/dashboard/FuncionarioDashboard.test.tsx` (6), `src/pages/Users.test.tsx` (6), `src/pages/MedicalRecords.test.tsx` (4), `src/pages/Appointments.test.tsx` (3), `src/pages/Doctors.cep.test.tsx` (3), `src/pages/Login.test.tsx` (3), `src/pages/Patients.cep.test.tsx` (2), `src/pages/Audit.test.tsx` (2).
- Validação completa de CI: `npm test`, `npm run check` (`tsc --noEmit`), `npm run build` (`tsc -b && vite build`) e `npm audit`.

## Convenções

- Componentes e páginas em `PascalCase`.
- Clientes de API e utilitários em `camelCase`.
- Imports absolutos via alias `@/`.
- Tipos compartilhados centralizados em `src/types/` (`api.ts`, `auth.ts`, `auditoria.ts`).
- Tokens visuais definidos em `src/index.css` via TailwindCSS v4 (`@theme inline`), sem `tailwind.config.ts`.
