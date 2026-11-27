import api from './axios'
import type {
  DashboardGestor,
  DashboardMedico,
  DashboardRecepcao,
} from '@/types/api'

export const dashboardApi = {
  recepcao: (data?: string) =>
    api
      .get<DashboardRecepcao>('/dashboard/recepcao', {
        params: data ? { data } : undefined,
      })
      .then(r => r.data),

  medico: (data?: string) =>
    api
      .get<DashboardMedico>('/dashboard/medico', {
        params: data ? { data } : undefined,
      })
      .then(r => r.data),

  gestor: (dias = 30) =>
    api
      .get<DashboardGestor>('/dashboard/gestor', { params: { dias } })
      .then(r => r.data),
}
