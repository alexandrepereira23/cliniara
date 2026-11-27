import { useState, useEffect } from 'react';
import { DashboardLayout } from '@/components/DashboardLayout';
import { PageHeader } from '@/components/PageHeader';
import { SearchInput } from '@/components/SearchInput';
import { EmptyState } from '@/components/EmptyState';
import { Badge } from '@/components/Badge';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { FileText, ChevronLeft, ChevronRight, Eye, Edit, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { prontuariosApi } from '@/api/prontuarios';
import { consultasApi } from '@/api/consultas';
import { useAuth } from '@/hooks/useAuth';
import { canInactivateProntuario } from '@/lib/rbac';
import { extractApiError } from '@/lib/utils';
import { statusConsultaLabel } from '@/lib/consultaStatus';
import type { ProntuarioListagem, ProntuarioDetalhamento, ConsultaListagem } from '@/types/api';

const EDIT_WINDOW_MS = 24 * 60 * 60 * 1000;

export function isWithinEditWindow(dataRegistro: string): boolean {
  const registeredAt = new Date(dataRegistro).getTime();
  if (Number.isNaN(registeredAt)) return false;
  return Date.now() - registeredAt < EDIT_WINDOW_MS;
}

export default function MedicalRecords() {
  const { user } = useAuth();
  const isMedico = user?.role === 'ROLE_MEDICO';
  const allowInactivate = canInactivateProntuario(user?.role);

  const [records, setRecords] = useState<ProntuarioListagem[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState('');

  // Modal criar
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [consultas, setConsultas] = useState<ConsultaListagem[]>([]);
  const [createForm, setCreateForm] = useState({
    consultaId: '', anamnese: '', diagnostico: '', cid10: '', observacoes: '',
  });
  const [saving, setSaving] = useState(false);

  // Modal detalhar
  const [detail, setDetail] = useState<ProntuarioDetalhamento | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  // Modal editar (janela de 24h)
  const [editingRecord, setEditingRecord] = useState<ProntuarioDetalhamento | null>(null);
  const [editForm, setEditForm] = useState({
    anamnese: '', diagnostico: '', cid10: '', observacoes: '',
  });
  const [updating, setUpdating] = useState(false);

  // Modal confirmar inativação (ROLE_AUDITOR / ROLE_GESTOR)
  const [inactivateTarget, setInactivateTarget] = useState<ProntuarioListagem | null>(null);
  const [inactivating, setInactivating] = useState(false);

  const fetchRecords = (p = 0) => {
    setLoading(true);
    prontuariosApi.list(p)
      .then(r => { setRecords(r.content); setTotalPages(r.totalPages); })
      .catch(() => toast.error('Erro ao carregar prontuários'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchRecords(page); }, [page]);

  const handleOpenCreate = async () => {
    setCreateForm({ consultaId: '', anamnese: '', diagnostico: '', cid10: '', observacoes: '' });
    setIsCreateOpen(true);
    try {
      const r = await consultasApi.list(0, 100);
      setConsultas(r.content);
    } catch {
      toast.error('Erro ao carregar consultas');
    }
  };

  const handleCreate = async () => {
    if (!createForm.consultaId || !createForm.anamnese.trim() || !createForm.diagnostico.trim()) {
      toast.error('Preencha consulta, anamnese e diagnóstico');
      return;
    }
    setSaving(true);
    try {
      await prontuariosApi.create({
        consultaId: Number(createForm.consultaId),
        anamnese: createForm.anamnese,
        diagnostico: createForm.diagnostico,
        ...(createForm.cid10 && { cid10: createForm.cid10 }),
        ...(createForm.observacoes && { observacoes: createForm.observacoes }),
      });
      toast.success('Prontuário criado com sucesso');
      setIsCreateOpen(false);
      setPage(0);
      fetchRecords(0);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao criar prontuário'));
    } finally {
      setSaving(false);
    }
  };

  const handleViewDetail = async (id: number) => {
    setDetail(null);
    setLoadingDetail(true);
    try {
      const data = await prontuariosApi.get(id);
      setDetail(data);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao carregar detalhes do prontuário'));
    } finally {
      setLoadingDetail(false);
    }
  };

  const openEditWithDetail = (data: ProntuarioDetalhamento) => {
    setEditingRecord(data);
    setEditForm({
      anamnese: data.anamnese ?? '',
      diagnostico: data.diagnostico ?? '',
      cid10: data.cid10 ?? '',
      observacoes: data.observacoes ?? '',
    });
  };

  const handleOpenEdit = async (record: ProntuarioListagem) => {
    if (!isWithinEditWindow(record.dataRegistro)) {
      toast.error('Prontuário não pode ser editado após 24 horas do registro');
      return;
    }
    try {
      const data = await prontuariosApi.get(record.id);
      openEditWithDetail(data);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao carregar prontuário para edição'));
    }
  };

  const handleUpdate = async () => {
    if (!editingRecord) return;
    if (!editForm.anamnese.trim() || !editForm.diagnostico.trim()) {
      toast.error('Preencha anamnese e diagnóstico');
      return;
    }
    setUpdating(true);
    try {
      await prontuariosApi.update({
        id: editingRecord.id,
        anamnese: editForm.anamnese.trim(),
        diagnostico: editForm.diagnostico.trim(),
        ...(editForm.cid10.trim() && { cid10: editForm.cid10.trim() }),
        ...(editForm.observacoes.trim() && { observacoes: editForm.observacoes.trim() }),
      });
      toast.success('Prontuário atualizado com sucesso');
      setEditingRecord(null);
      fetchRecords(page);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao atualizar prontuário'));
    } finally {
      setUpdating(false);
    }
  };

  const handleInactivate = async () => {
    if (!inactivateTarget) return;
    setInactivating(true);
    try {
      await prontuariosApi.remove(inactivateTarget.id);
      toast.success('Prontuário inativado com sucesso');
      setInactivateTarget(null);
      fetchRecords(page);
    } catch (err: any) {
      toast.error(extractApiError(err, 'Erro ao inativar prontuário'));
    } finally {
      setInactivating(false);
    }
  };

  const filtered = search.trim()
    ? records.filter(r =>
        [r.nomePaciente, r.diagnostico]
          .some(v => v?.toLowerCase().includes(search.toLowerCase()))
      )
    : records;

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

  const consultasElegiveis = consultas.filter(
    c => c.status === 'EM_ATENDIMENTO' || c.status === 'CONCLUIDA'
  );

  const formatConsulta = (c: ConsultaListagem) =>
    `${c.nomePaciente} — ${new Date(c.dataHora).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })} (${statusConsultaLabel[c.status] ?? c.status})`;

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <PageHeader
          icon={FileText}
          title="Prontuários"
          description="Consulte e acompanhe o histórico clínico dos pacientes"
          actionLabel={isMedico ? 'Novo Prontuário' : undefined}
          onAction={isMedico ? handleOpenCreate : undefined}
        />

        <Card className="border-border">
          <div className="p-6">
            <SearchInput
              placeholder="Buscar por paciente ou diagnóstico..."
              value={search}
              onChange={setSearch}
            />
          </div>
        </Card>

        {loading ? (
          <Card className="border-border p-12 text-center text-muted-foreground">Carregando...</Card>
        ) : filtered.length > 0 ? (
          <Card className="border-border overflow-hidden">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Paciente</TableHead>
                    <TableHead>Diagnóstico</TableHead>
                    <TableHead>Data do Registro</TableHead>
                    <TableHead>Consulta #</TableHead>
                    <TableHead className="text-right">Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.map(record => {
                    const editable = isWithinEditWindow(record.dataRegistro);
                    return (
                      <TableRow key={record.id}>
                        <TableCell className="font-medium">{record.nomePaciente}</TableCell>
                        <TableCell className="text-sm text-muted-foreground max-w-xs truncate">
                          {record.diagnostico}
                        </TableCell>
                        <TableCell>{formatDate(record.dataRegistro)}</TableCell>
                        <TableCell className="text-sm text-muted-foreground">#{record.consultaId}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="sm"
                              aria-label="Detalhar prontuário"
                              title="Visualizar prontuário"
                              onClick={() => handleViewDetail(record.id)}
                            >
                              <Eye className="h-4 w-4 text-primary" />
                            </Button>
                            {isMedico && (
                              <Button
                                variant="ghost"
                                size="sm"
                                aria-label="Editar prontuário"
                                disabled={!editable}
                                title={editable ? 'Editar prontuário (janela de 24h)' : 'Janela de edição de 24h expirada'}
                                onClick={() => handleOpenEdit(record)}
                              >
                                <Edit className="h-4 w-4" />
                              </Button>
                            )}
                            {allowInactivate && (
                              <Button
                                variant="ghost"
                                size="sm"
                                aria-label="Inativar prontuário"
                                title="Inativar prontuário"
                                onClick={() => setInactivateTarget(record)}
                              >
                                <Trash2 className="h-4 w-4 text-destructive" />
                              </Button>
                            )}
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
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                  <ChevronLeft className="h-4 w-4 mr-1" />Anterior
                </Button>
                <span className="text-sm text-muted-foreground">Página {page + 1} de {totalPages}</span>
                <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                  Próxima<ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            )}
          </Card>
        ) : (
          <EmptyState
            icon={FileText}
            title="Nenhum prontuário encontrado"
            description={search ? 'Nenhum resultado para a busca.' : 'Prontuários são criados pelos médicos ao registrar uma consulta.'}
          />
        )}
      </div>

      {/* Modal Criar Prontuário */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Novo Prontuário</DialogTitle>
            <DialogDescription>Registre o prontuário da consulta realizada</DialogDescription>
          </DialogHeader>

          <div className="space-y-5">
            <div>
              <Label className="mb-1.5 block" htmlFor="consultaId">Consulta *</Label>
              <Select
                value={createForm.consultaId}
                onValueChange={v => setCreateForm(p => ({ ...p, consultaId: v }))}
              >
                <SelectTrigger id="consultaId">
                  <SelectValue placeholder="Selecione a consulta" />
                </SelectTrigger>
                <SelectContent>
                  {consultasElegiveis.map(c => (
                    <SelectItem key={c.id} value={String(c.id)}>
                      {formatConsulta(c)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {consultasElegiveis.length === 0 && (
                <p className="mt-1.5 text-xs text-muted-foreground">
                  Nenhuma consulta em atendimento ou concluída disponível. Inicie o atendimento na tela de Consultas antes de criar o prontuário.
                </p>
              )}
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="diagnostico">Diagnóstico *</Label>
              <Input
                id="diagnostico"
                value={createForm.diagnostico}
                onChange={e => setCreateForm(p => ({ ...p, diagnostico: e.target.value }))}
                placeholder="Ex: Sinusite aguda"
              />
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="cid10">CID-10</Label>
              <Input
                id="cid10"
                value={createForm.cid10}
                onChange={e => setCreateForm(p => ({ ...p, cid10: e.target.value }))}
                placeholder="Ex: J01.9"
              />
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="anamnese">Anamnese *</Label>
              <textarea
                id="anamnese"
                value={createForm.anamnese}
                onChange={e => setCreateForm(p => ({ ...p, anamnese: e.target.value }))}
                placeholder="Queixa principal e histórico relatado pelo paciente..."
                rows={4}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none"
              />
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="observacoes">Observações / Conduta</Label>
              <textarea
                id="observacoes"
                value={createForm.observacoes}
                onChange={e => setCreateForm(p => ({ ...p, observacoes: e.target.value }))}
                placeholder="Recomendações, conduta médica, retorno..."
                rows={3}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2 border-t">
              <Button variant="outline" onClick={() => setIsCreateOpen(false)} disabled={saving}>Cancelar</Button>
              <Button onClick={handleCreate} disabled={saving}>
                {saving ? 'Salvando...' : 'Criar Prontuário'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Modal Detalhamento do Prontuário */}
      <Dialog
        open={!!detail || loadingDetail}
        onOpenChange={open => { if (!open) { setDetail(null); setLoadingDetail(false); } }}
      >
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {detail ? `Prontuário #${detail.id}` : 'Detalhes do Prontuário'}
            </DialogTitle>
            <DialogDescription>
              {detail
                ? `Consulta #${detail.consultaId} · Registrado em ${formatDate(detail.dataRegistro)}`
                : 'Carregando informações clínicas...'}
            </DialogDescription>
          </DialogHeader>

          {loadingDetail && !detail ? (
            <div className="py-8 text-center text-muted-foreground">Carregando...</div>
          ) : detail ? (
            <div className="space-y-5 text-sm">
              <div className="grid grid-cols-1 gap-4 rounded-md border border-border bg-muted/20 p-4 sm:grid-cols-2">
                <div>
                  <p className="text-xs text-muted-foreground">Paciente</p>
                  <p className="font-medium text-foreground">{detail.nomePaciente}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Médico Responsável</p>
                  <p className="font-medium text-foreground">{detail.nomeMedico}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">CID-10</p>
                  <p className="font-medium text-foreground">{detail.cid10 || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Janela de edição</p>
                  <Badge variant={isWithinEditWindow(detail.dataRegistro) ? 'success' : 'default'}>
                    {isWithinEditWindow(detail.dataRegistro) ? 'Editável (até 24h)' : 'Janela encerrada'}
                  </Badge>
                </div>
              </div>

              <div>
                <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-1">Diagnóstico</p>
                <p className="rounded-md border border-border p-3 text-foreground whitespace-pre-wrap">
                  {detail.diagnostico}
                </p>
              </div>

              <div>
                <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-1">Anamnese</p>
                <p className="rounded-md border border-border p-3 text-foreground whitespace-pre-wrap">
                  {detail.anamnese}
                </p>
              </div>

              <div>
                <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-1">Observações / Conduta</p>
                <p className="rounded-md border border-border p-3 text-foreground whitespace-pre-wrap">
                  {detail.observacoes || 'Sem observações registradas.'}
                </p>
              </div>

              <div className="flex justify-end gap-3 pt-2 border-t">
                {isMedico && isWithinEditWindow(detail.dataRegistro) && (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      const current = detail;
                      setDetail(null);
                      openEditWithDetail(current);
                    }}
                  >
                    <Edit className="h-4 w-4 mr-1.5" />
                    Editar
                  </Button>
                )}
                <Button variant="outline" onClick={() => setDetail(null)}>Fechar</Button>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      {/* Modal Editar Prontuário (janela de 24h) */}
      <Dialog
        open={!!editingRecord}
        onOpenChange={open => { if (!open && !updating) setEditingRecord(null); }}
      >
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Editar Prontuário #{editingRecord?.id}</DialogTitle>
            <DialogDescription>
              {editingRecord
                ? `Paciente: ${editingRecord.nomePaciente} · Edição permitida dentro da janela de 24h após o registro`
                : ''}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-5">
            <div>
              <Label className="mb-1.5 block" htmlFor="edit-diagnostico">Diagnóstico *</Label>
              <Input
                id="edit-diagnostico"
                value={editForm.diagnostico}
                onChange={e => setEditForm(p => ({ ...p, diagnostico: e.target.value }))}
              />
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="edit-cid10">CID-10</Label>
              <Input
                id="edit-cid10"
                value={editForm.cid10}
                onChange={e => setEditForm(p => ({ ...p, cid10: e.target.value }))}
              />
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="edit-anamnese">Anamnese *</Label>
              <textarea
                id="edit-anamnese"
                value={editForm.anamnese}
                onChange={e => setEditForm(p => ({ ...p, anamnese: e.target.value }))}
                rows={4}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none"
              />
            </div>

            <div>
              <Label className="mb-1.5 block" htmlFor="edit-observacoes">Observações / Conduta</Label>
              <textarea
                id="edit-observacoes"
                value={editForm.observacoes}
                onChange={e => setEditForm(p => ({ ...p, observacoes: e.target.value }))}
                rows={3}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2 border-t">
              <Button variant="outline" onClick={() => setEditingRecord(null)} disabled={updating}>Cancelar</Button>
              <Button onClick={handleUpdate} disabled={updating}>
                {updating ? 'Salvando...' : 'Salvar Alterações'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Modal Confirmar Inativação Administrativa (AUDITOR / GESTOR) */}
      <Dialog
        open={!!inactivateTarget}
        onOpenChange={open => { if (!open && !inactivating) setInactivateTarget(null); }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Inativar Prontuário #{inactivateTarget?.id}</DialogTitle>
            <DialogDescription>
              {inactivateTarget && (
                <>
                  Confirma a inativação lógica do prontuário de <strong>{inactivateTarget.nomePaciente}</strong>?
                  Esta ação é registrada na trilha de auditoria LGPD e oculta o prontuário das listagens ativas.
                </>
              )}
            </DialogDescription>
          </DialogHeader>

          <div className="flex justify-end gap-3 pt-4 border-t">
            <Button variant="outline" onClick={() => setInactivateTarget(null)} disabled={inactivating}>
              Cancelar
            </Button>
            <Button variant="destructive" onClick={handleInactivate} disabled={inactivating}>
              {inactivating ? 'Inativando...' : 'Confirmar Inativação'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </DashboardLayout>
  );
}
