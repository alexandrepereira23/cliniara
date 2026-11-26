import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Audit from './Audit'
import { auditoriaApi } from '@/api/auditoria'
import { prontuariosApi } from '@/api/prontuarios'

vi.mock('@/components/DashboardLayout', () => ({
  DashboardLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/api/auditoria', () => ({
  auditoriaApi: {
    listByProntuario: vi.fn(),
    listByRecurso: vi.fn(),
  },
}))

vi.mock('@/api/prontuarios', () => ({
  prontuariosApi: {
    list: vi.fn(),
  },
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

const auditoriaMock = vi.mocked(auditoriaApi)
const prontuariosMock = vi.mocked(prontuariosApi)

function page<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    size: 20,
    number: 0,
  }
}

describe('Audit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    prontuariosMock.list.mockResolvedValue(page([
      {
        id: 1,
        consultaId: 10,
        nomePaciente: 'Maria Souza',
        diagnostico: 'Sinusite aguda',
        dataRegistro: '2026-11-26T10:00:00',
      },
    ]))
    auditoriaMock.listByProntuario.mockResolvedValue(page([
      {
        id: 100,
        prontuarioId: 1,
        recursoTipo: 'PRONTUARIO',
        recursoId: 1,
        loginUsuario: 'medico@cliniara.com',
        acao: 'CRIOU',
        dataHora: '2026-11-26T10:00:00',
        ipOrigem: '127.0.0.1',
      },
      {
        id: 101,
        prontuarioId: 1,
        recursoTipo: 'PRESCRICAO',
        recursoId: 15,
        loginUsuario: 'medico@cliniara.com',
        acao: 'CRIOU',
        dataHora: '2026-11-26T10:05:00',
        ipOrigem: '127.0.0.1',
      },
    ]))
    auditoriaMock.listByRecurso.mockResolvedValue(page([
      {
        id: 101,
        prontuarioId: 1,
        recursoTipo: 'PRESCRICAO',
        recursoId: 15,
        loginUsuario: 'medico@cliniara.com',
        acao: 'CRIOU',
        dataHora: '2026-11-26T10:05:00',
        ipOrigem: '127.0.0.1',
      },
    ]))
  })

  it('exibe tipo e ID do recurso na trilha por prontuário e permite inspecionar recurso vinculado', async () => {
    const user = userEvent.setup()
    render(<Audit />)

    await user.click(await screen.findByText('Maria Souza'))

    await waitFor(() => expect(auditoriaMock.listByProntuario).toHaveBeenCalledWith(1, 0))
    expect(await screen.findByText('Prontuário #1')).toBeInTheDocument()
    const prescBtn = screen.getByRole('button', { name: 'Prescrição #15' })
    expect(prescBtn).toBeInTheDocument()

    await user.click(prescBtn)

    await waitFor(() => expect(auditoriaMock.listByRecurso).toHaveBeenCalledWith('PRESCRICAO', 15, 0))
    expect(await screen.findByText('Trilha de auditoria — Prescrição #15')).toBeInTheDocument()
  })

  it('permite consultar trilha diretamente por recurso clínico via GET /auditoria/{recursoTipo}/{recursoId}', async () => {
    const user = userEvent.setup()
    render(<Audit />)

    await user.click(screen.getByRole('button', { name: 'Por Recurso Clínico' }))

    await user.click(screen.getByRole('combobox', { name: /tipo de recurso/i }))
    await user.click(await screen.findByRole('option', { name: 'Prescrição' }))

    await user.type(screen.getByLabelText(/id do recurso/i), '15')
    await user.click(screen.getByRole('button', { name: /consultar trilha/i }))

    await waitFor(() => expect(auditoriaMock.listByRecurso).toHaveBeenCalledWith('PRESCRICAO', 15, 0))
    expect(await screen.findByText('Prescrição #15')).toBeInTheDocument()
  })
})
