import { useState, useEffect } from 'react';
import { DashboardLayout } from '@/components/DashboardLayout';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { ShieldCheck, ChevronLeft, ChevronRight, ArrowLeft, Search } from 'lucide-react';
import { toast } from 'sonner';
import { auditoriaApi } from '@/api/auditoria';
import { prontuariosApi } from '@/api/prontuarios';
import type { AuditoriaListagem, AcaoAuditoria, RecursoAuditoria } from '@/types/auditoria';
import type { ProntuarioListagem } from '@/types/api';

const ACAO_LABELS: Record<AcaoAuditoria, string> = {
  VISUALIZOU: 'Visualizou',
  CRIOU: 'Criou',
  EDITOU: 'Editou',
};

const ACAO_COLORS: Record<AcaoAuditoria, string> = {
  VISUALIZOU: 'bg-blue-100 text-blue-800',
  CRIOU: 'bg-green-100 text-green-800',
  EDITOU: 'bg-yellow-100 text-yellow-800',
};

const RECURSO_LABELS: Record<RecursoAuditoria, string> = {
  PRONTUARIO: 'Prontuário',
  PRESCRICAO: 'Prescrição',
  ATESTADO: 'Atestado',
};

const RECURSO_OPTIONS: RecursoAuditoria[] = ['PRONTUARIO', 'PRESCRICAO', 'ATESTADO'];

