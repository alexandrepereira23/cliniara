import type { Role } from '@/types/auth'

export const canWrite = (role?: Role): boolean =>
  role === 'ROLE_FUNCIONARIO'

export const isAuditorOrGestor = (role?: Role): boolean =>
  role === 'ROLE_AUDITOR' || role === 'ROLE_GESTOR'

export const canInactivateProntuario = (role?: Role): boolean =>
  isAuditorOrGestor(role)

export const canReadPatientConvenios = (role?: Role): boolean =>
  role === 'ROLE_FUNCIONARIO' || isAuditorOrGestor(role)

export const canReadPatients = (role?: Role): boolean =>
  role !== 'ROLE_ADMIN'

export const canReadConsultas = (role?: Role): boolean =>
  role !== 'ROLE_ADMIN'

export const canReadEspecialidades = (role?: Role): boolean =>
  role !== 'ROLE_ADMIN'

export const canReadConvenios = (role?: Role): boolean =>
  role !== 'ROLE_ADMIN'
