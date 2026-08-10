import { request, requestBlob } from './client'
import type { PresupuestoRequest, PresupuestoResponse } from '../types/api'

export function crearPresupuesto(presupuesto: PresupuestoRequest): Promise<PresupuestoResponse> {
  return request<PresupuestoResponse>('/api/presupuestos', { method: 'POST', body: JSON.stringify(presupuesto) })
}

export function getPresupuestos(desde: string, hasta: string): Promise<PresupuestoResponse[]> {
  return request<PresupuestoResponse[]>(`/api/presupuestos?desde=${desde}&hasta=${hasta}`)
}

export function getPresupuesto(id: number): Promise<PresupuestoResponse> {
  return request<PresupuestoResponse>(`/api/presupuestos/${id}`)
}

export function enviarPresupuestoEmail(id: number): Promise<PresupuestoResponse> {
  return request<PresupuestoResponse>(`/api/presupuestos/${id}/enviar-email`, { method: 'POST' })
}

export function descargarPresupuestoPdf(id: number): Promise<Blob> {
  return requestBlob(`/api/presupuestos/${id}/pdf`)
}
