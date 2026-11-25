# Análise e plano de evolução clínica — Voll.med

> Análise estática do repositório, com foco em gestão de produto, realismo dos processos clínicos e valorização do portfólio. As propostas deste documento não representam funcionalidades já implementadas nem certificação de conformidade para uso clínico real.

## Diagnóstico geral

**O Voll.med já deixou de ser um CRUD. Ele tem uma boa base técnica e vários módulos de gestão clínica, mas ainda precisa evoluir de “cadastros e funcionalidades separadas” para uma jornada de atendimento completa.**

Essa é a mudança que mais vai valorizar o portfólio: demonstrar entendimento não apenas de como desenvolver telas e APIs, mas de como uma clínica funciona.

A análise considerou código do backend, frontend, migrations, testes, pipeline e documentação. **Esta foi uma análise estática: não houve execução da aplicação nem revalidação dos testes.** Os números de 196 testes backend e 29 frontend são os registrados na documentação.

O objetivo considerado é uma **clínica ambulatorial de pequeno ou médio porte, com várias especialidades** — não um sistema hospitalar completo.

---

## 1. Estado atual: onde o projeto está bem

O projeto já possui fundamentos importantes:

| Área | O que já existe |
|---|---|
| Cadastros | Médicos, pacientes, especialidades e convênios |
| Agenda | Disponibilidade médica, agendamento, cancelamento, prioridade e retorno |
| Atendimento | Prontuários, prescrições e atestados |
| Segurança | JWT, BCrypt, perfis e restrições por médico |
| Rastreabilidade | Auditoria de acessos clínicos e datas de criação/alteração |
| Integrações | CEP via backend e IA restrita a médicos |
| Engenharia | Flyway, Docker, testes e documentação |
| Entrega | Pipeline com testes, verificação TypeScript, build e deploy na VPS |

**Avaliação qualitativa:**

- **Base técnica:** consistente para um portfólio em evolução.
- **Amplitude funcional:** boa.
- **Profundidade dos processos clínicos:** ainda parcial.
- **Preparação para uso com dados reais:** exige trabalho adicional de privacidade, integridade e operação.

Não é recomendável reescrever o projeto. A recomendação é amadurecer o que já existe.

---

## 2. Pontos concretos que merecem atenção primeiro

Antes de adicionar módulos, há algumas inconsistências importantes.

### 2.1. O retorno de consulta precisa de validações adicionais

