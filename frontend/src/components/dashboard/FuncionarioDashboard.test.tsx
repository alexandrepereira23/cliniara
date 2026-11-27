import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dashboardApi } from '@/api/dashboard';
import { AuthContext } from '@/contexts/AuthContext';
import type { ConsultaListagem, DashboardRecepcao } from '@/types/api';
import { FuncionarioDashboard } from './FuncionarioDashboard';

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    recepcao: vi.fn(),
  },
}));

function consulta(overrides: Partial<ConsultaListagem>): ConsultaListagem {
  return {
    id: 1,
    nomeMedico: 'Dra. Maria Santos',
    nomePaciente: 'João Oliveira',
    dataHora: '2026-08-18T10:30:00',
    prioridade: 'ROTINA',
    tipo: 'NORMAL',
    status: 'AGENDADA',
    ...overrides,
  };
}

function makeDashboard(overrides: Partial<DashboardRecepcao> = {}): DashboardRecepcao {
  const agendaHoje = overrides.agendaHoje ?? [];
  return {
    dataReferencia: '2026-08-18',
    consultasHoje: agendaHoje.length,
    aguardandoConfirmacaoHoje: agendaHoje.filter(c => c.status === 'AGENDADA').length,
    confirmadasHoje: agendaHoje.filter(c => c.status === 'CONFIRMADA').length,
    aguardandoAtendimentoHoje: agendaHoje.filter(c => c.status === 'CHECK_IN').length,
    emAtendimentoHoje: agendaHoje.filter(c => c.status === 'EM_ATENDIMENTO').length,
    concluidasHoje: agendaHoje.filter(c => c.status === 'CONCLUIDA').length,
    canceladasHoje: 0,
    ausentesHoje: 0,
    proximaConsulta: agendaHoje.find(c => c.status !== 'CONCLUIDA') ?? null,
    agendaHoje,
    ...overrides,
  };
}

function renderDashboard() {
  return render(
    <AuthContext.Provider
      value={{
        token: 'token',
        user: { login: 'funcionario@cliniara.com', role: 'ROLE_FUNCIONARIO' },
        isAuthenticated: true,
        login: async () => undefined,
        logout: () => undefined,
      }}
    >
      <FuncionarioDashboard />
    </AuthContext.Provider>
  );
}

function closestCard(element: HTMLElement) {
  const card = element.closest('[data-slot="card"]');
  expect(card).not.toBeNull();
  return card as HTMLElement;
}

