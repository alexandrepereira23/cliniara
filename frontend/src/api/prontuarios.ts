import api from './axios'
import type {
  Page,
  ProntuarioListagem,
  ProntuarioDetalhamento,
  ProntuarioCadastro,
  ProntuarioAtualizacao,
} from '@/types/api'

export const prontuariosApi = {
  list: (page = 0, size = 10) =>
    api.get<Page<ProntuarioListagem>>('/prontuarios', { params: { page, size } }).then(r => r.data),

  listByPaciente: (pacienteId: number, page = 0, size = 10) =>
    api.get<Page<ProntuarioListagem>>(`/prontuarios/paciente/${pacienteId}`, { params: { page, size } }).then(r => r.data),

  get: (id: number) =>
    api.get<ProntuarioDetalhamento>(`/prontuarios/${id}`).then(r => r.data),

  create: (data: ProntuarioCadastro) =>
    api.post<ProntuarioDetalhamento>('/prontuarios', data).then(r => r.data),

  update: (data: ProntuarioAtualizacao) =>
    api.put<ProntuarioDetalhamento>('/prontuarios', data).then(r => r.data),

  remove: (id: number) =>
    api.delete(`/prontuarios/${id}`).then(r => r.data),
}
