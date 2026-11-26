export type AcaoAuditoria = 'VISUALIZOU' | 'CRIOU' | 'EDITOU'
export type RecursoAuditoria = 'PRONTUARIO' | 'PRESCRICAO' | 'ATESTADO'

export interface AuditoriaListagem {
  id: number
  prontuarioId: number | null
  recursoTipo?: RecursoAuditoria
  recursoId?: number | null
  loginUsuario: string
  acao: AcaoAuditoria
  dataHora: string
  ipOrigem?: string
}