Em [`AgendaDeConsultas.java`](../backend/src/main/java/med/voll/api/domain/consulta/AgendaDeConsultas.java#L179), a validação verifica existência, atividade, prazo máximo de 30 dias e retorno já cadastrado.

Entretanto, não valida explicitamente:

- Se o paciente é o mesmo da consulta original.
- Se o retorno ocorre **depois** da consulta original.
- Se a consulta original efetivamente aconteceu.
- Qual deve ser a regra de médico/especialidade.
- Se um retorno cancelado deve permitir novo agendamento.

**Impacto:** o vínculo de retorno pode ficar semanticamente incorreto.

### 2.2. Conflito de agenda considera apenas horários iguais

Hoje, a consulta tem um horário de início, mas não duração. A verificação procura outra consulta no mesmo instante.

**Exemplo:** uma consulta às 10h e outra às 10h10 não conflitam na regra atual, mesmo que a primeira devesse durar 30 minutos.

Além disso, a sequência “verificar disponibilidade → salvar” precisa de proteção contra duas recepcionistas agendando simultaneamente.

Referências: [`Consulta.java`](../backend/src/main/java/med/voll/api/domain/consulta/Consulta.java#L36) e [`ConsultaRepository.java`](../backend/src/main/java/med/voll/api/domain/consulta/ConsultaRepository.java#L15).

### 2.3. Convênio do paciente não é validado no agendamento

A agenda verifica se o convênio existe, está ativo e é aceito pelo médico. Não verifica o vínculo do paciente com esse convênio nem a validade da carteirinha.

**Impacto:** o cadastro de convênios do paciente ainda não fecha o processo de agendamento.

### 2.4. Algumas funcionalidades existem só parcialmente na interface

Exemplos:

- O backend permite atualizar prontuário, mas a tela atual oferece listagem e criação, sem fluxo de detalhamento/edição.
- O backend recebe `consultaOrigemId` e `canceladoPor`, mas o formulário de consultas não os utiliza.
- Auditor e gestor têm leitura ampla na API, mas as rotas operacionais do frontend os redirecionam para auditoria.

**Isso não significa simplesmente liberar as telas:** primeiro é preciso definir o papel de cada perfil e alinhar interface e API.

### 2.5. Buscas e indicadores podem mostrar uma visão incompleta

Há buscas que filtram somente a página carregada, como em pacientes e consultas. Alguns seletores carregam apenas os primeiros 100 ou 200 registros.

No dashboard médico, os indicadores diários são calculados sobre as primeiras 100 consultas. Já o dashboard do funcionário busca todas as páginas para depois filtrar o dia.

**Melhoria:** filtros e agregações no backend, com endpoints próprios para agenda e indicadores.

### 2.6. Disponibilidade precisa de maior consistência no backend

O cadastro não apresenta validação de início anterior ao fim nem de sobreposição de intervalos. A validação visual não substitui a proteção da API.

Também merece um teste de integração a remoção: o controller chama `inativar()` sem uma transação de escrita explícita ou `save`.

Referência: [`DisponibilidadeMedicoController.java`](../backend/src/main/java/med/voll/api/controller/DisponibilidadeMedicoController.java).

### 2.7. Auditoria existe, mas ainda não representa conformidade completa

O aspecto de auditoria:

- Ignora silenciosamente falhas ao registrar.
- Não distingue claramente sucesso de acesso negado.
- Não mantém histórico das versões do conteúdo clínico.
- Em algumas listagens, não identifica precisamente os recursos acessados.

**Recomendação:** tratar auditoria como um mecanismo de segurança que também precisa de monitoramento, testes e política de retenção.

---

## 3. Melhorias por módulo

### 3.1. Pacientes — transformar cadastro em visão longitudinal

Hoje, o paciente é essencialmente um cadastro administrativo.

**Próximas melhorias:**

- Data de nascimento e idade calculada.
- Nome social, quando aplicável.
- Responsável legal e contato de emergência.
- Validação dos dígitos verificadores do CPF, não apenas quantidade de números.
- Fluxo para pacientes sem CPF, conforme o escopo escolhido.
- Correção auditada de dados cadastrais e tratamento de duplicidade.

**Principal entrega:** uma **página do paciente** com abas:

> Cadastro · Consultas · Histórico clínico autorizado · Documentos · Convênios · Financeiro

As abas devem respeitar permissões. A recepção não precisa receber o mesmo conteúdo que o médico.

### 3.2. Médicos e especialidades — aproximar da operação real

**Melhorias:**

- CRM com UF.
- RQE, quando aplicável.
- Suporte a múltiplas especialidades, se fizer sentido para o escopo.
- Duração padrão de atendimento por médico/procedimento.
- Férias, afastamentos e bloqueios pontuais.
- Processo de inativação que trate consultas futuras e acesso do usuário.

Hoje, inativar um médico não deveria ser apenas mudar `ativo` para `false`: é preciso decidir o destino da agenda e das pendências.

Para especialidades, evitar inativação sem informar os impactos em médicos e novos agendamentos.

### 3.3. Agenda e consultas — maior prioridade funcional

**Este é o módulo que deve evoluir primeiro.**

Implementar:

- Agenda diária e semanal.
- Filtros por médico, especialidade, período e situação.
- Horários disponíveis calculados pelo backend.
- Duração e conflito por intervalo.
- Reagendamento com histórico.
- Bloqueios por data, feriados e ausências.
- Escolha automática de médico considerando especialidade e convênio.

Revisar também duas regras herdadas do CRUD:

- **Uma consulta por paciente por dia:** uma clínica multiespecialidades pode precisar permitir consultas distintas, sem sobreposição.
- **Cancelamento somente com 24h:** a clínica precisa registrar cancelamentos tardios e indisponibilidade do médico, com motivo, responsável e eventual autorização especial.

A política pode restringir ações, mas não deve impedir registrar o que aconteceu.

#### Ciclo recomendado

```text
Agendada → Confirmada → Check-in → Em atendimento → Concluída
     └──────────────→ Cancelada / Não compareceu
```

Cada transição precisa registrar **quem fez, quando e por quê**, além de validar o estado anterior.

Reagendamento pode ser uma operação com histórico e vínculo entre agendamentos, em vez de apenas mais um status.

### 3.4. Recepção — novo fluxo, não apenas nova tela

Hoje falta representar a chegada do paciente.

**Proposta:**

- Check-in.
- Conferência cadastral e do convênio.
- Fila de espera por médico.
- Horário de chegada e tempo de espera.
- Registro de atraso, desistência e ausência.
- Encaminhamento para atendimento.

A prioridade administrativa já cadastrada **não equivale a uma triagem clínica**. Se houver enfermagem e classificação de risco no futuro, isso exige fluxo, atribuições e validação próprios.

### 3.5. Prontuário — tornar o atendimento o centro do sistema

O prontuário atual tem anamnese, diagnóstico, CID e observações. É um começo, mas ainda enxuto.

**Evolução recomendada:**

- Queixa principal.
- História da doença atual.
- Antecedentes.
- Alergias e medicamentos em uso.
- Sinais vitais.
- Exame físico.
- Hipóteses diagnósticas.
- Conduta e plano de acompanhamento.

Não é necessário exigir todos os campos em todos os atendimentos. É possível usar modelos por especialidade ou uma estrutura SOAP.

#### Mudança importante

Separar:

- **Prontuário do paciente:** conjunto longitudinal.
- **Registro/evolução do atendimento:** documento produzido em cada consulta.

Também implementar:

- Rascunho e finalização.
- Histórico de versões.
- Adendos com justificativa após finalização.
- Proteção contra edição simultânea.
- Política de acesso entre médicos da equipe assistencial.

A janela atual de edição de 24 horas, sozinha, não resolve autoria, integridade nem correções posteriores.

### 3.6. Prescrições — completar emissão e segurança

**Melhorias:**

- Emissão dentro da tela de atendimento.
- Via de administração, apresentação, quantidade e orientações.
- Modelos reutilizáveis.
- Impressão/PDF.
- Histórico e cancelamento justificado.
- Alerta para alergias registradas.

**Ponto sensível:** a validade fixa de 30 dias para receita simples e 60 para especial é uma simplificação do código atual. Não deve ser apresentada como regra universal.

Antes de evoluir receitas controladas, é necessário validar regras por categoria e requisitos aplicáveis. Para o portfólio, é preferível uma receita simples bem implementada a uma prescrição controlada aparentemente completa, mas incorreta.

### 3.7. Atestados e documentos — dar acabamento ao atendimento

**Melhorias:**

- Visualização completa.
- PDF com identificação da clínica, médico e paciente.
- Período de afastamento, além da quantidade de dias.
- Declaração de comparecimento como documento distinto.
- Cancelamento e reemissão rastreáveis.
- Registro da autorização para inclusão de CID, quando cabível.
- Verificação de autenticidade com exposição mínima de dados.

**PDF e QR Code não equivalem a assinatura digital válida.** É importante deixar essa diferença clara na demonstração e documentação.

### 3.8. Convênios — fechar a elegibilidade

O projeto já tem uma boa base de relacionamentos.

**Próximas entregas:**

- Selecionar o vínculo/carteirinha efetivamente utilizado.
- Validar vigência na data do atendimento.
- Conferir compatibilidade entre paciente, médico e procedimento.
- Registrar autorização, quando necessária.
- Preservar as condições utilizadas no atendimento, mesmo após mudanças cadastrais.

Depois disso:

- Tabela de preços.
- Coparticipação.
- Contas a receber dos convênios.

Integração completa com operadoras/TISS deve ficar para uma etapa posterior, não para o próximo incremento.

### 3.9. Usuários e permissões — completar o ciclo de acesso

Hoje, o foco é criar e listar usuários. Faltam operações comuns de administração.

**Melhorias:**

- Ativar/desativar contas.
- Troca e recuperação de senha.
- Primeiro acesso com troca obrigatória.
- Encerramento/revogação de sessões.
- Registro de alterações de perfil.
- Política consistente para usuário vinculado a médico inativo.

#### Revisão de perfis

Separar melhor:

- **Recepção:** cadastro, agenda e documentos operacionais estritamente necessários.
- **Médico:** conteúdo assistencial autorizado.
- **Gestor:** indicadores administrativos, preferencialmente agregados.
- **Auditor:** acesso conforme finalidade e escopo.
- **Admin técnico:** usuários e configurações, sem acesso clínico automático.

**Ser gestor não deveria significar acesso irrestrito a diagnósticos.** Também é recomendável revisar a possibilidade atual de auditor/gestor inativar prontuários.

### 3.10. Auditoria e privacidade — evoluir de log para governança

**Melhorias prioritárias:**

- Filtros por usuário, paciente, período, recurso e ação.
- Resultado da operação: sucesso, falha ou acesso negado.
- Justificativa para acessos excepcionais.
- Auditoria de exportação e emissão de documentos.
- Alertas quando a gravação de auditoria falhar.
- Proteção contra alteração dos registros.
- Política de retenção e restauração.

Além do código, definir:

- Finalidades e bases legais do tratamento.
- Procedimento para solicitações de titulares.
- Resposta a incidentes.
- Uso de fornecedores externos.
- Retenção de documentos clínicos.

**Ter logs não torna o sistema automaticamente adequado à LGPD.**

### 3.11. IA clínica — manter como apoio supervisionado

O diferencial existe, mas não é recomendável ampliar diagnóstico automatizado agora.

Priorizar:

1. Organização das anotações do médico.
2. Resumo do histórico autorizado.
3. Sugestões de informações faltantes.

**Proteções necessárias:**

- Resultado sempre identificado como sugestão.
- Revisão humana antes de incorporar ao prontuário.
- Nunca inventar dados ausentes.
- Modelo e versão do prompt rastreáveis.
- Limites de uso e custo.
- Timeout e tratamento de resposta inválida.
- Avaliação com casos sintéticos.
- Minimização de dados enviados ao fornecedor.

Também deixar explícito quando o resumo considera apenas registros daquele médico ou uma quantidade limitada de registros. Não deve parecer um histórico completo quando não é.

### 3.12. Dashboards — indicadores úteis, não apenas números

**Recepção:**

- Consultas do dia.
- Confirmações pendentes.
- Pacientes aguardando.
- Atrasos e ausências.

**Médico:**

- Próximo paciente.
- Fila de atendimento.
- Registros ainda em rascunho.
- Pendências assistenciais.

**Gestor:**

- Ocupação da agenda.
- Cancelamentos e absenteísmo.
- Tempo médio de espera.
- Produção por especialidade.
- Recebimentos, quando existir financeiro.

Esses indicadores devem vir de consultas agregadas no backend, com definições claras.

---

## 4. Novos módulos que agregariam valor

Escolher poucos, nesta ordem:

| Módulo | Valor | Escopo inicial |
|---|---|---|
| **Financeiro básico** | Fecha o processo administrativo | Valor da consulta, cobrança, pagamento, saldo, estorno e recibo |
| **Notificações** | Reduz trabalho manual e faltas | Confirmação e lembrete, inicialmente por e-mail |
| **Solicitação de exames** | Completa a conduta médica | Solicitar, acompanhar e anexar resultado |
| **Lista de espera** | Aproveita horários liberados | Preferências, oferta de vaga e aceite sem duplicidade |
| **Portal do paciente** | Bom diferencial posterior | Consultas e documentos autorizados, com identidade própria |

Para anexos, já prever controle de acesso, tamanho, tipo de arquivo e armazenamento privado.

Nas notificações, evitar diagnóstico ou detalhes clínicos no conteúdo enviado.

**Deixar para depois:** telemedicina, estoque de medicamentos, faturamento completo de convênios, múltiplas clínicas e aplicativo nativo.

---

## 5. Qualidade técnica e operação

### 5.1. Manter a arquitetura simples

**A recomendação é continuar com um monólito modular.**

Organizar progressivamente por domínio — agenda, pacientes, atendimento, documentos, acesso, financeiro — sem criar microsserviços apenas para demonstrar tecnologia.

O ganho de portfólio está em explicar decisões e garantir consistência, não em aumentar infraestrutura.

### 5.2. Testes que faltam fazer diferença

- Integração com MySQL real/Testcontainers.
- Aplicação das migrations em banco vazio.
- Concorrência de agendamento.
- Transições de situação da consulta.
- Regras de retorno.
- Negativas de autorização e acesso por ID.
- E2E da jornada completa.

A suíte atual usa H2 com Flyway desabilitado, portanto não valida integralmente schema, SQL e comportamento do MySQL de produção.

### 5.3. Frontend

- Busca e paginação reais no servidor.
- Seletores pesquisáveis.
- Formulários e validações padronizados.
- Preservação de dados em falhas.
- Diferenciar “sem resultados” de “não foi possível carregar”.
- Proteção contra perda de rascunho.
- Acessibilidade por teclado.
- Code splitting de rotas.

### 5.4. VPS e deploy

O pipeline já é um ponto positivo. O próximo nível é:

- Backup automatizado, criptografado e fora da VPS.
- **Teste de restauração**, não apenas geração de backup.
- Health/readiness da API.
- Smoke test após deploy.
- Imagens versionadas e estratégia de rollback.
- Logs estruturados sem dados clínicos.
- Monitoramento de disco, disponibilidade e erros.
- Processo seguro para migrations.

O comando atual de deploy concluir com `docker compose ps` não comprova que a jornada da aplicação está funcionando.

---

## 6. Roadmap recomendado

Reorganizar a evolução em entregas completas, cada uma envolvendo API, interface, testes e documentação.

| Ordem | Entrega | Critério principal de conclusão |
|---|---|---|
| **1** | **Integridade e privacidade** | Retornos consistentes, regras de agenda protegidas, elegibilidade definida e matriz de acesso revisada |
| **2** | **Jornada da consulta** | Recepção agenda, confirma e faz check-in; médico inicia e conclui; cancelamento e ausência ficam rastreáveis |
| **3** | **Atendimento integrado** | Uma única área permite consultar histórico autorizado, registrar evolução e emitir documentos |
| **4** | **Agenda operacional** | Visão diária/semanal, duração, bloqueios, reagendamento e filtros no servidor |
| **5** | **Fechamento administrativo** | Cobrança particular, recebimento, recibo e indicadores básicos |
| **6** | **Diferenciais e demonstração** | Lembretes, exames, IA supervisionada e ambiente demonstrativo bem apresentado |

**Testes E2E, segurança e operação devem acompanhar as entregas**, não virar uma fase final de correções.

A documentação técnica em `docs/` e `frontend/docs/` já foi alinhada para refletir a infraestrutura atual em VPS com Caddy e CI/CD; lembre-se de revisar também o `README.md` principal na etapa dedicada de apresentação do portfólio.

---

## 7. Como transformar isso em um portfólio forte

Montar uma demonstração com uma história completa:

> A recepcionista cadastra um paciente, agenda um horário disponível e confirma sua chegada. O médico abre o atendimento, consulta alergias e histórico autorizado, registra a evolução, emite uma receita e conclui. A recepção registra o pagamento. O gestor acompanha os indicadores sem visualizar conteúdo clínico indevido.

Para apresentar:

- Dados exclusivamente sintéticos.
- Ambiente demo isolado, sem credenciais administrativas públicas.
- Roteiro de demonstração de poucos minutos.
- Screenshots e vídeo curto.
- Diagrama de arquitetura.
- Decisões técnicas e limitações assumidas.
- Evidência dos testes e do processo de recuperação.

## Recomendação final

**Não priorizar aumentar a quantidade de módulos agora. Priorizar fechar a jornada clínica com integridade, permissões corretas e boa experiência.**

O próximo grande salto do Voll.med deve ser:

**agenda confiável → recepção → atendimento integrado → documentos → conclusão.**

Uma jornada dessas funcionando bem vai transmitir muito mais maturidade profissional do que vários novos CRUDs ou mais funcionalidades de IA isoladas.
