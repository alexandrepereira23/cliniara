# Contratos de API — Frontend ↔ Backend

- **Base URL no Axios (`src/api/axios.ts`):** `import.meta.env.VITE_API_BASE_URL ?? '/api'`
  - Dev local (`vite.config.ts`): `/api/*` → `http://localhost:8080/*`
  - Docker local (`nginx.conf`): `/api/*` → `http://backend:8080/*`
  - Produção (Vercel → VPS Caddy): `https://api-vollmed.usenexora.online/api/*` (host atual da API Cliniara) → `backend:8080/*`. Consulte o [runbook da VPS](../../docs/DEPLOY_VPS.md) antes de mudar o host.
- **Auth:** `Authorization: Bearer <tokenJWT>` injetado automaticamente em todas as requisições, exceto `POST /auth/login`.

Perfis de acesso (`Role` em `src/types/auth.ts`):

- `ROLE_ADMIN`: administração técnica, cadastro/listagem de usuários e consulta de CEP. Não acessa dados clínicos nem cadastros operacionais por padrão.
- `ROLE_FUNCIONARIO`: operação da clínica, agenda, cadastros, convênios, CEP e leitura operacional.
- `ROLE_MEDICO`: atendimento clínico, agenda própria, prontuários próprios e IA clínica.
- `ROLE_AUDITOR` / `ROLE_GESTOR`: leitura ampla, inativação administrativa de prontuários e auditoria LGPD.

---

## Autenticação (`src/api/auth.ts`)

### `POST /auth/login` (Público)
```ts
// Request (LoginPayload)
{ login: string, senha: string }

// Response 200 (TokenResponse)
{ tokenJWT: string }

// Response 401 — credenciais inválidas (texto simples retornado por TratadorDeErros)
"Credenciais inválidas"

// Response 403 — usuário ROLE_MEDICO vinculado a médico inativado
"Conta de usuário inativa"

// Response 429 — RateLimitFilter em /auth/* (máx. 10 req/IP em 15 min)
{ erro: "Muitas tentativas. Aguarde 15 minutos antes de tentar novamente." }
```

### `POST /auth/cadastro` (`ROLE_ADMIN`)
```ts
// Request (CadastroUsuarioPayload)
// medicoId é obrigatório para ROLE_MEDICO e proibido para os demais perfis.
{
  login: string,
  senha: string,
  role: 'ROLE_FUNCIONARIO' | 'ROLE_MEDICO' | 'ROLE_AUDITOR' | 'ROLE_GESTOR',
  medicoId?: number
}

// Response 201 (UsuarioDetalhamento)
{ id: number, login: string, role: Role }

// Response 409 — login duplicado ou medicoId inexistente/inativo/já vinculado
{ campo: "conflito", mensagem: string }

// Response 400 — validação (@Valid) ou regra de negócio (medicoId ausente/indevido)
// Response 403 — tentativa de criar ROLE_ADMIN ou usuário sem ROLE_ADMIN
```

### `GET /auth/usuarios` (`ROLE_ADMIN`)
```ts
// Query: ?page=0&size=10&sort=login,asc
// Response 200 — Page<UsuarioDetalhamento>
{
  content: Array<{ id: number, login: string, role: Role }>,
  totalElements: number,
  totalPages: number,
  number: number,
  size: number
}
```

### `GET /auth/medicos-disponiveis` (`ROLE_ADMIN`)
```ts
// Retorna apenas médicos ativos ainda sem usuário vinculado (projeção mínima para Users.tsx).
// Query padrão no frontend: ?page=0&size=100&sort=nome,asc
// Response 200 — Page<MedicoDisponivelVinculoUsuario>
{
  content: Array<{ id: number, nome: string, crm: string }>,
  totalElements: number,
  totalPages: number,
  number: number,
  size: number
}
```

---

## Endereços e CEP (`src/api/cep.ts`)

### `GET /enderecos/cep/{cep}` (`ROLE_FUNCIONARIO` / `ROLE_ADMIN`)
```ts
// Parâmetro {cep}: aceita 8 dígitos ("01001000") ou com máscara ("01001-000").
// O backend atua como gateway seguro (ACL) injetando X-API-Key para o serviço Consultar-Cep.

// Response 200 (EnderecoCepResponse)
{
  cep: string,
  logradouro: string,
  bairro: string,
  cidade: string,
  uf: string,
  complemento?: string
}

// Responses de erro (objeto JSON único):
// 400: { campo: "cep", mensagem: "CEP inválido. Deve conter 8 dígitos numéricos." }
// 404: { campo: "cep", mensagem: "CEP não encontrado: ..." }
// 502: { campo: "cep", mensagem: "Resposta inválida ou incompleta da API de CEP." }
// 503: { campo: "cep", mensagem: "Serviço de consulta de CEP temporariamente indisponível." }
```

