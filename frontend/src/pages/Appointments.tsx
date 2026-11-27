import { useState, useEffect } from 'react';
import { DashboardLayout } from '@/components/DashboardLayout';
import { PageHeader } from '@/components/PageHeader';
import { SearchInput } from '@/components/SearchInput';
import { Badge } from '@/components/Badge';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Calendar,
  CalendarClock,
  CheckCheck,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  History,
  LogIn,
  Play,
  UserX,
  XCircle,
} from 'lucide-react';
import { toast } from 'sonner';
import { consultasApi, type ConsultaListFilters } from '@/api/consultas';
import { medicosApi } from '@/api/medicos';
import { pacientesApi } from '@/api/pacientes';
import { conveniosApi } from '@/api/convenios';
import { useAuth } from '@/hooks/useAuth';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { canWrite } from '@/lib/rbac';
import { extractApiError } from '@/lib/utils';
import {
  allStatusConsultas,
  statusConsultaBadge,
  statusConsultaLabel,
} from '@/lib/consultaStatus';
import type {
  ConsultaListagem,
  ConsultaAgendamento,
  ConsultaCancelamento,
  ConsultaReagendamento,
  CanceladoPor,
  HistoricoStatusConsulta,
  MotivoCancelamento,
  Prioridade,
  StatusConsulta,
  TipoConsulta,
  MedicoListagem,
  PacienteListagem,
  ConvenioListagem,
} from '@/types/api';

const prioridadeLabel: Record<Prioridade, string> = {
  ROTINA: 'Rotina',
  PRIORITARIO: 'Prioritário',
  URGENCIA: 'Urgência',
};

const prioridadeBadge: Record<Prioridade, 'success' | 'warning' | 'error'> = {
  ROTINA: 'success',
  PRIORITARIO: 'warning',
  URGENCIA: 'error',
};

const tipoLabel: Record<TipoConsulta, string> = {
  NORMAL: 'Normal',
  RETORNO: 'Retorno',
};

const motivoLabel: Record<MotivoCancelamento, string> = {
  PACIENTE_DESISTIU: 'Paciente desistiu',
  MEDICO_CANCELOU: 'Médico cancelou',
  OUTROS: 'Outros',
};

const canceladoPorLabel: Record<CanceladoPor, string> = {
  PACIENTE: 'Paciente',
  CLINICA: 'Clínica',
};

type StatusFilterOption = 'ATIVAS' | 'TODAS' | StatusConsulta;

interface ScheduleForm {
  idPaciente: string
  idMedico: string
  dateInput: string
  timeInput: string
  prioridade: Prioridade | ''
  convenioId: string
  consultaOrigemId: string
}

interface RescheduleForm {
  dateInput: string
  timeInput: string
  idMedico: string
  solicitadoPor: CanceladoPor
  motivo: string
}

const emptyScheduleForm = (): ScheduleForm => ({
  idPaciente: '',
  idMedico: '',
  dateInput: '',
  timeInput: '',
  prioridade: '',
  convenioId: '',
  consultaOrigemId: '',
});

const emptyRescheduleForm = (): RescheduleForm => ({
  dateInput: '',
  timeInput: '',
  idMedico: '',
  solicitadoPor: 'CLINICA',
  motivo: '',
});

function isTodayOrPast(iso: string): boolean {
  const apptDate = new Date(iso);
  const now = new Date();
  const apptDay = new Date(apptDate.getFullYear(), apptDate.getMonth(), apptDate.getDate());
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return apptDay.getTime() <= today.getTime();
}

function buildListFilters(
  filter: StatusFilterOption,
  q: string,
  medicoFilter: string,
  dataInicio: string,
  dataFim: string,
): ConsultaListFilters | undefined {
  const filters: ConsultaListFilters = {};
  if (filter === 'TODAS') {
    filters.incluirEncerradas = true;
  } else if (filter !== 'ATIVAS') {
    filters.status = filter;
  }
  if (q.trim()) {
    filters.q = q.trim();
  }
  if (medicoFilter && medicoFilter !== 'ALL') {
    filters.medicoId = Number(medicoFilter);
  }
  if (dataInicio) {
    filters.dataInicio = dataInicio;
  }
  if (dataFim) {
    filters.dataFim = dataFim;
  }
  return Object.keys(filters).length > 0 ? filters : undefined;
}

