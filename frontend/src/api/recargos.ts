import { request } from './client'
import type { RecargoResponse } from '../types/api'

export function getRecargos(): Promise<RecargoResponse[]> {
  return request<RecargoResponse[]>('/api/recargos')
}

// Solo ADMIN.
export function actualizarRecargo(medioPago: RecargoResponse['medioPago'], porcentaje: number): Promise<RecargoResponse> {
  return request<RecargoResponse>(`/api/recargos/${medioPago}`, {
    method: 'PUT',
    body: JSON.stringify({ porcentaje }),
  })
}