---

## Especialidades (`src/api/especialidades.ts`)

### `GET /especialidades` (`ROLE_FUNCIONARIO` / `ROLE_MEDICO` / `ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Response 200 — Page<EspecialidadeListagem> (default size=20)
{ content: Array<{ id: number, nome: string }>, totalElements: number, totalPages: number, number: number, size: number }
```

### `GET /especialidades/{id}` (`ROLE_FUNCIONARIO` / `ROLE_MEDICO` / `ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Response 200
{ id: number, nome: string, ativo: boolean }
```

### `POST /especialidades` (`ROLE_FUNCIONARIO`)
```ts
// Request: { nome: string }
// Response 201: { id: number, nome: string, ativo: boolean }
// Response 409: { campo: "conflito", mensagem: "Já existe uma especialidade com este nome." }
```

### `PUT /especialidades/{id}` (`ROLE_FUNCIONARIO`)
```ts
// Request: { nome: string }
// Response 200: { id: number, nome: string, ativo: boolean }
```

### `DELETE /especialidades/{id}` (`ROLE_FUNCIONARIO`)
```ts
// Response 204 — exclusão lógica
```

---

## Médicos (`src/api/medicos.ts`)

### `POST /medicos` (`ROLE_FUNCIONARIO`)
```ts
// Request (MedicoCadastro)
{
  nome: string,
  email: string,
  telefone: string,
  crm: string,
  especialidadeId: number,
  endereco: {
    logradouro: string, bairro: string, cep: string,
    cidade: string, uf: string,
    numero?: string, complemento?: string
  }
}

// Response 201 (MedicoDetalhamento)
{ id: number, nome: string, email: string, crm: string, telefone: string, especialidade: string, endereco: EnderecoPayload }
```

### `GET /medicos?page=0&size=10&q=&especialidadeId=` (`ROLE_FUNCIONARIO` / `ROLE_MEDICO` / `ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Response 200 — Page<MedicoListagem> (ROLE_MEDICO recebe apenas o próprio registro)
{ content: Array<{ id: number, nome: string, email: string, crm: string, especialidade: string }>, ... }
```

### `GET /medicos/{id}` (`ROLE_FUNCIONARIO` / `ROLE_MEDICO` / `ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Response 200 — MedicoDetalhamento
{ id: number, nome: string, email: string, crm: string, telefone: string, especialidade: string, endereco: EnderecoPayload }
```

### `PUT /medicos` (`ROLE_FUNCIONARIO`)
```ts
// Request (MedicoAtualizacao)
{ id: number, nome?: string, telefone?: string, endereco?: Partial<EnderecoPayload> }
// Response 200 — MedicoDetalhamento
```

### `DELETE /medicos/{id}` (`ROLE_FUNCIONARIO`)
```ts
// Response 204 — exclusão lógica
// Response 409 — ConflitoException quando há consultas futuras pendentes ou em atendimento
// { campo: "conflito", mensagem: "Não é possível inativar o médico pois existem N consulta(s)..." }
```

---

## Disponibilidade do Médico (`src/api/disponibilidade.ts`)

### `POST /medicos/{medicoId}/disponibilidade` (`ROLE_FUNCIONARIO`)
```ts
// Request (DisponibilidadeCadastro)
{ diaSemana: DiaSemana, horaInicio: string, horaFim: string }

// Response 201 (DisponibilidadeListagem)
{ id: number, medicoId: number, diaSemana: DiaSemana, horaInicio: string, horaFim: string, ativo: boolean }
```

### `GET /medicos/{medicoId}/disponibilidade` (`ROLE_FUNCIONARIO` / `ROLE_MEDICO` / `ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Response 200 — Array<DisponibilidadeListagem>
```

### `DELETE /medicos/{medicoId}/disponibilidade/{disponibilidadeId}` (`ROLE_FUNCIONARIO`)
```ts
// Response 204 — inativação lógica
```

---

## Pacientes (`src/api/pacientes.ts`)

### `POST /pacientes` (`ROLE_FUNCIONARIO`)
```ts
// Request (PacienteCadastro)
{ nome: string, email: string, telefone: string, cpf: string, endereco: EnderecoPayload }

