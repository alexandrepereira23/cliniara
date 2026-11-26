# Backlog do Projeto Voll.med

## Visao Geral

Este arquivo centraliza melhorias futuras, correcoes tecnicas mapeadas, pendencias conhecidas e proximas funcionalidades do Voll.med. Itens aqui nao devem ser descritos como implementados em outros documentos ate que sejam entregues e validados.

## Prioridade Alta

### Correcoes Tecnicas de Integridade (Backend & Frontend)

- **Validacoes clinicas da agenda e retorno (`AgendaDeConsultas`):**
  - Validar no retorno (`consultaOrigemId`) se o paciente e o mesmo da consulta original, se a data do retorno e posterior a consulta de origem e permitir novo retorno caso o anterior tenha sido cancelado.
  - Validar no agendamento com `convenioId` se o paciente possui vinculo ativo e carteirinha dentro da validade (`convenio_pacientes`).
  - Validar `horaInicio < horaFim` e impedir sobreposicao de intervalos no cadastro de disponibilidade medica.
- **Limpeza de codigo legado no frontend:** remover artefatos de template nao importados pelas rotas ativas (`frontend/src/lib/mockData.ts`, `frontend/src/pages/Home.tsx`, `frontend/src/components/ManusDialog.tsx` e `frontend/src/components/Map.tsx`).

### Alinhamento de Funcionalidades Backend -> Frontend

- **Prontuarios (`/medical-records`):** adicionar visualizacao detalhada (`GET /prontuarios/{id}`), edicao pelo medico dentro da janela de 24h (`PUT /prontuarios`) e inativacao para auditor/gestor (`DELETE /prontuarios/{id}`).
- **Consultas (`/appointments`):** expor selecao de `consultaOrigemId` para agendamento de retorno e campo `canceladoPor` (`PACIENTE` | `CLINICA`) no modal de cancelamento.
- **Auditoria (`/audit`):** expor consulta de trilha por tipo de recurso clinico (`GET /auditoria/{recursoTipo}/{recursoId}` para `PRONTUARIO`, `PRESCRICAO` e `ATESTADO`).
- **Revisao de navegacao para `ROLE_AUDITOR` e `ROLE_GESTOR`:** alinhar rotas acessiveis no frontend com o escopo de leitura definido para cada perfil.

### Ampliar Cobertura E2E

- Navegacao de `ROLE_AUDITOR`/`ROLE_GESTOR` e consulta de auditoria LGPD.
- Fluxo clinico completo: prontuario, prescricao e atestado pelo `ROLE_MEDICO`.
- Confirmar o job `e2e` do GitHub Actions na primeira execucao real (MySQL + Flyway).

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

### Acesso de Funcionarios a Dados Clinicos

Definir decisao final sobre `ROLE_FUNCIONARIO` em dados sensiveis:

- Prontuarios, prescricoes e atestados.
- Criacao de visao resumida operacional sem conteudo clinico sensivel (ex.: sem diagnostico/anamnese para recepcao).

### Politica de Usuario Medico com Medico Inativado

Definir se usuario `ROLE_MEDICO` deve continuar autenticando quando o medico vinculado for inativado (bloquear login, bloquear apenas acoes assistenciais ou exigir reativacao/desvinculo formal), tratando tambem o destino das consultas futuras do medico inativado.

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
