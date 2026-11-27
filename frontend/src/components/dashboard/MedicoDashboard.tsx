import { useEffect, useState } from 'react';
import { Link } from 'wouter';
import {
  AlertCircle,
  BookOpen,
  Calendar,
  Clock,
  Plus,
  Stethoscope,
  UserCheck,
  Users,
} from 'lucide-react';
import { Badge } from '@/components/Badge';
import { DashboardLayout } from '@/components/DashboardLayout';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { dashboardApi } from '@/api/dashboard';
import { statusConsultaBadge, statusConsultaLabel } from '@/lib/consultaStatus';
import type {
  DashboardMedico,
  Prioridade,
  StatusConsulta,
  TipoConsulta,
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

const tipoConsultaLabel: Record<TipoConsulta, string> = {
  NORMAL: 'Normal',
  RETORNO: 'Retorno',
};

export function MedicoDashboard() {
  const [dashboard, setDashboard] = useState<DashboardMedico | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);

    dashboardApi
      .medico()
      .then(data => {
        if (!active) return;
        setDashboard(data);
      })
      .catch(() => {
        if (!active) return;
        setError(true);
        setDashboard(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

  const todayAppointments = dashboard?.agendaHoje ?? [];

  const statCards = [
    {
      title: 'Consultas Hoje',
      value: dashboard?.consultasHoje ?? 0,
      icon: Calendar,
      color: 'text-blue-600',
      bgColor: 'bg-blue-50',
    },
    {
      title: 'Fila de Espera (Check-in)',
      value: dashboard?.filaEsperaHoje ?? 0,
      icon: UserCheck,
      color: 'text-amber-600',
      bgColor: 'bg-amber-50',
    },
    {
      title: 'Urgências Hoje',
      value: dashboard?.urgenciasHoje ?? 0,
      icon: AlertCircle,
      color: 'text-red-600',
      bgColor: 'bg-red-50',
    },
    {
      title: 'Próximos 7 Dias',
      value: dashboard?.proximosSeteDias ?? 0,
      icon: Clock,
      color: 'text-cyan-600',
      bgColor: 'bg-cyan-50',
    },
    {
      title: 'Pacientes Vinculados',
      value: dashboard?.totalPacientesVinculados ?? 0,
      icon: Users,
      color: 'text-green-600',
      bgColor: 'bg-green-50',
    },
    {
      title: 'Prontuários',
      value: dashboard?.totalProntuarios ?? 0,
      icon: BookOpen,
      color: 'text-indigo-600',
      bgColor: 'bg-indigo-50',
    },
  ];

  return (
    <DashboardLayout>
      <div className="space-y-8">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Dashboard</h1>
          <p className="mt-2 text-muted-foreground">
            Bem-vindo ao sistema de gestão clínica Cliniara
          </p>
        </div>

        {error && (
          <Card role="alert" className="border-destructive/40 bg-destructive/5">
            <CardContent className="pt-6 text-sm text-destructive">
              Não foi possível carregar os indicadores da sua agenda no momento.
            </CardContent>
          </Card>
        )}

        {loading ? (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map(item => (
              <Card key={item} className="border-border animate-pulse">
                <CardContent className="pt-6">
                  <div className="h-16 rounded bg-muted" />
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {statCards.map(stat => {
              const Icon = stat.icon;
              return (
                <Card key={stat.title} className="border-border">
                  <CardContent className="pt-6">
                    <div className="flex items-start justify-between">
                      <div>
                        <p className="text-sm font-medium text-muted-foreground">{stat.title}</p>
                        <p className="mt-2 text-3xl font-bold text-foreground">{stat.value}</p>
                      </div>
                      <div className={`rounded-lg p-3 ${stat.bgColor}`}>
                        <Icon className={`h-6 w-6 ${stat.color}`} aria-hidden="true" />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}

        <Card className="border-border">
          <CardHeader>
            <CardTitle>Ações Rápidas</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Button variant="outline" asChild className="w-full justify-start">
                <Link href="/appointments">
                  <Stethoscope className="h-4 w-4" aria-hidden="true" />
                  Abrir Minha Agenda
                </Link>
              </Button>
              <Button variant="outline" asChild className="w-full justify-start">
                <Link href="/medical-records">
                  <Plus className="h-4 w-4" aria-hidden="true" />
                  Ver Prontuários
                </Link>
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card className="border-border">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Clock className="h-5 w-5" aria-hidden="true" />
              Agenda de Hoje
            </CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="py-8 text-center text-muted-foreground">Carregando...</div>
            ) : todayAppointments.length > 0 ? (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Horário</TableHead>
                      <TableHead>Paciente</TableHead>
                      <TableHead>Médico</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Prioridade</TableHead>
                      <TableHead>Tipo</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {todayAppointments.map(appointment => {
                      const status: StatusConsulta = appointment.status ?? 'AGENDADA';
                      return (
                        <TableRow key={appointment.id}>
                          <TableCell className="font-medium">{formatTime(appointment.dataHora)}</TableCell>
                          <TableCell>{appointment.nomePaciente}</TableCell>
                          <TableCell>{appointment.nomeMedico}</TableCell>
                          <TableCell>
                            <Badge variant={statusConsultaBadge[status]}>
                              {statusConsultaLabel[status]}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Badge variant={prioridadeBadge[appointment.prioridade]}>
                              {prioridadeLabel[appointment.prioridade]}
                            </Badge>
                          </TableCell>
                          <TableCell>{tipoConsultaLabel[appointment.tipo]}</TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            ) : (
              <div className="py-8 text-center">
                <p className="text-muted-foreground">Nenhuma consulta agendada para hoje</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
}