// Response 201 (PacienteDetalhamento)
{ id: number, nome: string, email: string, telefone: string, cpf: string, endereco: EnderecoPayload, ativo: boolean }
```

### `GET /pacientes?page=0&size=10&q=` (`ROLE_FUNCIONARIO` / `ROLE_AUDITOR` / `ROLE_GESTOR`; `ROLE_MEDICO` apenas vinculados)
```ts
// Response 200 — Page<PacienteListagem>
{ content: Array<{ id: number, nome: string, email: string, cpf: string }>, ... }
```

### `GET /pacientes/{id}` (`ROLE_FUNCIONARIO` / `ROLE_AUDITOR` / `ROLE_GESTOR`; `ROLE_MEDICO` apenas vinculados)
```ts
// Response 200 — PacienteDetalhamento
```

### `PUT /pacientes` (`ROLE_FUNCIONARIO`)
```ts
// Request (PacienteAtualizacao)
{ id: number, nome?: string, telefone?: string, endereco?: Partial<EnderecoPayload> }
// Response 200 — PacienteDetalhamento
```

### `DELETE /pacientes/{id}` (`ROLE_FUNCIONARIO`)
```ts
// Response 204 — exclusão lógica
```

---

## Consultas (`src/api/consultas.ts`)

### `POST /consultas` (`ROLE_FUNCIONARIO`)
```ts
// Request (ConsultaAgendamento)
// consultaOrigemId pode ser selecionado em Appointments.tsx (vincula automaticamente o médico original).
{
  idPaciente: number,
  idMedico?: number,           // opcional — sistema escolhe médico disponível se omitido (no retorno assume o médico original)
  data: string,                // ISO 8601: "2026-05-15T10:00:00"
  prioridade?: 'ROTINA' | 'PRIORITARIO' | 'URGENCIA',
  consultaOrigemId?: number,   // retorno em até 30 dias
  convenioId?: number
}

// Response 200 (DadosDetalhamentoConsulta)
{
  id: number,
  idMedico: number,
  idPaciente: number,
  data: string,
  prioridade: 'ROTINA' | 'PRIORITARIO' | 'URGENCIA',
  tipo: 'NORMAL' | 'RETORNO',
  status: StatusConsulta,
  consultaOrigemId: number | null,
  consultaReagendadaDeId: number | null,
  convenioId: number | null
}
```

### `GET /consultas?page=0&size=10&status=&incluirEncerradas=false&q=&medicoId=&dataInicio=&dataFim=` (`ROLE_FUNCIONARIO` / `ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Response 200 — Page<ConsultaListagem> (ROLE_MEDICO vê apenas suas consultas)
{
  content: Array<{
    id: number,
    idMedico: number,
    nomeMedico: string,
    idPaciente: number,
    nomePaciente: string,
    dataHora: string,
    prioridade: 'ROTINA' | 'PRIORITARIO' | 'URGENCIA',
    tipo: 'NORMAL' | 'RETORNO',
    status: 'AGENDADA' | 'CONFIRMADA' | 'CHECK_IN' | 'EM_ATENDIMENTO' | 'CONCLUIDA' | 'CANCELADA' | 'AUSENTE' | 'REAGENDADA',
    consultaOrigemId: number | null,
    consultaReagendadaDeId: number | null
  }>,
  ...
}
```

### Transições, Reagendamento e Histórico de Status
```ts
// PATCH /consultas/{id}/confirmar    (ROLE_FUNCIONARIO) → 200 DadosDetalhamentoConsulta
// PATCH /consultas/{id}/check-in     (ROLE_FUNCIONARIO) → 200 DadosDetalhamentoConsulta
// PATCH /consultas/{id}/iniciar      (ROLE_MEDICO responsável) → 200 DadosDetalhamentoConsulta
// PATCH /consultas/{id}/concluir     (ROLE_MEDICO responsável) → 200 DadosDetalhamentoConsulta
// PATCH /consultas/{id}/ausencia     (ROLE_FUNCIONARIO; body opcional { observacao?: string }) → 200 DadosDetalhamentoConsulta
// POST  /consultas/{id}/reagendar    (ROLE_FUNCIONARIO; { novaData, idMedico?, solicitadoPor, motivo? }) → 200 DadosDetalhamentoConsulta
// GET   /consultas/{id}/historico    (FUNCIONARIO | MEDICO responsável | AUDITOR | GESTOR) → 200 HistoricoStatusConsulta[]
```

### `DELETE /consultas` (`ROLE_FUNCIONARIO`)
```ts
// Request (ConsultaCancelamento) — enviado no body do DELETE (24h exigido apenas para PACIENTE)
{
  idConsulta: number,
  motivo: 'PACIENTE_DESISTIU' | 'MEDICO_CANCELOU' | 'OUTROS',
  canceladoPor?: 'PACIENTE' | 'CLINICA'
}

