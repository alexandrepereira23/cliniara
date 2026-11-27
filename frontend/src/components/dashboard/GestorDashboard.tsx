import { useEffect, useState } from 'react'
import {
  Activity,
  AlertCircle,
  BarChart3,
  Calendar,
  CheckCircle2,
  RefreshCw,
  Stethoscope,
  UserX,
  Users,
  XCircle,
} from 'lucide-react'
import { DashboardLayout } from '@/components/DashboardLayout'
import { Badge } from '@/components/Badge'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { dashboardApi } from '@/api/dashboard'
import { statusConsultaBadge, statusConsultaLabel } from '@/lib/consultaStatus'
import type { DashboardGestor } from '@/types/api'

const PERIODO_OPTIONS = [
  { value: '7', label: 'Últimos 7 dias' },
  { value: '30', label: 'Últimos 30 dias' },
  { value: '90', label: 'Últimos 90 dias' },
]

export function GestorDashboard() {
  const [dias, setDias] = useState(30)
  const [data, setData] = useState<DashboardGestor | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState(false)

  const loadDashboard = async (periodDays = dias, isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setError(false)
    try {
      const result = await dashboardApi.gestor(periodDays)
      setData(result)
    } catch {
      setError(true)
      if (!isRefresh) setData(null)
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    void loadDashboard(dias)
  }, [dias])

  const maxStatusCount = data
    ? Math.max(1, ...data.porStatus.map(s => s.quantidade))
    : 1

  const kpiCards = data
    ? [
        {
          title: 'Consultas Hoje',
          value: data.consultasHoje,
          subtitle: 'Ativas na agenda do dia',
          icon: Calendar,
          color: 'text-blue-600',
          bg: 'bg-blue-50',
        },
        {
          title: `Total (${data.periodoDias}d)`,
          value: data.totalConsultasPeriodo,
          subtitle: 'Todos os status no período',
          icon: Activity,
          color: 'text-indigo-600',
          bg: 'bg-indigo-50',
        },
        {
          title: 'Concluídas',
          value: data.concluidasPeriodo,
          subtitle: 'Atendimentos finalizados',
          icon: CheckCircle2,
          color: 'text-green-600',
          bg: 'bg-green-50',
        },
        {
          title: 'Taxa de Cancelamento',
          value: `${data.taxaCancelamentoPercentual.toFixed(1)}%`,
          subtitle: `${data.canceladasPeriodo} cancelada(s) no período`,
          icon: XCircle,
          color: 'text-red-600',
          bg: 'bg-red-50',
        },
        {
          title: 'Taxa de Absenteísmo',
          value: `${data.taxaAbsenteismoPercentual.toFixed(1)}%`,
          subtitle: `${data.ausentesPeriodo} ausência(s) no período`,
          icon: UserX,
          color: 'text-amber-600',
          bg: 'bg-amber-50',
        },
        {
          title: 'Equipe e Base Ativa',
          value: `${data.medicosAtivos} / ${data.pacientesAtivos}`,
          subtitle: 'Médicos ativos / Pacientes ativos',
          icon: Users,
          color: 'text-teal-600',
          bg: 'bg-teal-50',
        },
      ]
    : []

  return (
    <DashboardLayout>
      <div className="space-y-8">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Dashboard Gerencial
            </h1>
            <p className="text-sm text-muted-foreground">
              Indicadores agregados de operação, produtividade e absenteísmo — sem exposição de dados clínicos individuais.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <Select value={String(dias)} onValueChange={v => setDias(Number(v))}>
              <SelectTrigger className="w-44" aria-label="Selecionar período de análise">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PERIODO_OPTIONS.map(opt => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              variant="outline"
              onClick={() => void loadDashboard(dias, true)}
              disabled={loading || refreshing}
              aria-label="Atualizar indicadores gerenciais"
            >
              <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} aria-hidden="true" />
              Atualizar
            </Button>
          </div>
        </header>

        {error && (
          <Card role="alert" className="border-destructive/40 bg-destructive/5">
            <CardContent className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between pt-6">
              <div className="flex gap-3">
                <AlertCircle className="h-5 w-5 text-destructive shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium text-foreground">Não foi possível carregar os indicadores gerenciais.</p>
                  <p className="text-sm text-muted-foreground">Tente atualizar novamente em instantes.</p>
                </div>
              </div>
              <Button variant="outline" onClick={() => void loadDashboard(dias, true)} disabled={refreshing}>
                Tentar novamente
              </Button>
            </CardContent>
          </Card>
        )}

        {loading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map(i => (
              <Card key={i} className="border-border animate-pulse">
                <CardContent className="pt-6">
                  <div className="h-20 rounded bg-muted" />
                </CardContent>
              </Card>
            ))}
          </div>
        ) : data ? (
          <>
            <section aria-label="Indicadores principais" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {kpiCards.map(card => {
                const Icon = card.icon
                return (
                  <Card key={card.title} className="border-border">
                    <CardContent className="pt-6">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <p className="text-sm font-medium text-muted-foreground">{card.title}</p>
                          <p className="mt-2 text-3xl font-bold text-foreground">{card.value}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{card.subtitle}</p>
                        </div>
                        <div className={`rounded-lg p-3 ${card.bg}`}>
                          <Icon className={`h-5 w-5 ${card.color}`} aria-hidden="true" />
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </section>

            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
              <Card className="border-border">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-lg">
                    <BarChart3 className="h-5 w-5 text-primary" aria-hidden="true" />
                    Distribuição por Status ({data.periodoDias} dias)
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  {data.porStatus.map(item => {
                    const widthPercent = Math.round((item.quantidade / maxStatusCount) * 100)
                    return (
                      <div key={item.status} className="space-y-1">
                        <div className="flex items-center justify-between text-sm">
                          <Badge variant={statusConsultaBadge[item.status]}>
                            {statusConsultaLabel[item.status]}
                          </Badge>
                          <span className="font-semibold text-foreground">{item.quantidade}</span>
                        </div>
                        <div className="h-2 w-full rounded-full bg-muted overflow-hidden">
                          <div
                            className="h-full rounded-full bg-primary transition-all"
                            style={{ width: `${item.quantidade > 0 ? Math.max(4, widthPercent) : 0}%` }}
                          />
                        </div>
                      </div>
                    )
                  })}
                </CardContent>
              </Card>

              <Card className="border-border">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-lg">
                    <Stethoscope className="h-5 w-5 text-primary" aria-hidden="true" />
                    Produção por Especialidade ({data.periodoDias} dias)
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {data.porEspecialidade.length > 0 ? (
                    <div className="overflow-x-auto">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>Especialidade</TableHead>
                            <TableHead className="text-right">Total</TableHead>
                            <TableHead className="text-right">Concluídas</TableHead>
                            <TableHead className="text-right">Canceladas</TableHead>
                            <TableHead className="text-right">Ausentes</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {data.porEspecialidade.map(esp => (
                            <TableRow key={esp.especialidade}>
                              <TableCell className="font-medium">{esp.especialidade}</TableCell>
                              <TableCell className="text-right font-semibold">{esp.total}</TableCell>
                              <TableCell className="text-right text-green-700">{esp.concluidas}</TableCell>
                              <TableCell className="text-right text-red-700">{esp.canceladas}</TableCell>
                              <TableCell className="text-right text-amber-700">{esp.ausentes}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  ) : (
                    <EmptyState
                      icon={Stethoscope}
                      title="Sem movimentação no período"
                      description="Nenhuma consulta encontrada para o intervalo selecionado."
                    />
                  )}
                </CardContent>
              </Card>
            </div>
          </>
        ) : null}
      </div>
    </DashboardLayout>
  )
}
