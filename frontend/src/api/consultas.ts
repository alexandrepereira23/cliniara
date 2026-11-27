import api from './axios'
import type {
  Page,
  ConsultaAgendamento,
  ConsultaAusencia,
  ConsultaCancelamento,
  ConsultaDetalhamento,
  ConsultaListagem,
  ConsultaReagendamento,
  HistoricoStatusConsulta,
  StatusConsulta,
} from '@/types/api'

export interface ConsultaListFilters {
  status?: StatusConsulta
  incluirEncerradas?: boolean
}

export const consultasApi = {
  list: (page = 0, size = 10, filters?: ConsultaListFilters) =>
    api
      .get<Page<ConsultaListagem>>('/consultas', {
        params: {
          page,
          size,
          ...(filters?.status ? { status: filters.status } : {}),
          ...(filters?.incluirEncerradas ? { incluirEncerradas: true } : {}),
        },
      })
      .then(r => r.data),

  schedule: (data: ConsultaAgendamento) =>
    api.post<ConsultaDetalhamento>('/consultas', data).then(r => r.data),

  cancel: (data: ConsultaCancelamento) =>
    api.delete('/consultas', { data }).then(r => r.data),

  confirmar: (id: number) =>
    api.patch<ConsultaDetalhamento>(`/consultas/${id}/confirmar`).then(r => r.data),

  checkIn: (id: number) =>
    api.patch<ConsultaDetalhamento>(`/consultas/${id}/check-in`).then(r => r.data),

  iniciar: (id: number) =>
    api.patch<ConsultaDetalhamento>(`/consultas/${id}/iniciar`).then(r => r.data),

  concluir: (id: number) =>
    api.patch<ConsultaDetalhamento>(`/consultas/${id}/concluir`).then(r => r.data),

  registrarAusencia: (id: number, data?: ConsultaAusencia) =>
    api.patch<ConsultaDetalhamento>(`/consultas/${id}/ausencia`, data ?? {}).then(r => r.data),

  reagendar: (id: number, data: ConsultaReagendamento) =>
    api.post<ConsultaDetalhamento>(`/consultas/${id}/reagendar`, data).then(r => r.data),

  historico: (id: number) =>
    api.get<HistoricoStatusConsulta[]>(`/consultas/${id}/historico`).then(r => r.data),
}
