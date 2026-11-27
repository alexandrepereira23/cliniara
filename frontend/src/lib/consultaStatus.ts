import type { StatusConsulta } from '@/types/api'

export const statusConsultaLabel: Record<StatusConsulta, string> = {
  AGENDADA: 'Agendada',
  CONFIRMADA: 'Confirmada',
  CHECK_IN: 'Check-in',
  EM_ATENDIMENTO: 'Em atendimento',
  CONCLUIDA: 'Concluída',
  CANCELADA: 'Cancelada',
  AUSENTE: 'Ausente',
  REAGENDADA: 'Reagendada',
}

export const statusConsultaBadge: Record<
  StatusConsulta,
  'default' | 'success' | 'warning' | 'error' | 'info'
> = {
  AGENDADA: 'info',
  CONFIRMADA: 'success',
  CHECK_IN: 'warning',
  EM_ATENDIMENTO: 'info',
  CONCLUIDA: 'success',
  CANCELADA: 'error',
  AUSENTE: 'error',
  REAGENDADA: 'default',
}

export const allStatusConsultas: StatusConsulta[] = [
  'AGENDADA',
  'CONFIRMADA',
  'CHECK_IN',
  'EM_ATENDIMENTO',
  'CONCLUIDA',
  'CANCELADA',
  'AUSENTE',
  'REAGENDADA',
]
