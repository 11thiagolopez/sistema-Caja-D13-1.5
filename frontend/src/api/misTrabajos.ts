import { request } from './client'
import type { EstadoTrabajoTecnico, MiTrabajoResponse, NotaTrabajoResponse } from '../types/api'

export function getMisTrabajos(): Promise<MiTrabajoResponse[]> {
  return request<MiTrabajoResponse[]>('/api/mis-trabajos')
}

export function getMiTrabajo(id: number): Promise<MiTrabajoResponse> {
  return request<MiTrabajoResponse>(`/api/mis-trabajos/${id}`)
}

export function cambiarEstadoMiTrabajo(id: number, estado: EstadoTrabajoTecnico): Promise<MiTrabajoResponse> {
  return request<MiTrabajoResponse>(`/api/mis-trabajos/${id}/estado`, {
    method: 'POST',
    body: JSON.stringify({ estado }),
  })
}

export function agregarNotaMiTrabajo(id: number, texto: string): Promise<NotaTrabajoResponse> {
  return request<NotaTrabajoResponse>(`/api/mis-trabajos/${id}/notas`, {
    method: 'POST',
    body: JSON.stringify({ texto }),
  })
}