export default function Appointments() {
  const { user } = useAuth();
  const allowWrite = canWrite(user?.role);
  const isMedico = user?.role === 'ROLE_MEDICO';

  const [appointments, setAppointments] = useState<ConsultaListagem[]>([]);
  const [activeConsultasForReturn, setActiveConsultasForReturn] = useState<ConsultaListagem[]>([]);
  const [doctors, setDoctors] = useState<MedicoListagem[]>([]);
  const [patients, setPatients] = useState<PacienteListagem[]>([]);
  const [convenios, setConvenios] = useState<ConvenioListagem[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search, 300);
  const [statusFilter, setStatusFilter] = useState<StatusFilterOption>('ATIVAS');
  const [medicoFilter, setMedicoFilter] = useState('ALL');
  const [dataInicioFilter, setDataInicioFilter] = useState('');
  const [dataFimFilter, setDataFimFilter] = useState('');

  // Schedule modal
  const [isScheduleOpen, setIsScheduleOpen] = useState(false);
  const [scheduleForm, setScheduleForm] = useState<ScheduleForm>(emptyScheduleForm());
  const [scheduling, setScheduling] = useState(false);

  // Cancel modal
  const [isCancelOpen, setIsCancelOpen] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<ConsultaListagem | null>(null);
  const [cancelMotivo, setCancelMotivo] = useState<MotivoCancelamento | ''>('');
  const [canceladoPor, setCanceladoPor] = useState<CanceladoPor | 'none'>('none');
  const [cancelling, setCancelling] = useState(false);

  // Reschedule modal
  const [rescheduleTarget, setRescheduleTarget] = useState<ConsultaListagem | null>(null);
  const [rescheduleForm, setRescheduleForm] = useState<RescheduleForm>(emptyRescheduleForm());
  const [rescheduling, setRescheduling] = useState(false);

  // Absence modal
  const [absenceTarget, setAbsenceTarget] = useState<ConsultaListagem | null>(null);
  const [absenceObs, setAbsenceObs] = useState('');
  const [markingAbsence, setMarkingAbsence] = useState(false);

  // History modal
  const [historyTarget, setHistoryTarget] = useState<ConsultaListagem | null>(null);
  const [historyItems, setHistoryItems] = useState<HistoricoStatusConsulta[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const fetchCatalogs = async () => {
    if (!allowWrite) return;
    try {
      const [allActive, docs, pats, convs] = await Promise.all([
        consultasApi.list(0, 100),
        medicosApi.list(0, 100),
        pacientesApi.list(0, 100),
        conveniosApi.listAll(),
      ]);
      setActiveConsultasForReturn(allActive.content);
      setDoctors(docs.content);
      setPatients(pats.content);
      setConvenios(convs);
    } catch {
      toast.error('Erro ao carregar dados');
    }
  };

  const fetchAppointments = async (
    p = page,
    filter = statusFilter,
    q = debouncedSearch,
    medico = medicoFilter,
    dataInicio = dataInicioFilter,
    dataFim = dataFimFilter,
  ) => {
    if (dataInicio && dataFim && dataInicio > dataFim) {
      toast.error('A data inicial não pode ser posterior à data final.');
      return;
    }
    setLoading(true);
    try {
      const filters = buildListFilters(filter, q, allowWrite ? medico : 'ALL', dataInicio, dataFim);
      const appts = filters
        ? await consultasApi.list(p, 10, filters)
        : await consultasApi.list(p);
      setAppointments(appts.content);
      setTotalPages(appts.totalPages);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao carregar dados'));
    } finally {
      setLoading(false);
    }
  };

  const refreshAfterMutation = async (p = page) => {
    await Promise.all([
      fetchAppointments(p, statusFilter, debouncedSearch, medicoFilter, dataInicioFilter, dataFimFilter),
      allowWrite ? consultasApi.list(0, 100).then(r => setActiveConsultasForReturn(r.content)).catch(() => {}) : Promise.resolve(),
    ]);
  };

  useEffect(() => {
    void fetchCatalogs();
  }, [allowWrite]);

  useEffect(() => {
    void fetchAppointments(page, statusFilter, debouncedSearch, medicoFilter, dataInicioFilter, dataFimFilter);
  }, [page, statusFilter, debouncedSearch, medicoFilter, dataInicioFilter, dataFimFilter, allowWrite]);

  const handleStatusFilterChange = (value: string) => {
    setStatusFilter(value as StatusFilterOption);
    setPage(0);
  };

  const handleSearchChange = (value: string) => {
    setSearch(value);
    setPage(0);
  };

  const handleMedicoFilterChange = (value: string) => {
    setMedicoFilter(value);
    setPage(0);
  };

  const hasActiveFilters =
    Boolean(search.trim()) ||
    statusFilter !== 'ATIVAS' ||
    medicoFilter !== 'ALL' ||
    Boolean(dataInicioFilter) ||
    Boolean(dataFimFilter);

  const handleClearFilters = () => {
    setSearch('');
    setStatusFilter('ATIVAS');
    setMedicoFilter('ALL');
    setDataInicioFilter('');
    setDataFimFilter('');
    setPage(0);
  };

  const filteredAppointments = appointments;

  const selectedPatientIdNum = Number(scheduleForm.idPaciente);
  const activeReturnOriginIds = new Set(
    activeConsultasForReturn
      .map(c => c.consultaOrigemId)
      .filter((id): id is number => id != null)
  );
  const eligibleOriginConsultas = selectedPatientIdNum
    ? activeConsultasForReturn.filter(c => {
        if (c.tipo !== 'NORMAL') return false;
        if (c.status !== 'CONCLUIDA') return false;
        if (c.idPaciente != null && c.idPaciente !== selectedPatientIdNum) return false;
        if (activeReturnOriginIds.has(c.id)) return false;
        const diffDays = (Date.now() - new Date(c.dataHora).getTime()) / (24 * 60 * 60 * 1000);
        return diffDays <= 30;
      })
    : [];

  const isReturnSelected =
    Boolean(scheduleForm.consultaOrigemId) && scheduleForm.consultaOrigemId !== 'none';

  const handlePacienteChange = (patientId: string) => {
    setScheduleForm(prev => ({
      ...prev,
      idPaciente: patientId,
      consultaOrigemId: '',
      ...(prev.consultaOrigemId && prev.consultaOrigemId !== 'none' ? { idMedico: '' } : {}),
    }));
  };

  const handleConsultaOrigemChange = (value: string) => {
    if (!value || value === 'none') {
      setScheduleForm(prev => ({ ...prev, consultaOrigemId: '' }));
      return;
    }
    const origem = eligibleOriginConsultas.find(c => String(c.id) === value);
    setScheduleForm(prev => ({
      ...prev,
      consultaOrigemId: value,
      ...(origem?.idMedico != null ? { idMedico: String(origem.idMedico) } : {}),
    }));
  };

  const handleSchedule = async () => {
    if (!scheduleForm.idPaciente || !scheduleForm.dateInput || !scheduleForm.timeInput) {
      toast.error('Preencha paciente, data e hora');
      return;
    }
    const isoData = `${scheduleForm.dateInput}T${scheduleForm.timeInput}:00`;
    if (new Date(isoData) <= new Date()) {
      toast.error('A data deve ser futura');
      return;
    }
    setScheduling(true);
    try {
      const payload: ConsultaAgendamento = {
        idPaciente: Number(scheduleForm.idPaciente),
        data: isoData,
        ...(scheduleForm.idMedico && scheduleForm.idMedico !== 'none' && { idMedico: Number(scheduleForm.idMedico) }),
        ...(scheduleForm.prioridade && { prioridade: scheduleForm.prioridade }),
        ...(isReturnSelected && { consultaOrigemId: Number(scheduleForm.consultaOrigemId) }),
        ...(scheduleForm.convenioId && scheduleForm.convenioId !== 'none' && { convenioId: Number(scheduleForm.convenioId) }),
      };
      await consultasApi.schedule(payload);
      toast.success(isReturnSelected ? 'Retorno agendado com sucesso' : 'Consulta agendada com sucesso');
      setIsScheduleOpen(false);
      setScheduleForm(emptyScheduleForm());
      setPage(0);
      void refreshAfterMutation(0);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao agendar consulta'));
    } finally {
      setScheduling(false);
    }
  };

  const runDirectTransition = async (
    apt: ConsultaListagem,
    action: () => Promise<unknown>,
    successMsg: string,
    fallbackErr: string,
  ) => {
    setActionLoadingId(apt.id);
    try {
      await action();
      toast.success(successMsg);
      void refreshAfterMutation(page);
    } catch (err: any) {
      toast.error(extractApiError(err, fallbackErr));
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleOpenCancel = (apt: ConsultaListagem) => {
    setCancelTarget(apt);
    setCancelMotivo('');
    setCanceladoPor('none');
    setIsCancelOpen(true);
  };

  const handleCancel = async () => {
    if (!cancelTarget || !cancelMotivo) {
      toast.error('Selecione o motivo do cancelamento');
      return;
    }
    setCancelling(true);
    try {
      const payload: ConsultaCancelamento = {
        idConsulta: cancelTarget.id,
        motivo: cancelMotivo,
        ...(canceladoPor !== 'none' && { canceladoPor }),
      };
      await consultasApi.cancel(payload);
      toast.success('Consulta cancelada com sucesso');
      setIsCancelOpen(false);
      setCancelTarget(null);
      void refreshAfterMutation(page);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao cancelar consulta'));
    } finally {
      setCancelling(false);
    }
  };

  const handleOpenReschedule = (apt: ConsultaListagem) => {
    setRescheduleTarget(apt);
    setRescheduleForm({
      dateInput: '',
      timeInput: '',
      idMedico: apt.idMedico != null ? String(apt.idMedico) : '',
      solicitadoPor: 'CLINICA',
      motivo: '',
    });
  };

  const handleReschedule = async () => {
    if (!rescheduleTarget || !rescheduleForm.dateInput || !rescheduleForm.timeInput) {
      toast.error('Informe a nova data e horário');
      return;
    }
    const isoData = `${rescheduleForm.dateInput}T${rescheduleForm.timeInput}:00`;
    if (new Date(isoData) <= new Date()) {
      toast.error('A nova data deve ser futura');
      return;
    }
    setRescheduling(true);
    try {
      const payload: ConsultaReagendamento = {
        novaData: isoData,
        solicitadoPor: rescheduleForm.solicitadoPor,
        ...(rescheduleForm.idMedico && { idMedico: Number(rescheduleForm.idMedico) }),
        ...(rescheduleForm.motivo.trim() && { motivo: rescheduleForm.motivo.trim() }),
      };
      await consultasApi.reagendar(rescheduleTarget.id, payload);
      toast.success('Consulta reagendada com sucesso');
      setRescheduleTarget(null);
      void refreshAfterMutation(page);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao reagendar consulta'));
    } finally {
      setRescheduling(false);
    }
  };

  const handleOpenAbsence = (apt: ConsultaListagem) => {
    setAbsenceTarget(apt);
    setAbsenceObs('');
  };

  const handleMarkAbsence = async () => {
    if (!absenceTarget) return;
    setMarkingAbsence(true);
    try {
      await consultasApi.registrarAusencia(
        absenceTarget.id,
        absenceObs.trim() ? { observacao: absenceObs.trim() } : undefined,
      );
      toast.success('Ausência registrada com sucesso');
      setAbsenceTarget(null);
      void refreshAfterMutation(page);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao registrar ausência'));
    } finally {
      setMarkingAbsence(false);
    }
  };

  const handleOpenHistory = async (apt: ConsultaListagem) => {
    setHistoryTarget(apt);
    setHistoryItems([]);
    setLoadingHistory(true);
    try {
      const items = await consultasApi.historico(apt.id);
      setHistoryItems(items);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao carregar histórico da consulta'));
      setHistoryTarget(null);
    } finally {
      setLoadingHistory(false);
    }
  };

  const formatDateTime = (iso: string) => {
    const d = new Date(iso);
    return {
      date: d.toLocaleDateString('pt-BR'),
      time: d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
    };
  };

  const formatFullDateTime = (iso: string) =>
    new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <PageHeader
          icon={Calendar}
          title="Consultas"
          description="Agende, acompanhe o ciclo de atendimento e gerencie remarcações"
          actionLabel="Nova consulta"
          onAction={allowWrite ? () => { setScheduleForm(emptyScheduleForm()); setIsScheduleOpen(true); } : undefined}
        />

        <Card className="border-border">
          <div className="p-6 space-y-4">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center">
              <div className="flex-1">
                <SearchInput
                  placeholder="Buscar por paciente ou médico..."
                  value={search}
                  onChange={handleSearchChange}
                />
              </div>
              <div className="w-full lg:w-56">
                <Label className="sr-only" htmlFor="statusFilter">Filtrar por status</Label>
                <Select value={statusFilter} onValueChange={handleStatusFilterChange}>
                  <SelectTrigger id="statusFilter" aria-label="Filtrar por status">
                    <SelectValue placeholder="Filtrar por status" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ATIVAS">Ativas na agenda (padrão)</SelectItem>
                    <SelectItem value="TODAS">Todas (inclui encerradas)</SelectItem>
                    {allStatusConsultas.map(status => (
                      <SelectItem key={status} value={status}>
                        {statusConsultaLabel[status]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {allowWrite && (
                <div className="w-full lg:w-56">
                  <Label className="sr-only" htmlFor="medicoFilter">Filtrar por médico</Label>
                  <Select value={medicoFilter} onValueChange={handleMedicoFilterChange}>
                    <SelectTrigger id="medicoFilter" aria-label="Filtrar por médico">
                      <SelectValue placeholder="Todos os médicos" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">Todos os médicos</SelectItem>
                      {doctors.map(doc => (
                        <SelectItem key={doc.id} value={String(doc.id)}>
                          {doc.nome}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="w-full sm:w-44">
                <Label className="mb-1 block text-xs text-muted-foreground" htmlFor="dataInicioFilter">
                  Data inicial
                </Label>
                <Input
                  id="dataInicioFilter"
                  type="date"
                  value={dataInicioFilter}
                  onChange={e => { setDataInicioFilter(e.target.value); setPage(0); }}
                />
              </div>
              <div className="w-full sm:w-44">
                <Label className="mb-1 block text-xs text-muted-foreground" htmlFor="dataFimFilter">
                  Data final
                </Label>
                <Input
                  id="dataFimFilter"
                  type="date"
                  value={dataFimFilter}
                  onChange={e => { setDataFimFilter(e.target.value); setPage(0); }}
                />
              </div>
              {hasActiveFilters && (
                <Button type="button" variant="outline" size="sm" onClick={handleClearFilters}>
                  Limpar filtros
                </Button>
              )}
            </div>
          </div>
        </Card>

        {loading ? (
          <Card className="border-border p-12 text-center text-muted-foreground">
            Carregando...
          </Card>
        ) : filteredAppointments.length > 0 ? (
          <Card className="border-border overflow-hidden">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Paciente</TableHead>
                    <TableHead>Médico</TableHead>
                    <TableHead>Data</TableHead>
                    <TableHead>Hora</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Prioridade</TableHead>
                    <TableHead>Tipo</TableHead>
                    <TableHead>Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredAppointments.map((apt) => {
                    const { date, time } = formatDateTime(apt.dataHora);
                    const status: StatusConsulta = apt.status ?? 'AGENDADA';
                    const busy = actionLoadingId === apt.id;
                    const canConfirm = allowWrite && status === 'AGENDADA';
                    const canCheckIn = allowWrite && (status === 'AGENDADA' || status === 'CONFIRMADA');
                    const canRescheduleOrCancel =
                      allowWrite && (status === 'AGENDADA' || status === 'CONFIRMADA' || status === 'CHECK_IN');
                    const canMarkAbsence =
                      allowWrite && (status === 'AGENDADA' || status === 'CONFIRMADA') && isTodayOrPast(apt.dataHora);
                    const canStart = isMedico && status === 'CHECK_IN';
                    const canFinish = isMedico && status === 'EM_ATENDIMENTO';

                    return (
                      <TableRow key={apt.id}>
                        <TableCell className="font-medium">{apt.nomePaciente}</TableCell>
                        <TableCell>{apt.nomeMedico}</TableCell>
                        <TableCell>{date}</TableCell>
                        <TableCell>{time}</TableCell>
                        <TableCell>
                          <Badge variant={statusConsultaBadge[status]}>
                            {statusConsultaLabel[status]}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={prioridadeBadge[apt.prioridade]}>
                            {prioridadeLabel[apt.prioridade]}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <div>
                            {apt.tipo === 'RETORNO' && apt.consultaOrigemId
                              ? `Retorno (#${apt.consultaOrigemId})`
                              : tipoLabel[apt.tipo] ?? apt.tipo}
                          </div>
                          {apt.consultaReagendadaDeId && (
                            <p className="text-xs text-muted-foreground">
                              Reagendada de #{apt.consultaReagendadaDeId}
                            </p>
                          )}
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            {canConfirm && (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={busy}
                                aria-label="Confirmar consulta"
                                title="Confirmar consulta"
                                onClick={() =>
                                  runDirectTransition(
                                    apt,
                                    () => consultasApi.confirmar(apt.id),
                                    'Consulta confirmada com sucesso',
                                    'Erro ao confirmar consulta',
                                  )
                                }
                              >
                                <CheckCircle2 className="h-4 w-4 text-green-600" />
                              </Button>
                            )}
                            {canCheckIn && (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={busy}
                                aria-label="Registrar check-in"
                                title="Registrar check-in"
                                onClick={() =>
                                  runDirectTransition(
                                    apt,
                                    () => consultasApi.checkIn(apt.id),
                                    'Check-in registrado com sucesso',
                                    'Erro ao registrar check-in',
                                  )
                                }
                              >
                                <LogIn className="h-4 w-4 text-amber-600" />
                              </Button>
                            )}
                            {canStart && (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={busy}
                                aria-label="Iniciar atendimento"
                                title="Iniciar atendimento"
                                onClick={() =>
                                  runDirectTransition(
                                    apt,
                                    () => consultasApi.iniciar(apt.id),
                                    'Atendimento iniciado',
                                    'Erro ao iniciar atendimento',
                                  )
                                }
                              >
                                <Play className="h-4 w-4 text-blue-600" />
                              </Button>
                            )}
                            {canFinish && (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={busy}
                                aria-label="Concluir atendimento"
                                title="Concluir atendimento"
                                onClick={() =>
                                  runDirectTransition(
                                    apt,
                                    () => consultasApi.concluir(apt.id),
                                    'Atendimento concluído',
                                    'Erro ao concluir atendimento',
                                  )
                                }
                              >
                                <CheckCheck className="h-4 w-4 text-green-600" />
                              </Button>
                            )}
                            {canRescheduleOrCancel && (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={busy}
                                aria-label="Reagendar consulta"
                                title="Reagendar consulta"
                                onClick={() => handleOpenReschedule(apt)}
                              >
                                <CalendarClock className="h-4 w-4 text-primary" />
                              </Button>
                            )}
                            {canMarkAbsence && (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={busy}
                                aria-label="Registrar ausência"
                                title="Registrar ausência"
                                onClick={() => handleOpenAbsence(apt)}
                              >
                                <UserX className="h-4 w-4 text-orange-600" />
                              </Button>
                            )}
                            {canRescheduleOrCancel && (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={busy}
                                aria-label="Cancelar consulta"
                                onClick={() => handleOpenCancel(apt)}
                                title="Cancelar consulta"
                              >
                                <XCircle className="h-4 w-4 text-destructive" />
                              </Button>
                            )}
                            <Button
                              variant="ghost"
                              size="sm"
                              aria-label="Ver histórico"
                              title="Ver histórico de status"
                              onClick={() => handleOpenHistory(apt)}
                            >
                              <History className="h-4 w-4 text-muted-foreground" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>

            {totalPages > 1 && (
              <div className="flex items-center justify-between border-t px-6 py-3">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage(p => p - 1)}
                >
                  <ChevronLeft className="h-4 w-4 mr-1" />
                  Anterior
                </Button>
                <span className="text-sm text-muted-foreground">
                  Página {page + 1} de {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(p => p + 1)}
                >
                  Próxima
                  <ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            )}
          </Card>
        ) : (
          <EmptyState
            icon={Calendar}
            title="Nenhuma consulta encontrada"
            description={search || statusFilter !== 'ATIVAS' ? 'Nenhum resultado para os filtros aplicados.' : 'Agende a primeira consulta para começar.'}
            actionLabel={allowWrite ? 'Nova consulta' : undefined}
            onAction={allowWrite ? () => { setScheduleForm(emptyScheduleForm()); setIsScheduleOpen(true); } : undefined}
          />
        )}
      </div>

      {/* Schedule Modal */}
      <Dialog open={isScheduleOpen} onOpenChange={setIsScheduleOpen}>
        <DialogContent className="sm:max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Nova Consulta</DialogTitle>
            <DialogDescription>Preencha os dados para agendar uma consulta ou retorno</DialogDescription>
          </DialogHeader>

          <div className="space-y-8">
            <div>
              <h3 className="text-sm font-semibold mb-5 text-foreground">Dados da Consulta</h3>
              <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <Label className="mb-1.5 block" htmlFor="idPaciente">Paciente *</Label>
                  <Select
                    value={scheduleForm.idPaciente}
                    onValueChange={handlePacienteChange}
                  >
                    <SelectTrigger id="idPaciente">
                      <SelectValue placeholder="Selecione um paciente" />
                    </SelectTrigger>
                    <SelectContent>
                      {patients.map(pat => (
                        <SelectItem key={pat.id} value={String(pat.id)}>
                          {pat.nome} — CPF: {pat.cpf}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="sm:col-span-2">
                  <Label className="mb-1.5 block" htmlFor="consultaOrigemId">
                    Retorno de consulta concluída (opcional — em até 30 dias)
                  </Label>
                  <Select
                    value={scheduleForm.consultaOrigemId || 'none'}
                    onValueChange={handleConsultaOrigemChange}
                    disabled={!scheduleForm.idPaciente}
                  >
                    <SelectTrigger id="consultaOrigemId">
                      <SelectValue
                        placeholder={
                          !scheduleForm.idPaciente
                            ? 'Selecione o paciente primeiro'
                            : 'Consulta normal (sem vínculo de retorno)'
                        }
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">Consulta normal (sem vínculo de retorno)</SelectItem>
                      {eligibleOriginConsultas.map(origem => {
                        const { date, time } = formatDateTime(origem.dataHora);
                        return (
                          <SelectItem key={origem.id} value={String(origem.id)}>
                            Consulta #{origem.id} — {origem.nomeMedico} ({date} às {time})
                          </SelectItem>
                        );
                      })}
                    </SelectContent>
                  </Select>
                </div>

                <div className="sm:col-span-2">
                  <Label className="mb-1.5 block" htmlFor="idMedico">
                    Médico {isReturnSelected ? '(vinculado à consulta original)' : '(opcional — se não informado, o sistema escolhe)'}
                  </Label>
                  <Select
                    value={scheduleForm.idMedico}
                    onValueChange={v => setScheduleForm(p => ({ ...p, idMedico: v }))}
                    disabled={isReturnSelected}
                  >
                    <SelectTrigger id="idMedico">
                      <SelectValue placeholder="Qualquer médico disponível" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">Qualquer médico disponível</SelectItem>
                      {doctors.map(doc => (
                        <SelectItem key={doc.id} value={String(doc.id)}>
                          {doc.nome} — {doc.especialidade} (CRM {doc.crm})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {isReturnSelected && (
                    <p className="mt-1.5 text-xs text-muted-foreground">
                      Retorno vinculado automaticamente ao médico da consulta original.
                    </p>
                  )}
                </div>

                <div>
                  <Label className="mb-1.5 block" htmlFor="dateInput">Data *</Label>
                  <Input
                    id="dateInput"
                    type="date"
                    value={scheduleForm.dateInput}
                    onChange={e => setScheduleForm(p => ({ ...p, dateInput: e.target.value }))}
                  />
                </div>
                <div>
                  <Label className="mb-1.5 block" htmlFor="timeInput">Hora * (07:00 – 18:30)</Label>
                  <Input
                    id="timeInput"
                    type="time"
                    value={scheduleForm.timeInput}
                    onChange={e => setScheduleForm(p => ({ ...p, timeInput: e.target.value }))}
                    min="07:00"
                    max="18:30"
                  />
                </div>
                <div>
                  <Label className="mb-1.5 block" htmlFor="prioridade">Prioridade</Label>
                  <Select
                    value={scheduleForm.prioridade}
                    onValueChange={v => setScheduleForm(p => ({ ...p, prioridade: v as Prioridade }))}
                  >
                    <SelectTrigger id="prioridade">
                      <SelectValue placeholder="Rotina (padrão)" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ROTINA">Rotina</SelectItem>
                      <SelectItem value="PRIORITARIO">Prioritário</SelectItem>
                      <SelectItem value="URGENCIA">Urgência</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <Label className="mb-1.5 block" htmlFor="convenioId">Convênio</Label>
                  <Select
                    value={scheduleForm.convenioId}
                    onValueChange={v => setScheduleForm(p => ({ ...p, convenioId: v }))}
                  >
                    <SelectTrigger id="convenioId">
                      <SelectValue placeholder="Particular (sem convênio)" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">Particular</SelectItem>
                      {convenios.map(c => (
                        <SelectItem key={c.id} value={String(c.id)}>
                          {c.nome}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t">
              <Button variant="outline" onClick={() => setIsScheduleOpen(false)} disabled={scheduling}>
                Cancelar
              </Button>
              <Button onClick={handleSchedule} disabled={scheduling}>
                {scheduling ? 'Agendando...' : 'Agendar Consulta'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Cancel Modal */}
      <Dialog open={isCancelOpen} onOpenChange={setIsCancelOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Cancelar Consulta</DialogTitle>
            <DialogDescription>
              {cancelTarget && (
                <>Consulta de <strong>{cancelTarget.nomePaciente}</strong> com <strong>{cancelTarget.nomeMedico}</strong></>
              )}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <Label className="mb-1.5 block" htmlFor="motivo">Motivo do cancelamento *</Label>
              <Select
                value={cancelMotivo}
                onValueChange={v => setCancelMotivo(v as MotivoCancelamento)}
              >
                <SelectTrigger id="motivo">
                  <SelectValue placeholder="Selecione o motivo" />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(motivoLabel) as MotivoCancelamento[]).map(m => (
                    <SelectItem key={m} value={m}>
                      {motivoLabel[m]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="canceladoPor">Solicitado por (opcional)</Label>
              <Select
                value={canceladoPor}
                onValueChange={v => setCanceladoPor(v as CanceladoPor | 'none')}
              >
                <SelectTrigger id="canceladoPor">
                  <SelectValue placeholder="Não informado" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">Não informado</SelectItem>
                  {(Object.keys(canceladoPorLabel) as CanceladoPor[]).map(origem => (
                    <SelectItem key={origem} value={origem}>
                      {canceladoPorLabel[origem]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="mt-1.5 text-xs text-muted-foreground">
                Cancelamentos solicitados pelo paciente exigem antecedência mínima de 24h; cancelamentos pela clínica ou médico não possuem restrição de prazo.
              </p>
            </div>

            <div className="flex justify-end gap-3 pt-2 border-t">
              <Button variant="outline" onClick={() => setIsCancelOpen(false)} disabled={cancelling}>
                Voltar
              </Button>
              <Button variant="destructive" onClick={handleCancel} disabled={cancelling}>
                {cancelling ? 'Cancelando...' : 'Confirmar Cancelamento'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Reschedule Modal */}
      <Dialog open={!!rescheduleTarget} onOpenChange={open => { if (!open && !rescheduling) setRescheduleTarget(null); }}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Reagendar Consulta #{rescheduleTarget?.id}</DialogTitle>
            <DialogDescription>
              {rescheduleTarget && (
                <>Remarcar consulta de <strong>{rescheduleTarget.nomePaciente}</strong> com vínculo histórico</>
              )}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <Label className="mb-1.5 block" htmlFor="rescheduleDate">Nova data *</Label>
                <Input
                  id="rescheduleDate"
                  type="date"
                  value={rescheduleForm.dateInput}
                  onChange={e => setRescheduleForm(p => ({ ...p, dateInput: e.target.value }))}
                />
              </div>
              <div>
                <Label className="mb-1.5 block" htmlFor="rescheduleTime">Novo horário * (07:00 – 18:30)</Label>
                <Input
                  id="rescheduleTime"
                  type="time"
                  value={rescheduleForm.timeInput}
                  onChange={e => setRescheduleForm(p => ({ ...p, timeInput: e.target.value }))}
                  min="07:00"
                  max="18:30"
                />
              </div>
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="rescheduleMedico">
                Médico {rescheduleTarget?.tipo === 'RETORNO' ? '(fixo para retorno)' : ''}
              </Label>
              <Select
                value={rescheduleForm.idMedico}
                onValueChange={v => setRescheduleForm(p => ({ ...p, idMedico: v }))}
                disabled={rescheduleTarget?.tipo === 'RETORNO'}
              >
                <SelectTrigger id="rescheduleMedico">
                  <SelectValue placeholder="Manter médico atual" />
                </SelectTrigger>
                <SelectContent>
                  {doctors.map(doc => (
                    <SelectItem key={doc.id} value={String(doc.id)}>
                      {doc.nome} — {doc.especialidade}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="rescheduleSolicitadoPor">Solicitado por *</Label>
              <Select
                value={rescheduleForm.solicitadoPor}
                onValueChange={v => setRescheduleForm(p => ({ ...p, solicitadoPor: v as CanceladoPor }))}
              >
                <SelectTrigger id="rescheduleSolicitadoPor">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CLINICA">Clínica (sem restrição de 24h)</SelectItem>
                  <SelectItem value="PACIENTE">Paciente (exige 24h de antecedência)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="rescheduleMotivo">Motivo (opcional)</Label>
              <Input
                id="rescheduleMotivo"
                value={rescheduleForm.motivo}
                onChange={e => setRescheduleForm(p => ({ ...p, motivo: e.target.value }))}
                placeholder="Ex: Ajuste de agenda médica"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2 border-t">
              <Button variant="outline" onClick={() => setRescheduleTarget(null)} disabled={rescheduling}>
                Voltar
              </Button>
              <Button onClick={handleReschedule} disabled={rescheduling}>
                {rescheduling ? 'Reagendando...' : 'Confirmar Reagendamento'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Absence Modal */}
      <Dialog open={!!absenceTarget} onOpenChange={open => { if (!open && !markingAbsence) setAbsenceTarget(null); }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Registrar Ausência</DialogTitle>
            <DialogDescription>
              {absenceTarget && (
                <>Marcar falta para <strong>{absenceTarget.nomePaciente}</strong> na consulta #{absenceTarget.id}</>
              )}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <Label className="mb-1.5 block" htmlFor="absenceObs">Observação (opcional)</Label>
              <Input
                id="absenceObs"
                value={absenceObs}
                onChange={e => setAbsenceObs(e.target.value)}
                placeholder="Ex: Não compareceu após 20 minutos"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2 border-t">
              <Button variant="outline" onClick={() => setAbsenceTarget(null)} disabled={markingAbsence}>
                Voltar
              </Button>
              <Button variant="destructive" onClick={handleMarkAbsence} disabled={markingAbsence}>
                {markingAbsence ? 'Registrando...' : 'Confirmar Ausência'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* History Modal */}
      <Dialog open={!!historyTarget} onOpenChange={open => { if (!open) setHistoryTarget(null); }}>
        <DialogContent className="sm:max-w-xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Histórico da Consulta #{historyTarget?.id}</DialogTitle>
            <DialogDescription>
              {historyTarget && (
                <>Trilha de status de <strong>{historyTarget.nomePaciente}</strong> com <strong>{historyTarget.nomeMedico}</strong></>
              )}
            </DialogDescription>
          </DialogHeader>

          {loadingHistory ? (
            <div className="py-8 text-center text-muted-foreground">Carregando histórico...</div>
          ) : historyItems.length > 0 ? (
            <ol className="space-y-3">
              {historyItems.map(item => (
                <li key={item.id} className="rounded-lg border border-border bg-muted/20 p-3 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      {item.statusAnterior && (
                        <>
                          <Badge variant={statusConsultaBadge[item.statusAnterior]}>
                            {statusConsultaLabel[item.statusAnterior]}
                          </Badge>
                          <span className="text-muted-foreground">→</span>
                        </>
                      )}
                      <Badge variant={statusConsultaBadge[item.statusNovo]}>
                        {statusConsultaLabel[item.statusNovo]}
                      </Badge>
                    </div>
                    <span className="text-xs text-muted-foreground">
                      {formatFullDateTime(item.alteradoEm)}
                    </span>
                  </div>
                  {item.motivo && (
                    <p className="mt-2 text-foreground">{item.motivo}</p>
                  )}
                  <p className="mt-1 text-xs text-muted-foreground">
                    Responsável: {item.usuarioLogin || 'Sistema'}
                  </p>
                </li>
              ))}
            </ol>
          ) : (
            <div className="py-6 text-center text-sm text-muted-foreground">
              Nenhum registro de transição encontrado para esta consulta.
            </div>
          )}

          <div className="flex justify-end pt-2 border-t">
            <Button variant="outline" onClick={() => setHistoryTarget(null)}>
              Fechar
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </DashboardLayout>
  );
}
