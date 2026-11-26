# Backlog do Projeto Voll.med

## Visao Geral

Este arquivo centraliza melhorias futuras, correcoes tecnicas mapeadas, pendencias conhecidas e proximas funcionalidades do Voll.med. Itens aqui nao devem ser descritos como implementados em outros documentos ate que sejam entregues e validados.

## Prioridade Alta

### Ciclo Completo da Consulta

Implementar e validar estados adicionais da consulta com trilha de quem alterou e quando:

- Agendada.
- Confirmada.
- Check-in.
- Em atendimento.
- Concluida.
- Cancelada.
- Paciente ausente.
- Reagendada (com vinculo historico entre agendamentos).

### Consultas Futuras de Medico Inativado

O bloqueio imediato de login (`403`) e de JWT ativo (`401`) para `ROLE_MEDICO` inativado ja esta implementado. Permanece pendente definir o tratamento operacional para consultas futuras ja agendadas quando um medico e inativado (cancelamento em lote, aviso ou bloqueio da inativacao).

## Prioridade Media

### Otimizacao, Busca Server-Side e Escalabilidade

- Avaliar lazy loading (`React.lazy`) das rotas em `App.tsx` e medir reducao de chunks com `npm run build`.
- Implementar busca e filtros reais no servidor para pacientes, medicos, consultas e prontuarios (substituindo o filtro client-side restrito a pagina atual).
- Adicionar paginacao/busca pesquisavel nos seletores de formulario (incluindo `GET /auth/medicos-disponiveis`, pacientes e medicos no agendamento).
- Criar endpoints agregados no backend para indicadores dos dashboards (recepcao, medico e gestor).
- Criar testes de integracao com MySQL real / Testcontainers validando todas as migrations Flyway (`V1`–`V25`) e o lock pessimista de vinculo medico-usuario sob concorrencia real.

### Agenda Operacional

- Duracao padrao de consulta e deteccao de conflito por intervalo de tempo (nao apenas horario exato).
- Visualizacao diaria e semanal por medico/especialidade.
- Bloqueios pontuais de agenda, feriados e ausencias.

### Formularios, UX e Acessibilidade

- Padronizar estados de loading, vazio e erro de carregamento (diferenciando lista vazia de falha na API).
- Preservar dados digitados apos falhas de submissao e proteger rascunhos clinicos.
- Criar tela dedicada de acesso negado (`403`).
- Revisar navegacao por teclado, foco, labels e contraste (alinhando badges de status ao Design System).

### Operacao em Producao (VPS)

- Rotina automatizada de backup criptografado do MySQL fora da VPS + teste periodico de restauracao.
- Endpoint de health/readiness dedicado na API e smoke test automatico pos-deploy no GitHub Actions.

## Prioridade Baixa / Futuro

### IA Clinica Supervisionada

- Melhorar prompts, rastreabilidade de modelo/versao e avaliacao com casos sinteticos.
- Configurar timeouts explicitos, limites de uso/custo e tratamento padronizado de indisponibilidade da API Anthropic.
- Minimizar dados enviados e explicitar na UI o recorte do historico analisado.

### Auditoria e Governanca LGPD

- Filtros por usuario, paciente, periodo, recurso, acao e resultado (sucesso, falha, acesso negado).
- Alertas/monitoramento caso a gravacao de auditoria falhe (hoje excecoes no aspect sao ignoradas silenciosamente para nao interromper o atendimento).
- Exportacao controlada e politica de retencao.

### Documentos Clinicos e Novos Modulos

- Impressao/download em PDF padronizado de prescricoes e atestados, declaracao de comparecimento e rascunho/adendos de prontuario.
- Modulos incrementais de negocio: **Financeiro basico** (particular/recibos), **Notificacoes/Lembretes**, **Solicitacao de exames** e **Lista de espera**.
- UX Mobile aprimorada em tabelas extensas.
- Reformulacao dedicada do `README.md` principal para demonstracao de portfolio.

## Itens Ja Resolvidos

