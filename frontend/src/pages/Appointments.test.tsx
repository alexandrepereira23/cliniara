import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Appointments from './Appointments'
import { consultasApi } from '@/api/consultas'
import { medicosApi } from '@/api/medicos'
import { pacientesApi } from '@/api/pacientes'
import { conveniosApi } from '@/api/convenios'
import { AuthContext } from '@/contexts/AuthContext'
import { toast } from 'sonner'
import type { Role } from '@/types/auth'
import type { ConsultaDetalhamento, ConsultaListagem } from '@/types/api'

vi.mock('@/components/DashboardLayout', () => ({
  DashboardLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/api/consultas', () => ({
  consultasApi: {
    list: vi.fn(),
    schedule: vi.fn(),
    cancel: vi.fn(),
    confirmar: vi.fn(),
    checkIn: vi.fn(),
    iniciar: vi.fn(),
    concluir: vi.fn(),
    registrarAusencia: vi.fn(),
    reagendar: vi.fn(),
    historico: vi.fn(),
  },
}))

vi.mock('@/api/medicos', () => ({
  medicosApi: {
    list: vi.fn(),
  },
}))

vi.mock('@/api/pacientes', () => ({
  pacientesApi: {
    list: vi.fn(),
  },
}))

vi.mock('@/api/convenios', () => ({
  conveniosApi: {
    listAll: vi.fn(),
  },
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

const consultasMock = vi.mocked(consultasApi)
const medicosMock = vi.mocked(medicosApi)
const pacientesMock = vi.mocked(pacientesApi)
const conveniosMock = vi.mocked(conveniosApi)
const toastMock = vi.mocked(toast)

function page<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    size: 10,
    number: 0,
  }
}

function renderWithRole(role: Role) {
  return render(
    <AuthContext.Provider
      value={{
        token: 'token',
        user: { login: `${role.toLowerCase()}@cliniara.com`, role },
        isAuthenticated: true,
        login: async () => undefined,
        logout: () => undefined,
      }}
    >
      <Appointments />
    </AuthContext.Provider>,
  )
}

function futureDateInput(daysAhead = 5) {
  const target = new Date()
  target.setDate(target.getDate() + daysAhead)
  const yyyy = target.getFullYear()
  const mm = String(target.getMonth() + 1).padStart(2, '0')
  const dd = String(target.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

const consultaConcluida: ConsultaListagem = {
  id: 50,
  idMedico: 2,
  nomeMedico: 'Dra. Ana Paula',
  idPaciente: 7,
  nomePaciente: 'João Silva',
  dataHora: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
  prioridade: 'ROTINA',
  tipo: 'NORMAL',
  status: 'CONCLUIDA',
  consultaOrigemId: null,
}

const consultaAgendada: ConsultaListagem = {
  id: 60,
  idMedico: 2,
  nomeMedico: 'Dra. Ana Paula',
  idPaciente: 7,
  nomePaciente: 'Maria Oliveira',
  dataHora: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
  prioridade: 'ROTINA',
  tipo: 'NORMAL',
  status: 'AGENDADA',
  consultaOrigemId: null,
}

const detalhamentoMock: ConsultaDetalhamento = {
  id: 60,
  idMedico: 2,
  idPaciente: 7,
  data: consultaAgendada.dataHora,
  prioridade: 'ROTINA',
  tipo: 'NORMAL',
  status: 'CONFIRMADA',
}

describe('Appointments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    consultasMock.list.mockResolvedValue(page([consultaConcluida, consultaAgendada]))
    consultasMock.schedule.mockResolvedValue(detalhamentoMock)
    consultasMock.cancel.mockResolvedValue(undefined)
    consultasMock.confirmar.mockResolvedValue({ ...detalhamentoMock, status: 'CONFIRMADA' })
    consultasMock.checkIn.mockResolvedValue({ ...detalhamentoMock, status: 'CHECK_IN' })
    consultasMock.iniciar.mockResolvedValue({ ...detalhamentoMock, status: 'EM_ATENDIMENTO' })
    consultasMock.concluir.mockResolvedValue({ ...detalhamentoMock, status: 'CONCLUIDA' })
    consultasMock.registrarAusencia.mockResolvedValue({ ...detalhamentoMock, status: 'AUSENTE' })
    consultasMock.reagendar.mockResolvedValue({ ...detalhamentoMock, id: 61, status: 'AGENDADA', consultaReagendadaDeId: 60 })
    consultasMock.historico.mockResolvedValue([
      {
        id: 1,
        statusAnterior: null,
        statusNovo: 'AGENDADA',
        usuarioLogin: 'recepcao@cliniara.com',
        motivo: 'Consulta agendada',
        alteradoEm: new Date().toISOString(),
      },
    ])
    medicosMock.list.mockResolvedValue(page([
      { id: 2, nome: 'Dra. Ana Paula', email: 'ana@cliniara.com', crm: '12345', especialidade: 'Cardiologia' },
      { id: 3, nome: 'Dr. Bruno', email: 'bruno@cliniara.com', crm: '54321', especialidade: 'Ortopedia' },
    ]))
    pacientesMock.list.mockResolvedValue(page([
      { id: 7, nome: 'João Silva', email: 'joao@email.com', cpf: '12345678901' },
    ]))
    conveniosMock.listAll.mockResolvedValue([])
  })

  it('vincula automaticamente o médico da consulta CONCLUIDA ao selecionar retorno e envia consultaOrigemId', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_FUNCIONARIO')

    await screen.findByText('João Silva')
    await user.click(screen.getByRole('button', { name: /nova consulta/i }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: /paciente/i }))
    await user.click(await screen.findByRole('option', { name: /João Silva/i }))

    await user.click(within(dialog).getByRole('combobox', { name: /retorno de consulta/i }))
    expect(screen.queryByRole('option', { name: /Consulta #60/i })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('option', { name: /Consulta #50 — Dra\. Ana Paula/i }))

    const medicoTrigger = within(dialog).getByRole('combobox', { name: /médico/i })
    expect(medicoTrigger).toBeDisabled()
    expect(medicoTrigger).toHaveTextContent('Dra. Ana Paula')

    const dateStr = futureDateInput(5)
    fireEvent.change(within(dialog).getByLabelText(/data \*/i), { target: { value: dateStr } })
    fireEvent.change(within(dialog).getByLabelText(/hora \*/i), { target: { value: '10:00' } })

    await user.click(within(dialog).getByRole('button', { name: /agendar consulta/i }))

    await waitFor(() => {
      expect(consultasMock.schedule).toHaveBeenCalledWith({
        idPaciente: 7,
        idMedico: 2,
        data: `${dateStr}T10:00:00`,
        consultaOrigemId: 50,
      })
    })
    expect(toastMock.success).toHaveBeenCalledWith('Retorno agendado com sucesso')
  })

  it('envia canceladoPor ao cancelar consulta agendada', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_FUNCIONARIO')

    const row = await screen.findByRole('row', { name: /Maria Oliveira/i })
    await user.click(within(row).getByRole('button', { name: /cancelar consulta/i }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: /motivo do cancelamento/i }))
    await user.click(await screen.findByRole('option', { name: 'Paciente desistiu' }))

    await user.click(within(dialog).getByRole('combobox', { name: /solicitado por/i }))
    await user.click(await screen.findByRole('option', { name: 'Paciente' }))

    await user.click(within(dialog).getByRole('button', { name: /confirmar cancelamento/i }))

    await waitFor(() => {
      expect(consultasMock.cancel).toHaveBeenCalledWith({
        idConsulta: 60,
        motivo: 'PACIENTE_DESISTIU',
        canceladoPor: 'PACIENTE',
      })
    })
    expect(toastMock.success).toHaveBeenCalledWith('Consulta cancelada com sucesso')
  })

  it('ROLE_FUNCIONARIO confirma, reagenda e visualiza histórico da consulta', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_FUNCIONARIO')

    const row = await screen.findByRole('row', { name: /Maria Oliveira/i })

    await user.click(within(row).getByRole('button', { name: /confirmar consulta/i }))
    await waitFor(() => expect(consultasMock.confirmar).toHaveBeenCalledWith(60))

    await user.click(within(row).getByRole('button', { name: /reagendar consulta/i }))
    const rescheduleDialog = await screen.findByRole('dialog')
    const novaData = futureDateInput(8)
    fireEvent.change(within(rescheduleDialog).getByLabelText(/nova data \*/i), { target: { value: novaData } })
    fireEvent.change(within(rescheduleDialog).getByLabelText(/novo horário \*/i), { target: { value: '14:00' } })
    fireEvent.change(within(rescheduleDialog).getByLabelText(/motivo/i), { target: { value: 'Ajuste de agenda' } })
    await user.click(within(rescheduleDialog).getByRole('button', { name: /confirmar reagendamento/i }))

    await waitFor(() => {
      expect(consultasMock.reagendar).toHaveBeenCalledWith(60, {
        novaData: `${novaData}T14:00:00`,
        idMedico: 2,
        solicitadoPor: 'CLINICA',
        motivo: 'Ajuste de agenda',
      })
    })
    expect(toastMock.success).toHaveBeenCalledWith('Consulta reagendada com sucesso')

    await user.click(within(row).getByRole('button', { name: /ver histórico/i }))
    await waitFor(() => expect(consultasMock.historico).toHaveBeenCalledWith(60))
    const historyDialog = await screen.findByRole('dialog')
    expect(within(historyDialog).getByText('Consulta agendada')).toBeInTheDocument()
    expect(within(historyDialog).getByText(/recepcao@cliniara\.com/i)).toBeInTheDocument()
  })

  it('ROLE_MEDICO executa ações assistenciais (iniciar e concluir) e não vê ações da recepção', async () => {
    const user = userEvent.setup()
    consultasMock.list.mockResolvedValue(page([
      { ...consultaAgendada, id: 70, nomePaciente: 'Carlos Checkin', status: 'CHECK_IN' },
      { ...consultaAgendada, id: 71, nomePaciente: 'Beatriz Atendimento', status: 'EM_ATENDIMENTO' },
    ]))

    renderWithRole('ROLE_MEDICO')

    const rowCheckIn = await screen.findByRole('row', { name: /Carlos Checkin/i })
    expect(within(rowCheckIn).queryByRole('button', { name: /cancelar consulta/i })).not.toBeInTheDocument()
    await user.click(within(rowCheckIn).getByRole('button', { name: /iniciar atendimento/i }))
    await waitFor(() => expect(consultasMock.iniciar).toHaveBeenCalledWith(70))

    const rowEmAtendimento = await screen.findByRole('row', { name: /Beatriz Atendimento/i })
    await user.click(within(rowEmAtendimento).getByRole('button', { name: /concluir atendimento/i }))
    await waitFor(() => expect(consultasMock.concluir).toHaveBeenCalledWith(71))
  })

  it('ROLE_AUDITOR visualiza retorno vinculado em modo leitura sem carregar catálogos de agendamento', async () => {
    consultasMock.list.mockResolvedValue(page([
      {
        ...consultaConcluida,
        id: 51,
        tipo: 'RETORNO',
        consultaOrigemId: 50,
      },
    ]))

    renderWithRole('ROLE_AUDITOR')

    expect(await screen.findByText('Retorno (#50)')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /nova consulta/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cancelar consulta/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /ver histórico/i })).toBeInTheDocument()
    expect(medicosMock.list).not.toHaveBeenCalled()
    expect(pacientesMock.list).not.toHaveBeenCalled()
    expect(conveniosMock.listAll).not.toHaveBeenCalled()
  })

  it('envia busca textual q, filtro de status e período para o servidor em GET /consultas', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_FUNCIONARIO')

    await screen.findByText('João Silva')
    fireEvent.change(screen.getByLabelText(/data inicial/i), { target: { value: '2026-11-01' } })
    fireEvent.change(screen.getByLabelText(/data final/i), { target: { value: '2026-11-30' } })
    fireEvent.change(screen.getByRole('searchbox', { name: /buscar por paciente ou médico/i }), {
      target: { value: 'João' },
    })

    await waitFor(() => {
      expect(consultasMock.list).toHaveBeenCalledWith(0, 10, {
        q: 'João',
        dataInicio: '2026-11-01',
        dataFim: '2026-11-30',
      })
    })

    await user.click(screen.getByRole('button', { name: /limpar filtros/i }))
    await waitFor(() => {
      expect(consultasMock.list).toHaveBeenLastCalledWith(0)
    })
  })
})
