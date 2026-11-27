import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { dashboardApi } from '@/api/dashboard'
import { AuthContext } from '@/contexts/AuthContext'
import type { DashboardGestor } from '@/types/api'
import { GestorDashboard } from './GestorDashboard'

vi.mock('@/components/DashboardLayout', () => ({
  DashboardLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    gestor: vi.fn(),
  },
}))

const gestorMock = vi.mocked(dashboardApi.gestor)

function makeGestorData(overrides: Partial<DashboardGestor> = {}): DashboardGestor {
  return {
    periodoDias: 30,
    consultasHoje: 5,
    totalConsultasPeriodo: 40,
    concluidasPeriodo: 30,
    canceladasPeriodo: 6,
    ausentesPeriodo: 4,
    reagendadasPeriodo: 2,
    taxaCancelamentoPercentual: 15.0,
    taxaAbsenteismoPercentual: 10.0,
    medicosAtivos: 8,
    pacientesAtivos: 120,
    porStatus: [
      { status: 'CONCLUIDA', quantidade: 30 },
      { status: 'CANCELADA', quantidade: 6 },
      { status: 'AUSENTE', quantidade: 4 },
    ],
    porEspecialidade: [
      { especialidade: 'Cardiologia', total: 25, concluidas: 20, canceladas: 3, ausentes: 2 },
      { especialidade: 'Ortopedia', total: 15, concluidas: 10, canceladas: 3, ausentes: 2 },
    ],
    ...overrides,
  }
}

function renderGestor() {
  return render(
    <AuthContext.Provider
      value={{
        token: 'token',
        user: { login: 'gestor@cliniara.com', role: 'ROLE_GESTOR' },
        isAuthenticated: true,
        login: async () => undefined,
        logout: () => undefined,
      }}
    >
      <GestorDashboard />
    </AuthContext.Provider>,
  )
}

describe('GestorDashboard', () => {
  beforeEach(() => {
    gestorMock.mockReset()
  })

  it('exibe KPIs agregados, produção por especialidade e permite alterar o período', async () => {
    const user = userEvent.setup()
    gestorMock
      .mockResolvedValueOnce(makeGestorData({ periodoDias: 30 }))
      .mockResolvedValueOnce(makeGestorData({ periodoDias: 7, totalConsultasPeriodo: 12 }))

    renderGestor()

    expect(await screen.findByText('Dashboard Gerencial')).toBeInTheDocument()
    expect(screen.getByText('15.0%')).toBeInTheDocument()
    expect(screen.getByText('10.0%')).toBeInTheDocument()
    expect(screen.getByText('8 / 120')).toBeInTheDocument()
    expect(screen.getByText('Cardiologia')).toBeInTheDocument()
    expect(gestorMock).toHaveBeenCalledWith(30)

    await user.click(screen.getByRole('combobox', { name: /selecionar período de análise/i }))
    await user.click(await screen.findByRole('option', { name: 'Últimos 7 dias' }))

    await waitFor(() => expect(gestorMock).toHaveBeenCalledWith(7))
    expect(await screen.findByText('Total (7d)')).toBeInTheDocument()
  })

  it('exibe alerta de erro e permite tentar novamente', async () => {
    const user = userEvent.setup()
    gestorMock
      .mockRejectedValueOnce(new Error('Falha'))
      .mockResolvedValueOnce(makeGestorData())

    renderGestor()

    expect(await screen.findByText('Não foi possível carregar os indicadores gerenciais.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /tentar novamente/i }))

    expect(await screen.findByText('Cardiologia')).toBeInTheDocument()
  })
})
