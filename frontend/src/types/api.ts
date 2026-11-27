export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface EnderecoPayload {
  logradouro: string
  bairro: string
  cep: string
  cidade: string
  uf: string
  numero?: string
  complemento?: string
}

// --- Especialidade ---
export interface EspecialidadeListagem {
  id: number
  nome: string
}

export interface EspecialidadeCadastro {
  nome: string
}

export interface EspecialidadeAtualizacao {
  nome: string
}

// --- Convênio ---
export type TipoConvenio = 'PARTICULAR' | 'PLANO'

export interface ConvenioListagem {
  id: number
  nome: string
  codigoANS?: string
  tipo?: TipoConvenio
}

// --- Médico-Convênio ---
export interface MedicoConvenioListagem {
  id: number
  convenioId: number
  nomeConvenio: string
  codigoANS?: string
  tipo: TipoConvenio
}

export interface MedicoConvenioVinculo {
  convenioId: number
}

// --- Convênio-Paciente ---
export interface ConvenioPacienteListagem {
  id: number
  pacienteId: number
  nomePaciente: string
  convenioId: number
  nomeConvenio: string
  codigoANS?: string
  tipoConvenio: TipoConvenio
  numeroCarteirinha: string
  validade?: string
}

export interface ConvenioPacienteCadastro {
  convenioId: number
  numeroCarteirinha: string
  validade?: string
}

export interface ConvenioCadastro {
  nome: string
  codigoANS: string
  tipo: TipoConvenio
}

export interface ConvenioAtualizacao {
  nome?: string
}

// --- Médico ---
export interface MedicoCadastro {
  nome: string
  email: string
  telefone: string
  crm: string
  especialidadeId: number
  endereco: EnderecoPayload
}

export interface MedicoAtualizacao {
  id: number
  nome?: string
  telefone?: string
  endereco?: Partial<EnderecoPayload>
}

export interface MedicoListagem {
  id: number
  nome: string
  email: string
  crm: string
  especialidade: string
}

export interface MedicoDetalhamento extends MedicoListagem {
  telefone: string
  endereco: EnderecoPayload
}

// --- Paciente ---
export interface PacienteCadastro {
  nome: string
  email: string
  telefone: string
  cpf: string
  endereco: EnderecoPayload
}

export interface PacienteAtualizacao {
  id: number
  nome?: string
  telefone?: string
  endereco?: Partial<EnderecoPayload>
}

export interface PacienteListagem {
  id: number
  nome: string
  email: string
  cpf: string
}

export interface PacienteDetalhamento extends PacienteListagem {
  telefone: string
  endereco: EnderecoPayload
  ativo: boolean
}

// --- Prontuário ---
export interface ProntuarioListagem {
  id: number
  consultaId: number
  nomePaciente: string
  diagnostico: string
  dataRegistro: string
}

export interface ProntuarioDetalhamento {
  id: number
  consultaId: number
  nomeMedico: string
  nomePaciente: string
  anamnese: string
  diagnostico: string
  cid10?: string
  observacoes?: string
  dataRegistro: string
}

// --- Atestado ---
export interface AtestadoListagem {
  id: number
  prontuarioId: number
  diasAfastamento: number
  dataEmissao: string
}

export interface AtestadoDetalhamento extends AtestadoListagem {
  cid10?: string
  observacoes?: string
}

// --- Prescrição ---
export type TipoPrescricao = 'SIMPLES' | 'ESPECIAL'

export interface PrescricaoListagem {
  id: number
  prontuarioId: number
  tipo: TipoPrescricao
  dataEmissao: string
  dataValidade?: string
}

// --- Consulta ---
export type Prioridade = 'ROTINA' | 'PRIORITARIO' | 'URGENCIA'
export type TipoConsulta = 'NORMAL' | 'RETORNO'
export type StatusConsulta =
  | 'AGENDADA'
  | 'CONFIRMADA'
  | 'CHECK_IN'
  | 'EM_ATENDIMENTO'
  | 'CONCLUIDA'
  | 'CANCELADA'
  | 'AUSENTE'
  | 'REAGENDADA'
export type MotivoCancelamento = 'PACIENTE_DESISTIU' | 'MEDICO_CANCELOU' | 'OUTROS'
export type CanceladoPor = 'PACIENTE' | 'CLINICA'

export interface ConsultaAgendamento {
  idPaciente: number
  idMedico?: number
  data: string
  prioridade?: Prioridade
  consultaOrigemId?: number
  convenioId?: number
}