// Response 204
```

---

## Dashboard (`src/api/dashboard.ts`)

### `GET /dashboard/recepcao?data=` (`ROLE_FUNCIONARIO`)
```ts
// Response 200 — DashboardRecepcao
{
  dataReferencia: string,
  consultasHoje: number,
  aguardandoConfirmacaoHoje: number,
  confirmadasHoje: number,
  aguardandoAtendimentoHoje: number,
  emAtendimentoHoje: number,
  concluidasHoje: number,
  canceladasHoje: number,
  ausentesHoje: number,
  proximaConsulta: ConsultaListagem | null,
  agendaHoje: ConsultaListagem[]
}
```

### `GET /dashboard/medico?data=` (`ROLE_MEDICO`)
```ts
// Response 200 — DashboardMedico (escopado ao médico logado)
{
  dataReferencia: string,
  consultasHoje: number,
  urgenciasHoje: number,
  filaEsperaHoje: number,
  emAtendimentoAgora: number,
  concluidasHoje: number,
  proximosSeteDias: number,
  totalPacientesVinculados: number,
  totalConsultasAtivas: number,
  totalProntuarios: number,
  agendaHoje: ConsultaListagem[]
}
```

### `GET /dashboard/gestor?dias=30` (`ROLE_GESTOR` / `ROLE_AUDITOR`)
```ts
// Query: dias entre 1 e 365 (default 30)
// Response 200 — DashboardGestor (100% agregado, sem diagnósticos individuais)
{
  periodoDias: number,
  consultasHoje: number,
  totalConsultasPeriodo: number,
  concluidasPeriodo: number,
  canceladasPeriodo: number,
  ausentesPeriodo: number,
  reagendadasPeriodo: number,
  taxaCancelamentoPercentual: number,
  taxaAbsenteismoPercentual: number,
  medicosAtivos: number,
  pacientesAtivos: number,
  porStatus: Array<{ status: StatusConsulta, quantidade: number }>,
  porEspecialidade: Array<{ especialidade: string, total: number, concluidas: number, canceladas: number, ausentes: number }>
}
```

---

## Prontuários (`src/api/prontuarios.ts`)

### `POST /prontuarios` (`ROLE_MEDICO`)
```ts
// Request (ProntuarioCadastro)
{ consultaId: number, anamnese: string, diagnostico: string, cid10?: string, observacoes?: string }

// Response 201 (ProntuarioDetalhamento)
{ id: number, consultaId: number, nomeMedico: string, nomePaciente: string, anamnese: string, diagnostico: string, cid10?: string, observacoes?: string, dataRegistro: string }
```

### `GET /prontuarios?page=0&size=10&q=` (`ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Response 200 — Page<ProntuarioListagem> (ROLE_MEDICO vê apenas os seus; ROLE_FUNCIONARIO recebe 403)
{ content: Array<{ id: number, consultaId: number, nomePaciente: string, diagnostico: string, dataRegistro: string }>, ... }
```

### `GET /prontuarios/{id}` (`ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Consumido por prontuariosApi.get(id) no modal de detalhamento e pré-carregamento de edição em MedicalRecords.tsx.
// Response 200 — ProntuarioDetalhamento
```

### `GET /prontuarios/paciente/{pacienteId}` (`ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Response 200 — Page<ProntuarioListagem>
```

### `PUT /prontuarios` (`ROLE_MEDICO`)
```ts
// Consumido por prontuariosApi.update(data) em MedicalRecords.tsx (edição pelo médico autor dentro da janela de 24h)
{ id: number, anamnese?: string, diagnostico?: string, cid10?: string, observacoes?: string }
// Response 200 — ProntuarioDetalhamento | Response 422 — janela de 24h expirada
```

### `DELETE /prontuarios/{id}` (`ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Consumido por prontuariosApi.remove(id) com modal de confirmação em MedicalRecords.tsx
// Response 204 — inativação lógica administrativa
```

---

## Prescrições (`src/api/prescricoes.ts`)

### `POST /prescricoes` (`ROLE_MEDICO`)
```ts
// Request (PrescricaoCadastro)
{
  prontuarioId: number,
  tipo: 'SIMPLES' | 'ESPECIAL',
  itens: Array<{ medicamento: string, dosagem: string, posologia: string, duracao: string }>
}