describe('FuncionarioDashboard', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.recepcao).mockReset();
  });

  it('exibe estado de carregamento enquanto a agenda inicial está pendente', () => {
    vi.mocked(dashboardApi.recepcao).mockReturnValue(new Promise(() => undefined));

    renderDashboard();

    expect(screen.getByRole('status')).toHaveTextContent('Carregando agenda de hoje.');
  });

  it('carrega resumo agregado do backend em uma única chamada e usa somente rotas existentes nas ações rápidas', async () => {
    const c1 = consulta({ id: 3, nomePaciente: 'Ana Souza', nomeMedico: 'Dr. Carlos Lima', dataHora: '2026-08-18T08:00:00', prioridade: 'PRIORITARIO', tipo: 'RETORNO', status: 'CHECK_IN' });
    const c2 = consulta({ id: 1, nomePaciente: 'João Oliveira', dataHora: '2026-08-18T10:30:00', status: 'CONFIRMADA' });

    vi.mocked(dashboardApi.recepcao).mockResolvedValue(
      makeDashboard({
        agendaHoje: [c1, c2],
        proximaConsulta: c2,
        aguardandoAtendimentoHoje: 1,
        confirmadasHoje: 1,
        canceladasHoje: 1,
      })
    );

    renderDashboard();

    await waitFor(() => expect(dashboardApi.recepcao).toHaveBeenCalledTimes(1));

    const consultasCard = closestCard(screen.getByText('Consultas de hoje'));
    expect(within(consultasCard).getByText('2')).toBeInTheDocument();

    const filaCard = closestCard(screen.getByText('Aguardando (Check-in)'));
    expect(within(filaCard).getByText('1')).toBeInTheDocument();

    const proximaConsultaCard = closestCard(screen.getByText('Próxima consulta'));
    expect(within(proximaConsultaCard).getByText('10:30')).toBeInTheDocument();
    expect(within(proximaConsultaCard).getByText('João Oliveira')).toBeInTheDocument();
    expect(within(proximaConsultaCard).getByText('Médico: Dra. Maria Santos')).toBeInTheDocument();

    const agendaCard = closestCard(screen.getByText('Agenda de hoje'));
    expect(within(agendaCard).getAllByText('Ana Souza').length).toBeGreaterThan(0);
    expect(within(agendaCard).getAllByText('João Oliveira').length).toBeGreaterThan(0);

    expect(screen.getByRole('link', { name: /Nova consulta/i })).toHaveAttribute('href', '/appointments');
    expect(screen.getByRole('link', { name: /Novo paciente/i })).toHaveAttribute('href', '/patients');
    expect(screen.getByRole('link', { name: /Consultar agenda/i })).toHaveAttribute('href', '/appointments');
    expect(screen.getByRole('link', { name: /Consultar atestados/i })).toHaveAttribute('href', '/certificates');
    expect(screen.queryByRole('link', { name: /Localizar prontuário/i })).not.toBeInTheDocument();
  });

  it('filtra a agenda localmente pelo nome do paciente', async () => {
    vi.mocked(dashboardApi.recepcao).mockResolvedValue(
      makeDashboard({
        agendaHoje: [
          consulta({ id: 1, nomePaciente: 'João Oliveira', dataHora: '2026-08-18T10:30:00' }),
          consulta({ id: 2, nomePaciente: 'Ana Souza', dataHora: '2026-08-18T14:00:00' }),
        ],
      })
    );

    renderDashboard();

    const agendaCard = closestCard(await screen.findByText('Agenda de hoje'));

    fireEvent.change(screen.getByRole('searchbox', { name: /buscar consulta pelo nome do paciente/i }), {
      target: { value: 'Ana' },
    });

    expect(within(agendaCard).getAllByText('Ana Souza').length).toBeGreaterThan(0);
    expect(within(agendaCard).queryByText('João Oliveira')).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('searchbox', { name: /buscar consulta pelo nome do paciente/i }), {
      target: { value: 'Paciente inexistente' },
    });

    expect(screen.getByText('Nenhuma consulta encontrada para essa busca.')).toBeInTheDocument();
  });

  it('exibe estado vazio e ações para agenda sem consultas hoje', async () => {
    vi.mocked(dashboardApi.recepcao).mockResolvedValue(makeDashboard({ agendaHoje: [] }));

    renderDashboard();

    expect(await screen.findByText('Nenhuma consulta agendada para hoje.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Agendar nova consulta/i })).toHaveAttribute('href', '/appointments');
    expect(screen.getByRole('link', { name: /Consultar próximos dias/i })).toHaveAttribute('href', '/appointments');
  });

  it('exibe erro e permite tentar novamente', async () => {
    const user = userEvent.setup();
    vi.mocked(dashboardApi.recepcao)
      .mockRejectedValueOnce(new Error('Falha de rede'))
      .mockResolvedValueOnce(
        makeDashboard({
          agendaHoje: [consulta({ id: 1, nomePaciente: 'João Oliveira', dataHora: '2026-08-18T10:30:00' })],
        })
      );

    renderDashboard();

    expect(await screen.findByText('Não foi possível carregar a agenda.')).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: /Tentar novamente/i })[0]);

    const consultasCard = closestCard(await screen.findByText('Consultas de hoje'));
    expect(within(consultasCard).getByText('1')).toBeInTheDocument();
    expect(screen.getAllByText('João Oliveira').length).toBeGreaterThan(0);
  });

  it('atualiza a agenda ao clicar em atualizar', async () => {
    const user = userEvent.setup();
    vi.mocked(dashboardApi.recepcao)
      .mockResolvedValueOnce(
        makeDashboard({
          agendaHoje: [consulta({ id: 1, nomePaciente: 'João Oliveira', dataHora: '2026-08-18T10:30:00' })],
        })
      )
      .mockResolvedValueOnce(
        makeDashboard({
          agendaHoje: [consulta({ id: 2, nomePaciente: 'Ana Souza', dataHora: '2026-08-18T11:30:00' })],
        })
      );

    renderDashboard();

    expect(await screen.findAllByText('João Oliveira')).not.toHaveLength(0);

    await user.click(screen.getByRole('button', { name: /Atualizar agenda de hoje/i }));

    expect(await screen.findAllByText('Ana Souza')).not.toHaveLength(0);
    expect(screen.queryByText('João Oliveira')).not.toBeInTheDocument();
  });

  it('exibe badge de status e permite filtrar a agenda de hoje por status', async () => {
    const user = userEvent.setup();
    vi.mocked(dashboardApi.recepcao).mockResolvedValue(
      makeDashboard({
        agendaHoje: [
          consulta({ id: 1, nomePaciente: 'João Oliveira', dataHora: '2026-08-18T10:30:00', status: 'AGENDADA' }),
          consulta({ id: 2, nomePaciente: 'Ana Souza', dataHora: '2026-08-18T11:30:00', status: 'CHECK_IN' }),
        ],
      })
    );

    renderDashboard();

    const agendaCard = closestCard(await screen.findByText('Agenda de hoje'));
    expect(within(agendaCard).getAllByText('Check-in').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('combobox', { name: /filtrar agenda por status/i }));
    await user.click(await screen.findByRole('option', { name: 'Check-in' }));

    expect(within(agendaCard).getAllByText('Ana Souza').length).toBeGreaterThan(0);
    expect(within(agendaCard).queryByText('João Oliveira')).not.toBeInTheDocument();
  });
});
