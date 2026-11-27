import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MedicalRecords from './MedicalRecords'
import { prontuariosApi } from '@/api/prontuarios'
import { consultasApi } from '@/api/consultas'
import { AuthContext } from '@/contexts/AuthContext'
import { toast } from 'sonner'
import type { Role } from '@/types/auth'
import type { ProntuarioDetalhamento, ProntuarioListagem } from '@/types/api'

vi.mock('@/components/DashboardLayout', () => ({
  DashboardLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/api/prontuarios', () => ({
  prontuariosApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('@/api/consultas', () => ({
  consultasApi: {
    list: vi.fn(),
  },
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

const prontuariosMock = vi.mocked(prontuariosApi)
const consultasMock = vi.mocked(consultasApi)
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
      <MedicalRecords />
    </AuthContext.Provider>,
  )
}

function makeRecord(overrides: Partial<ProntuarioListagem> = {}): ProntuarioListagem {
  return {
    id: 1,
    consultaId: 10,
    nomePaciente: 'Maria Souza',
    diagnostico: 'Sinusite aguda',
    dataRegistro: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    ...overrides,
  }
}

function makeDetail(overrides: Partial<ProntuarioDetalhamento> = {}): ProntuarioDetalhamento {
  return {
    id: 1,
    consultaId: 10,
    nomeMedico: 'Dr. Carlos Lima',
    nomePaciente: 'Maria Souza',
    anamnese: 'Cefaleia e congestão nasal há 3 dias',
    diagnostico: 'Sinusite aguda',
    cid10: 'J01.9',
    observacoes: 'Hidratação e repouso',
    dataRegistro: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    ...overrides,
  }
}

describe('MedicalRecords', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    prontuariosMock.list.mockResolvedValue(page([makeRecord()]))
    prontuariosMock.get.mockResolvedValue(makeDetail())
    prontuariosMock.update.mockResolvedValue(makeDetail({ diagnostico: 'Sinusite bacteriana' }))
    prontuariosMock.remove.mockResolvedValue(undefined)
    consultasMock.list.mockResolvedValue(page([]))
  })

  it('ROLE_MEDICO visualiza detalhes completos do prontuário via GET /prontuarios/{id}', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_MEDICO')

    const row = await screen.findByRole('row', { name: /Maria Souza/i })
    await user.click(within(row).getByRole('button', { name: /detalhar prontuário/i }))

    await waitFor(() => expect(prontuariosMock.get).toHaveBeenCalledWith(1))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Dr. Carlos Lima')).toBeInTheDocument()
    expect(within(dialog).getByText('J01.9')).toBeInTheDocument()
    expect(within(dialog).getByText('Cefaleia e congestão nasal há 3 dias')).toBeInTheDocument()
    expect(within(dialog).getByText('Editável (até 24h)')).toBeInTheDocument()
  })

  it('ROLE_MEDICO edita prontuário dentro da janela de 24h via PUT /prontuarios', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_MEDICO')

    const row = await screen.findByRole('row', { name: /Maria Souza/i })
    await user.click(within(row).getByRole('button', { name: /editar prontuário/i }))

    const dialog = await screen.findByRole('dialog')
    const diagInput = within(dialog).getByLabelText(/diagnóstico/i)
    await user.clear(diagInput)
    await user.type(diagInput, 'Sinusite bacteriana')
    await user.click(within(dialog).getByRole('button', { name: /salvar alterações/i }))

    await waitFor(() => {
      expect(prontuariosMock.update).toHaveBeenCalledWith({
        id: 1,
        anamnese: 'Cefaleia e congestão nasal há 3 dias',
        diagnostico: 'Sinusite bacteriana',
        cid10: 'J01.9',
        observacoes: 'Hidratação e repouso',
      })
    })
    expect(toastMock.success).toHaveBeenCalledWith('Prontuário atualizado com sucesso')
  })

  it('desabilita botão de edição para prontuário fora da janela de 24h', async () => {
    const expiredDate = new Date(Date.now() - 30 * 60 * 60 * 1000).toISOString()
    prontuariosMock.list.mockResolvedValue(page([makeRecord({ dataRegistro: expiredDate })]))

    renderWithRole('ROLE_MEDICO')

    const row = await screen.findByRole('row', { name: /Maria Souza/i })
    const editBtn = within(row).getByRole('button', { name: /editar prontuário/i })
    expect(editBtn).toBeDisabled()
    expect(editBtn).toHaveAttribute('title', 'Janela de edição de 24h expirada')
  })

  it('ROLE_AUDITOR não vê criação/edição e consegue inativar prontuário com confirmação', async () => {
    const user = userEvent.setup()
    renderWithRole('ROLE_AUDITOR')

    const row = await screen.findByRole('row', { name: /Maria Souza/i })
    expect(screen.queryByRole('button', { name: /novo prontuário/i })).not.toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: /editar prontuário/i })).not.toBeInTheDocument()

    await user.click(within(row).getByRole('button', { name: /inativar prontuário/i }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /confirmar inativação/i }))

    await waitFor(() => expect(prontuariosMock.remove).toHaveBeenCalledWith(1))
    expect(toastMock.success).toHaveBeenCalledWith('Prontuário inativado com sucesso')
  })

  it('ROLE_MEDICO vê apenas consultas EM_ATENDIMENTO ou CONCLUIDA no seletor de Novo Prontuário', async () => {
    const user = userEvent.setup()
    consultasMock.list.mockResolvedValue(page([
      {
        id: 11,
        nomeMedico: 'Dr. Carlos Lima',
        nomePaciente: 'Paciente Agendado',
        dataHora: new Date().toISOString(),
        prioridade: 'ROTINA',
        tipo: 'NORMAL',
        status: 'AGENDADA',
      },
      {
        id: 12,
        nomeMedico: 'Dr. Carlos Lima',
        nomePaciente: 'Paciente Em Atendimento',
        dataHora: new Date().toISOString(),
        prioridade: 'ROTINA',
        tipo: 'NORMAL',
        status: 'EM_ATENDIMENTO',
      },
    ]))

    renderWithRole('ROLE_MEDICO')

    await screen.findByText('Maria Souza')
    await user.click(screen.getByRole('button', { name: /novo prontuário/i }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: /consulta/i }))

    expect(await screen.findByRole('option', { name: /Paciente Em Atendimento/i })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Paciente Agendado/i })).not.toBeInTheDocument()
  })

  it('envia busca textual q para GET /prontuarios no servidor com debounce', async () => {
    renderWithRole('ROLE_MEDICO')

    await screen.findByText('Maria Souza')
    const searchInput = screen.getByRole('searchbox', { name: /buscar por paciente, diagnóstico ou cid-10/i })
    const user = userEvent.setup()
    await user.type(searchInput, 'Sinusite')

    await waitFor(() => {
      expect(prontuariosMock.list).toHaveBeenCalledWith(0, 10, 'Sinusite')
    })
  })
})