// Response 201 (PrescricaoDetalhamento)
{ id: number, prontuarioId: number, tipo: 'SIMPLES' | 'ESPECIAL', dataEmissao: string, dataValidade?: string, itens: ItemPrescricaoDetalhamento[] }
```

### `GET /prescricoes/{id}` (`ROLE_FUNCIONARIO` operacional / `ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Response 200 — PrescricaoDetalhamento
```

### `GET /prescricoes/prontuario/{prontuarioId}` (`ROLE_FUNCIONARIO` operacional / `ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Response 200 — Page<PrescricaoListagem>
```

### `GET /prescricoes/paciente/{pacienteId}` (`ROLE_FUNCIONARIO` operacional / `ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Consumido por prescricoesApi.listByPaciente(pacienteId, page, size) em Prescriptions.tsx sem expor prontuário/diagnóstico.
// ROLE_MEDICO recebe apenas as prescrições que emitiu.
// Response 200 — Page<PrescricaoListagem>
```

---

## Atestados (`src/api/atestados.ts`)

### `POST /atestados` (`ROLE_MEDICO`)
```ts
// Request (AtestadoCadastro)
{ prontuarioId: number, diasAfastamento: number, cid10?: string, observacoes?: string }

// Response 201 (AtestadoDetalhamento)
{ id: number, prontuarioId: number, diasAfastamento: number, cid10?: string, dataEmissao: string, observacoes?: string }
```

### `GET /atestados/{id}` (`ROLE_FUNCIONARIO` operacional / `ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Response 200 — AtestadoDetalhamento
```

### `GET /atestados/paciente/{pacienteId}` (`ROLE_FUNCIONARIO` operacional / `ROLE_AUDITOR` / `ROLE_GESTOR` / `ROLE_MEDICO`)
```ts
// Response 200 — Page<AtestadoListagem>
```

---

## Convênios e Vínculos (`src/api/convenios.ts`, `medicoConvenios.ts`, `convenioPaciente.ts`)

### Cadastro de Convênios (`/convenios`)
```ts
// POST /convenios (ROLE_FUNCIONARIO)
// Request: { nome: string, codigoANS: string, tipo: 'PARTICULAR' | 'PLANO' }
// Response 201: { id: number, nome: string, codigoANS: string, tipo: TipoConvenio, ativo: boolean }

// GET /convenios (ROLE_FUNCIONARIO / ROLE_MEDICO / ROLE_AUDITOR / ROLE_GESTOR)
// Response 200 — Page<ConvenioListagem>

// GET /convenios/{id} (ROLE_FUNCIONARIO / ROLE_MEDICO / ROLE_AUDITOR / ROLE_GESTOR)
// Response 200 — { id: number, nome: string, codigoANS: string, tipo: TipoConvenio, ativo: boolean }

// PUT /convenios/{id} (ROLE_FUNCIONARIO)
// Request: { nome?: string } -> Response 200

// DELETE /convenios/{id} (ROLE_FUNCIONARIO) -> Response 204
```

### Convênios Aceitos pelo Médico (`/medicos/{medicoId}/convenios`)
```ts
// GET /medicos/{medicoId}/convenios (ROLE_FUNCIONARIO / ROLE_MEDICO / ROLE_AUDITOR / ROLE_GESTOR)
// Response 200 — Array<MedicoConvenioListagem>

// POST /medicos/{medicoId}/convenios (ROLE_FUNCIONARIO)
// Request: { convenioId: number } -> Response 201 (MedicoConvenioListagem)

