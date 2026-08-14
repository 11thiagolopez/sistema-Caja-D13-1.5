import { request } from './client'
import type { CotizacionManualRequest, CotizacionResponse } from '../types/api'

// null cuando todavía no hay cotización cargada para HOY (backend responde 204 sin cuerpo).
export function getCotizacionActual(): Promise<CotizacionResponse | null> {
  return request<CotizacionResponse | null>('/api/cotizacion/actual')
}

export function cargarCotizacion(): Promise<CotizacionResponse> {
  return request<CotizacionResponse>('/api/cotizacion/cargar', { method: 'POST' })
}

export function cargarCotizacionManual(req: CotizacionManualRequest): Promise<CotizacionResponse> {
  return request<CotizacionResponse>('/api/cotizacion/manual', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}