export interface ConsultaCancelamento {
  idConsulta: number
  motivo: MotivoCancelamento
  canceladoPor?: CanceladoPor
}

export interface ConsultaReagendamento {
  novaData: string
  idMedico?: number
  solicitadoPor: CanceladoPor
  motivo?: string
}

export interface ConsultaAusencia {
  observacao?: string
}

export interface ConsultaDetalhamento {
  id: number
  idMedico: number
  idPaciente: number
  data: string
  prioridade: Prioridade
  tipo: TipoConsulta
  status: StatusConsulta
  consultaOrigemId?: number | null
  consultaReagendadaDeId?: number | null
  convenioId?: number | null
}

export interface HistoricoStatusConsulta {
  id: number
  statusAnterior?: StatusConsulta | null
  statusNovo: StatusConsulta
  usuarioLogin?: string | null
  motivo?: string | null
  alteradoEm: string
}

// --- Prontuário create / update ---
export interface ProntuarioCadastro {
  consultaId: number
  anamnese: string
  diagnostico: string
  cid10?: string
  observacoes?: string
}

export interface ProntuarioAtualizacao {
  id: number
  anamnese?: string
  diagnostico?: string
  cid10?: string
  observacoes?: string
}

export interface ItemPrescricaoDetalhamento {
  id: number
  medicamento: string
  dosagem: string
  posologia: string
  duracao: string
}

export interface PrescricaoDetalhamento {
  id: number
  prontuarioId: number
  tipo: TipoPrescricao
  dataEmissao: string
  dataValidade?: string
  itens: ItemPrescricaoDetalhamento[]
}

// --- Prescrição create ---
export interface ItemPrescricaoCadastro {
  medicamento: string
  dosagem: string
  posologia: string
  duracao: string
}

export interface PrescricaoCadastro {
  prontuarioId: number
  tipo: TipoPrescricao
  itens: ItemPrescricaoCadastro[]
}

// --- Atestado create ---
export interface AtestadoCadastro {
  prontuarioId: number
  diasAfastamento: number
  cid10?: string
  observacoes?: string
}

// --- Disponibilidade ---
export type DiaSemana = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface DisponibilidadeListagem {
  id: number
  diaSemana: DiaSemana
  horaInicio: string
  horaFim: string
}

export interface DisponibilidadeCadastro {
  diaSemana: DiaSemana
  horaInicio: string
  horaFim: string
}

// --- IA clínica ---
export interface IaPreDiagnosticoPayload {
  consultaId: number
  sintomas: string
}

export interface IaGerarLaudoPayload {
  prontuarioId: number
  anotacoes: string
}

export interface IaResposta {
  resposta: string
}

export interface ConsultaListagem {
  id: number
  idMedico?: number
  nomeMedico: string
  idPaciente?: number
  nomePaciente: string
  dataHora: string
  prioridade: Prioridade
  tipo: TipoConsulta
  status: StatusConsulta
  consultaOrigemId?: number | null
  consultaReagendadaDeId?: number | null
}

// --- Dashboards ---
export interface ContagemStatusDashboard {
  status: StatusConsulta
  quantidade: number
}

export interface ProducaoEspecialidadeDashboard {
  especialidade: string
  total: number
  concluidas: number
  canceladas: number
  ausentes: number
}

export interface DashboardRecepcao {
  dataReferencia: string
  consultasHoje: number
  aguardandoConfirmacaoHoje: number
  confirmadasHoje: number
  aguardandoAtendimentoHoje: number
  emAtendimentoHoje: number
  concluidasHoje: number
  canceladasHoje: number
  ausentesHoje: number
  proximaConsulta: ConsultaListagem | null
  agendaHoje: ConsultaListagem[]
}

export interface DashboardMedico {
  dataReferencia: string
  consultasHoje: number
  urgenciasHoje: number
  filaEsperaHoje: number
  emAtendimentoAgora: number
  concluidasHoje: number
  proximosSeteDias: number
  totalPacientesVinculados: number
  totalConsultasAtivas: number
  totalProntuarios: number
  agendaHoje: ConsultaListagem[]
}

export interface DashboardGestor {
  periodoDias: number
  consultasHoje: number
  totalConsultasPeriodo: number
  concluidasPeriodo: number
  canceladasPeriodo: number
  ausentesPeriodo: number
  reagendadasPeriodo: number
  taxaCancelamentoPercentual: number
  taxaAbsenteismoPercentual: number
  medicosAtivos: number
  pacientesAtivos: number
  porStatus: ContagemStatusDashboard[]
  porEspecialidade: ProducaoEspecialidadeDashboard[]
}