// DELETE /medicos/{medicoId}/convenios/{convenioId} (ROLE_FUNCIONARIO) -> Response 204
```

### Convênios do Paciente (`/pacientes/{pacienteId}/convenios`)
```ts
// GET /pacientes/{pacienteId}/convenios (ROLE_FUNCIONARIO / ROLE_AUDITOR / ROLE_GESTOR)
// Response 200 — Page<ConvenioPacienteListagem> (paginado, não array puro)
{
  content: Array<{
    id: number,
    pacienteId: number,
    nomePaciente: string,
    convenioId: number,
    nomeConvenio: string,
    codigoANS?: string,
    tipoConvenio: 'PARTICULAR' | 'PLANO',
    numeroCarteirinha: string,
    validade?: string
  }>,
  totalElements: number,
  totalPages: number,
  number: number,
  size: number
}

// POST /pacientes/{pacienteId}/convenios (ROLE_FUNCIONARIO)
// Request: { convenioId: number, numeroCarteirinha: string, validade?: string } -> Response 201

// DELETE /pacientes/{pacienteId}/convenios/{convenioPacienteId} (ROLE_FUNCIONARIO) -> Response 204
```

---

## Auditoria LGPD (`src/api/auditoria.ts`)

### `GET /auditoria/prontuarios/{prontuarioId}` (`ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Query padrão no frontend: ?page=0&size=20&sort=dataHora,desc
// Response 200 — Page<AuditoriaListagem>
{
  content: Array<{
    id: number,
    prontuarioId: number,
    recursoTipo?: 'PRONTUARIO' | 'PRESCRICAO' | 'ATESTADO',
    recursoId?: number,
    loginUsuario: string,
    acao: 'VISUALIZOU' | 'CRIOU' | 'EDITOU',
    dataHora: string,
    ipOrigem?: string
  }>,
  totalElements: number,
  totalPages: number,
  number: number,
  size: number
}
```

### `GET /auditoria/{recursoTipo}/{recursoId}` (`ROLE_AUDITOR` / `ROLE_GESTOR`)
```ts
// Consumido por auditoriaApi.listByRecurso(recursoTipo, recursoId, page, size) na aba "Por Recurso Clínico" de Audit.tsx
// Parâmetro recursoTipo: 'PRONTUARIO' | 'PRESCRICAO' | 'ATESTADO'
// Response 200 — Page<AuditoriaListagem>
```

---

## IA Clínica (`src/api/ia.ts` — apenas `ROLE_MEDICO`)

### `POST /ia/pre-diagnostico`
```ts
// Request: { consultaId: number, sintomas: string }
// Response 200: { resposta: string }
```

### `POST /ia/gerar-laudo`
```ts
// Request: { prontuarioId: number, anotacoes: string }
// Response 200: { resposta: string }
```

### `GET /ia/resumo-historico/{pacienteId}`
```ts
// Response 200: { resposta: string }
```

---

## Tratamento de Erros no Frontend

Utilitário principal: `extractApiError(err, fallback)` em `src/lib/utils.ts` + interceptor em `src/api/axios.ts`.

| Status | Formato retornado pelo Backend | Comportamento no Frontend |
|--------|--------------------------------|---------------------------|
| `400` | `[{ campo, mensagem }]` (`@Valid`) ou `{ campo, mensagem }` (`ValidacaoException` / JSON / CEP) | `extractApiError` concatena os campos ou extrai `mensagem` para exibir em toast/formulário |
| `401` | `"Credenciais inválidas"` no login; `{ erro: "Token inválido..." }` ou `{ erro: "Conta de usuário inativa" }` no filtro | No login exibe mensagem de credenciais; nas demais rotas limpa `localStorage` e redireciona para `/login` |
| `403` | `"Acesso negado"` ou `"Conta de usuário inativa"` (texto simples) | No login exibe a mensagem da API (ex.: `"Conta de usuário inativa"`); nas demais rotas exibe toast de falta de permissão |
| `404` | Vazio (`EntityNotFoundException`) ou `{ campo: "cep", mensagem }` | Tratado inline ou via fallback do `extractApiError` |
| `409` | `{ campo: "conflito", mensagem }` | `extractApiError` exibe a mensagem específica do conflito |
| `422` | Regra temporal clínica (ex.: janela de 24h do prontuário) | Exibe mensagem da API via toast |
| `429` | `{ erro: "Muitas tentativas..." }` em `/auth/*` | Tratado no fluxo de login |
| `502` / `503` | `{ campo: "cep", mensagem }` na falha/indisponibilidade do CEP | Toast amigável mantendo preenchimento manual liberado |
| `500` | `{ campo: "erro_interno", mensagem: "Ocorreu um erro interno..." }` | Toast de erro interno |
