import { request } from './client'
import type {
  AbrirCajaRequest,
  ConfirmarRetiroRequest,
  MovimientoCajaResponse,
  ResumenDiaResponse,
  ResumenRangoResponse,
  RetiroSolicitarRequest,
  SesionCajaResponse,
  SolicitudRetiroResponse,
} from '../types/api'

export function abrirCaja(req: AbrirCajaRequest): Promise<SesionCajaResponse> {
  return request<SesionCajaResponse>('/api/caja/abrir', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function cerrarCaja(): Promise<SesionCajaResponse> {
  return request<SesionCajaResponse>('/api/caja/cerrar', { method: 'POST' })
}

// null cuando no hay ninguna sesión ABIERTA (backend responde 204 sin cuerpo).
export function getSesionAbierta(): Promise<SesionCajaResponse | null> {
  return request<SesionCajaResponse | null>('/api/caja/sesion-abierta')
}

export function solicitarRetiro(req: RetiroSolicitarRequest): Promise<SolicitudRetiroResponse> {
  return request<SolicitudRetiroResponse>('/api/caja/retiro/solicitar', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function confirmarRetiro(req: ConfirmarRetiroRequest): Promise<MovimientoCajaResponse> {
  return request<MovimientoCajaResponse>('/api/caja/retiro/confirmar', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function getResumenDia(): Promise<ResumenDiaResponse> {
  return request<ResumenDiaResponse>('/api/caja/resumen-dia')
}

export function getResumenRango(desde: string, hasta: string): Promise<ResumenRangoResponse> {
  return request<ResumenRangoResponse>(`/api/caja/resumen?desde=${desde}&hasta=${hasta}`)
}