- Vinculo usuario-medico ao criar `ROLE_MEDICO` + endpoint `GET /auth/medicos-disponiveis` para `ROLE_ADMIN`.
- Bloqueio pessimista transacional no vinculo medico-usuario.
- Matriz principal de permissoes por perfil e separacao do `ROLE_ADMIN` tecnico.
- Integracao com `Consultar-Cep` via gateway backend com `X-API-Key` restrita ao servidor e autopreenchimento em medicos e pacientes.
- Infraestrutura de producao em VPS com Docker Compose, Caddy HTTPS e pipeline CI/CD no GitHub Actions (`docs/DEPLOY_VPS.md`), alem do frontend na Vercel.
- Docker Compose fullstack local com MySQL, backend e frontend Nginx.
- IA clinica no backend e frontend (`ROLE_MEDICO`).
- Auditoria LGPD por recurso clinico (`V25`) para prontuarios, prescricoes e atestados.
- Especialidades migradas de enum para entidade/tabela (`V21`).
- Vulnerabilidades npm no frontend corrigidas via `npm audit fix` (0 vulnerabilidades remanescentes).
- Tratamento dedicado de `ResponseStatusException` em `TratadorDeErros` preservando status HTTP (`400`, `403`, `404`, `409`, `422`) e payload JSON padronizado.
- Persistencia transacional (`@Transactional` + `save()`) em `DisponibilidadeMedicoController` e `MedicoConvenioController`, validada com teste de integracao em banco H2.
- `@ParameterObject` aplicado em todos os parametros `Pageable` dos controllers, protegido por teste reflexivo automatizado.
- E2E smoke tests com Playwright (login, RBAC, cadastros com CEP, disponibilidade, agendamento e vinculo de usuario medico), integrados ao CI como gate do deploy.
- Integridade clinica da agenda e disponibilidade: regras completas de retorno (`consultaOrigemId` com mesmo paciente, mesmo medico/auto-atribuicao, `data > origem`, limite de 30 dias e novo retorno apos cancelamento), elegibilidade de convenio `PLANO` (vinculo ativo e validade da carteirinha) vs `PARTICULAR`, reativacao de vinculo `convenio_pacientes` sem violar unicidade, e validacao de `horaInicio < horaFim` e nao sobreposicao de horarios de disponibilidade.
- Privacidade clinica para `ROLE_FUNCIONARIO`: bloqueio de leitura em `/prontuarios/**` (`403`) e restricao de `/medical-records` a `ROLE_MEDICO`, criacao de `GET /prescricoes/paciente/{pacienteId}` e adaptacao de `Prescriptions.tsx`, `Certificates.tsx` e `FuncionarioDashboard.tsx` sem expor diagnosticos.
- Bloqueio imediato de conta `ROLE_MEDICO` com medico inativado: `403` no login (`DisabledException`) e `401` imediato em JWTs ja emitidos no `SecurityFillter`.
- Alinhamento completo Backend -> Frontend: detalhamento (`GET /prontuarios/{id}`), edicao em 24h (`PUT /prontuarios`) e inativacao administrativa (`DELETE /prontuarios/{id}`) em `MedicalRecords.tsx`; agendamento de retorno (`consultaOrigemId` + campos de apoio em `DadosListagemConsulta`) e `canceladoPor` em `Appointments.tsx`; consulta LGPD por recurso clinico (`GET /auditoria/{recursoTipo}/{recursoId}`) em `Audit.tsx`.
- Navegacao somente-leitura alinhada para `ROLE_AUDITOR` e `ROLE_GESTOR` (mantendo `/audit` como home inicial e liberando consulta de modulos operacionais/clinicos) + remocao dos 4 arquivos legados de template (`mockData.ts`, `Home.tsx`, `ManusDialog.tsx`, `Map.tsx`).
- Expansao da cobertura automatizada: +9 testes Vitest (`MedicalRecords.test.tsx`, `Appointments.test.tsx`, `Audit.test.tsx`) e +2 cenarios E2E Playwright (prontuario/edicao/prescricao por `ROLE_MEDICO` e auditoria/inativacao por `ROLE_AUDITOR`).
