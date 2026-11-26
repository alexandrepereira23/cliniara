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
import type { ConsultaListagem } from '@/types/api'

vi.mock('@/components/DashboardLayout', () => ({
  DashboardLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/api/consultas', () => ({
  consultasApi: {
    list: vi.fn(),
    schedule: vi.fn(),
    cancel: vi.fn(),
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

const consultaOriginal: ConsultaListagem = {
  id: 50,
  idMedico: 2,
  nomeMedico: 'Dra. Ana Paula',
  idPaciente: 7,
  nomePaciente: 'João Silva',
  dataHora: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
  prioridade: 'ROTINA',
  tipo: 'NORMAL',
  consultaOrigemId: null,
}

describe('Appointments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    consultasMock.list.mockResolvedValue(page([consultaOriginal]))
    consultasMock.schedule.mockResolvedValue(consultaOriginal)
    consultasMock.cancel.mockResolvedValue(undefined)
    medicosMock.list.mockResolvedValue(page([
      { id: 2, nome: 'Dra. Ana Paula', email: 'ana@cliniara.com', crm: '12345', especialidade: 'Cardiologia' },
      { id: 3, nome: 'Dr. Bruno', email: 'bruno@cliniara.com', crm: '54321', especialidade: 'Ortopedia' },
    ]))
    pacientesMock.list.mockResolvedValue(page([
      { id: 7, nome: 'João Silva', email: 'joao@email.com', cpf: '12345678901' },
    ]))
    conveniosMock.listAll.mockResolvedValue([])
  })

  it('vincula automaticamente o médico da consulta original ao selecionar retorno e envia consultaOrigemId', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_FUNCIONARIO')

    await screen.findByText('João Silva')
    await user.click(screen.getByRole('button', { name: /nova consulta/i }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: /paciente/i }))
    await user.click(await screen.findByRole('option', { name: /João Silva/i }))

    await user.click(within(dialog).getByRole('combobox', { name: /retorno de consulta/i }))
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

  it('envia canceladoPor ao cancelar consulta', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_FUNCIONARIO')

    const row = await screen.findByRole('row', { name: /João Silva/i })
    await user.click(within(row).getByRole('button', { name: /cancelar consulta/i }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: /motivo do cancelamento/i }))
    await user.click(await screen.findByRole('option', { name: 'Paciente desistiu' }))

    await user.click(within(dialog).getByRole('combobox', { name: /solicitado por/i }))
    await user.click(await screen.findByRole('option', { name: 'Paciente' }))

    await user.click(within(dialog).getByRole('button', { name: /confirmar cancelamento/i }))

    await waitFor(() => {
      expect(consultasMock.cancel).toHaveBeenCalledWith({
        idConsulta: 50,
        motivo: 'PACIENTE_DESISTIU',
        canceladoPor: 'PACIENTE',
      })
    })
    expect(toastMock.success).toHaveBeenCalledWith('Consulta cancelada com sucesso')
  })

  it('ROLE_AUDITOR visualiza retorno vinculado em modo leitura sem carregar catálogos de agendamento', async () => {
    consultasMock.list.mockResolvedValue(page([
      {
        ...consultaOriginal,
        id: 51,
        tipo: 'RETORNO',
        consultaOrigemId: 50,
      },
    ]))

    renderWithRole('ROLE_AUDITOR')

    expect(await screen.findByText('Retorno (#50)')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /nova consulta/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cancelar consulta/i })).not.toBeInTheDocument()
    expect(medicosMock.list).not.toHaveBeenCalled()
    expect(pacientesMock.list).not.toHaveBeenCalled()
    expect(conveniosMock.listAll).not.toHaveBeenCalled()
  })
})
