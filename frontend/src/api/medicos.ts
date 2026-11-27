import api from './axios'
import type {
  Page,
  MedicoCadastro,
  MedicoAtualizacao,
  MedicoListagem,
  MedicoDetalhamento,
} from '@/types/api'

export interface MedicoListFilters {
  q?: string
  especialidadeId?: number
}

export const medicosApi = {
  list: (page = 0, size = 10, filters?: MedicoListFilters) =>
    api
      .get<Page<MedicoListagem>>('/medicos', {
        params: {
          page,
          size,
          ...(filters?.q && filters.q.trim() ? { q: filters.q.trim() } : {}),
          ...(filters?.especialidadeId != null ? { especialidadeId: filters.especialidadeId } : {}),
        },
      })
      .then(r => r.data),

  get: (id: number) =>
    api.get<MedicoDetalhamento>(`/medicos/${id}`).then(r => r.data),

  create: (data: MedicoCadastro) =>
    api.post<MedicoDetalhamento>('/medicos', data).then(r => r.data),

  update: (data: MedicoAtualizacao) =>
    api.put<MedicoDetalhamento>('/medicos', data).then(r => r.data),

  remove: (id: number) =>
    api.delete(`/medicos/${id}`),
}