export default function Audit() {
  const [mode, setMode] = useState<'prontuario' | 'recurso'>('prontuario');

  // Modo 1: Por Prontuário
  const [prontuarios, setProntuarios] = useState<ProntuarioListagem[]>([]);
  const [loadingPron, setLoadingPron] = useState(false);
  const [pronPage, setPronPage] = useState(0);
  const [pronTotalPages, setPronTotalPages] = useState(0);

  const [selected, setSelected] = useState<ProntuarioListagem | null>(null);
  const [logs, setLogs] = useState<AuditoriaListagem[]>([]);
  const [loadingLogs, setLoadingLogs] = useState(false);
  const [logPage, setLogPage] = useState(0);
  const [logTotalPages, setLogTotalPages] = useState(0);
  const [recursoFilter, setRecursoFilter] = useState<RecursoAuditoria | 'TODOS'>('TODOS');

  // Modo 2: Por Recurso Clínico (GET /auditoria/{recursoTipo}/{recursoId})
  const [recursoTipo, setRecursoTipo] = useState<RecursoAuditoria>('PRONTUARIO');
  const [recursoIdInput, setRecursoIdInput] = useState('');
  const [queriedRecurso, setQueriedRecurso] = useState<{ tipo: RecursoAuditoria; id: number } | null>(null);
  const [recursoLogs, setRecursoLogs] = useState<AuditoriaListagem[]>([]);
  const [loadingRecursoLogs, setLoadingRecursoLogs] = useState(false);
  const [recursoPage, setRecursoPage] = useState(0);
  const [recursoTotalPages, setRecursoTotalPages] = useState(0);

  const fetchProntuarios = async (p = 0) => {
    setLoadingPron(true);
    try {
      const result = await prontuariosApi.list(p, 10);
      setProntuarios(result.content);
      setPronTotalPages(result.totalPages);
      setPronPage(p);
    } catch {
      toast.error('Erro ao carregar prontuários');
    } finally {
      setLoadingPron(false);
    }
  };

  const fetchLogs = async (prontuarioId: number, p = 0) => {
    setLoadingLogs(true);
    try {
      const result = await auditoriaApi.listByProntuario(prontuarioId, p);
      setLogs(result.content);
      setLogTotalPages(result.totalPages);
      setLogPage(p);
    } catch {
      toast.error('Erro ao carregar logs de auditoria');
    } finally {
      setLoadingLogs(false);
    }
  };

  const fetchLogsByRecurso = async (tipo: RecursoAuditoria, id: number, p = 0) => {
    setLoadingRecursoLogs(true);
    try {
      const result = await auditoriaApi.listByRecurso(tipo, id, p);
      setRecursoLogs(result.content);
      setRecursoTotalPages(result.totalPages);
      setRecursoPage(p);
      setQueriedRecurso({ tipo, id });
    } catch {
      toast.error('Erro ao carregar trilha do recurso clínico');
    } finally {
      setLoadingRecursoLogs(false);
    }
  };

  useEffect(() => {
    fetchProntuarios(0);
  }, []);

  const handleSelectProntuario = (pron: ProntuarioListagem) => {
    setSelected(pron);
    setRecursoFilter('TODOS');
    setLogs([]);
    fetchLogs(pron.id, 0);
  };

  const handleBack = () => {
    setSelected(null);
    setLogs([]);
    setRecursoFilter('TODOS');
  };

  const handleConsultarRecurso = () => {
    const idNum = Number(recursoIdInput);
    if (!Number.isInteger(idNum) || idNum <= 0) {
      toast.error('Informe um ID numérico válido para o recurso clínico');
      return;
    }
    fetchLogsByRecurso(recursoTipo, idNum, 0);
  };

  const handleInspectFromLog = (tipo: RecursoAuditoria, id: number) => {
    setMode('recurso');
    setRecursoTipo(tipo);
    setRecursoIdInput(String(id));
    fetchLogsByRecurso(tipo, id, 0);
  };

  const filteredLogs = recursoFilter === 'TODOS'
    ? logs
    : logs.filter(log => (log.recursoTipo ?? 'PRONTUARIO') === recursoFilter);

  const formatRecurso = (log: AuditoriaListagem) => {
    const tipo = log.recursoTipo ?? 'PRONTUARIO';
    const label = RECURSO_LABELS[tipo] ?? tipo;
    const id = log.recursoId ?? log.prontuarioId;
    return id != null ? `${label} #${id}` : label;
  };

  const renderLogsTable = (items: AuditoriaListagem[], allowInspectLink = false) => (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Recurso</TableHead>
            <TableHead>Ação</TableHead>
            <TableHead>Usuário</TableHead>
            <TableHead>Data e Hora</TableHead>
            <TableHead>IP Origem</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map(log => {
            const tipo = log.recursoTipo ?? 'PRONTUARIO';
            const recursoId = log.recursoId ?? log.prontuarioId;
            return (
              <TableRow key={log.id}>
                <TableCell className="font-medium">
                  {allowInspectLink && recursoId != null ? (
                    <button
                      type="button"
                      className="text-primary hover:underline text-left"
                      onClick={() => handleInspectFromLog(tipo, recursoId)}
                    >
                      {formatRecurso(log)}
                    </button>
                  ) : (
                    formatRecurso(log)
                  )}
                </TableCell>
                <TableCell>
                  <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${ACAO_COLORS[log.acao]}`}>
                    {ACAO_LABELS[log.acao]}
                  </span>
                </TableCell>
                <TableCell className="font-medium">{log.loginUsuario}</TableCell>
                <TableCell>
                  {new Date(log.dataHora).toLocaleString('pt-BR', {
                    day: '2-digit', month: '2-digit', year: 'numeric',
                    hour: '2-digit', minute: '2-digit', second: '2-digit',
                  })}
                </TableCell>
                <TableCell className="text-muted-foreground">{log.ipOrigem ?? '—'}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <PageHeader
          icon={ShieldCheck}
          title="Auditoria LGPD"
          description="Histórico de acessos a prontuários, prescrições e atestados — conformidade com a LGPD"
        />

        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant={mode === 'prontuario' ? 'default' : 'outline'}
            size="sm"
            onClick={() => setMode('prontuario')}
          >
            Por Prontuário
          </Button>
          <Button
            type="button"
            variant={mode === 'recurso' ? 'default' : 'outline'}
            size="sm"
            onClick={() => setMode('recurso')}
          >
            Por Recurso Clínico
          </Button>
        </div>

        {mode === 'prontuario' ? (
          !selected ? (
            <Card className="border-border overflow-hidden">
              <div className="px-6 py-4 border-b">
                <p className="text-sm font-medium text-foreground">Selecione um prontuário para auditar</p>
                <p className="text-xs text-muted-foreground mt-0.5">Clique em um prontuário para ver o histórico de acessos</p>
              </div>

              {loadingPron ? (
                <div className="p-12 text-center text-muted-foreground">Carregando...</div>
              ) : prontuarios.length === 0 ? (
                <div className="p-12">
                  <EmptyState
                    icon={ShieldCheck}
                    title="Nenhum prontuário encontrado"
                    description="Não há prontuários registrados no sistema."
                  />
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>#</TableHead>
                        <TableHead>Paciente</TableHead>
                        <TableHead>Diagnóstico</TableHead>
                        <TableHead>Data</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {prontuarios.map(pron => (
                        <TableRow
                          key={pron.id}
                          className="cursor-pointer hover:bg-muted/50"
                          onClick={() => handleSelectProntuario(pron)}
                        >
                          <TableCell className="font-medium text-muted-foreground">{pron.id}</TableCell>
                          <TableCell className="font-medium">{pron.nomePaciente}</TableCell>
                          <TableCell className="max-w-xs truncate">{pron.diagnostico}</TableCell>
                          <TableCell>
                            {new Date(pron.dataRegistro).toLocaleDateString('pt-BR')}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}

              {pronTotalPages > 1 && (
                <div className="flex items-center justify-between border-t px-6 py-3">
                  <Button variant="outline" size="sm" disabled={pronPage === 0} onClick={() => fetchProntuarios(pronPage - 1)}>
                    <ChevronLeft className="h-4 w-4 mr-1" />Anterior
                  </Button>
                  <span className="text-sm text-muted-foreground">Página {pronPage + 1} de {pronTotalPages}</span>
                  <Button variant="outline" size="sm" disabled={pronPage >= pronTotalPages - 1} onClick={() => fetchProntuarios(pronPage + 1)}>
                    Próxima<ChevronRight className="h-4 w-4 ml-1" />
                  </Button>
                </div>
              )}
            </Card>
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <Button variant="outline" size="sm" onClick={handleBack}>
                    <ArrowLeft className="h-4 w-4 mr-1" />Voltar
                  </Button>
                  <div>
                    <p className="text-sm font-semibold text-foreground">
                      Prontuário #{selected.id} — {selected.nomePaciente}
                    </p>
                    <p className="text-xs text-muted-foreground">{selected.diagnostico}</p>
                  </div>
                </div>

                <div className="w-48">
                  <Select
                    value={recursoFilter}
                    onValueChange={v => setRecursoFilter(v as RecursoAuditoria | 'TODOS')}
                  >
                    <SelectTrigger aria-label="Filtrar por tipo de recurso">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="TODOS">Todos os recursos</SelectItem>
                      {RECURSO_OPTIONS.map(opt => (
                        <SelectItem key={opt} value={opt}>
                          {RECURSO_LABELS[opt]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <Card className="border-border overflow-hidden">
                <div className="px-6 py-4 border-b">
                  <p className="text-sm font-medium text-foreground">Histórico de acessos</p>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Registros de quem visualizou, criou ou editou este prontuário e seus documentos vinculados
                  </p>
                </div>

                {loadingLogs ? (
                  <div className="p-12 text-center text-muted-foreground">Carregando...</div>
                ) : filteredLogs.length === 0 ? (
                  <div className="p-12">
                    <EmptyState
                      icon={ShieldCheck}
                      title="Nenhum log encontrado"
                      description={
                        recursoFilter === 'TODOS'
                          ? 'Este prontuário não possui registros de auditoria.'
                          : `Nenhum registro de ${RECURSO_LABELS[recursoFilter].toLowerCase()} encontrado nesta página.`
                      }
                    />
                  </div>
                ) : (
                  renderLogsTable(filteredLogs, true)
                )}

                {logTotalPages > 1 && (
                  <div className="flex items-center justify-between border-t px-6 py-3">
                    <Button variant="outline" size="sm" disabled={logPage === 0} onClick={() => fetchLogs(selected.id, logPage - 1)}>
                      <ChevronLeft className="h-4 w-4 mr-1" />Anterior
                    </Button>
                    <span className="text-sm text-muted-foreground">Página {logPage + 1} de {logTotalPages}</span>
                    <Button variant="outline" size="sm" disabled={logPage >= logTotalPages - 1} onClick={() => fetchLogs(selected.id, logPage + 1)}>
                      Próxima<ChevronRight className="h-4 w-4 ml-1" />
                    </Button>
                  </div>
                )}
              </Card>
            </>
          )
        ) : (
          <>
            <Card className="border-border p-6">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 items-end">
                <div>
                  <Label className="mb-1.5 block" htmlFor="recursoTipo">Tipo de recurso</Label>
                  <Select
                    value={recursoTipo}
                    onValueChange={v => setRecursoTipo(v as RecursoAuditoria)}
                  >
                    <SelectTrigger id="recursoTipo">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {RECURSO_OPTIONS.map(opt => (
                        <SelectItem key={opt} value={opt}>
                          {RECURSO_LABELS[opt]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-1.5 block" htmlFor="recursoId">ID do recurso</Label>
                  <Input
                    id="recursoId"
                    type="number"
                    min={1}
                    value={recursoIdInput}
                    onChange={e => setRecursoIdInput(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleConsultarRecurso();
                      }
                    }}
                    placeholder="Ex: 1"
                  />
                </div>

                <div>
                  <Button
                    type="button"
                    className="w-full sm:w-auto"
                    onClick={handleConsultarRecurso}
                    disabled={loadingRecursoLogs}
                  >
                    <Search className="h-4 w-4 mr-1.5" />
                    {loadingRecursoLogs ? 'Consultando...' : 'Consultar trilha'}
                  </Button>
                </div>
              </div>
            </Card>

            <Card className="border-border overflow-hidden">
              <div className="px-6 py-4 border-b">
                <p className="text-sm font-medium text-foreground">
                  {queriedRecurso
                    ? `Trilha de auditoria — ${RECURSO_LABELS[queriedRecurso.tipo]} #${queriedRecurso.id}`
                    : 'Consulta por recurso clínico'}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Consulte logs LGPD diretamente por Prontuário, Prescrição ou Atestado
                </p>
              </div>

              {loadingRecursoLogs ? (
                <div className="p-12 text-center text-muted-foreground">Carregando...</div>
              ) : !queriedRecurso ? (
                <div className="p-12">
                  <EmptyState
                    icon={ShieldCheck}
                    title="Informe um recurso para consultar"
                    description="Escolha o tipo de recurso clínico, informe o ID e clique em Consultar trilha."
                  />
                </div>
              ) : recursoLogs.length === 0 ? (
                <div className="p-12">
                  <EmptyState
                    icon={ShieldCheck}
                    title="Nenhum log encontrado"
                    description={`Não há registros de auditoria para ${RECURSO_LABELS[queriedRecurso.tipo]} #${queriedRecurso.id}.`}
                  />
                </div>
              ) : (
                renderLogsTable(recursoLogs, false)
              )}

              {queriedRecurso && recursoTotalPages > 1 && (
                <div className="flex items-center justify-between border-t px-6 py-3">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={recursoPage === 0}
                    onClick={() => fetchLogsByRecurso(queriedRecurso.tipo, queriedRecurso.id, recursoPage - 1)}
                  >
                    <ChevronLeft className="h-4 w-4 mr-1" />Anterior
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    Página {recursoPage + 1} de {recursoTotalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={recursoPage >= recursoTotalPages - 1}
                    onClick={() => fetchLogsByRecurso(queriedRecurso.tipo, queriedRecurso.id, recursoPage + 1)}
                  >
                    Próxima<ChevronRight className="h-4 w-4 ml-1" />
                  </Button>
                </div>
              )}
            </Card>
          </>
        )}
      </div>
    </DashboardLayout>
  );
}
