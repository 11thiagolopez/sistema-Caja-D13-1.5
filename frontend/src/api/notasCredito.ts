import { request, requestBlob } from './client'
import type { NotaCreditoRequest, NotaCreditoResponse } from '../types/api'

export function crearNotaCredito(nc: NotaCreditoRequest): Promise<NotaCreditoResponse> {
  return request<NotaCreditoResponse>('/api/notas-credito', { method: 'POST', body: JSON.stringify(nc) })
}

export function getNotasCredito(desde: string, hasta: string): Promise<NotaCreditoResponse[]> {
  return request<NotaCreditoResponse[]>(`/api/notas-credito?desde=${desde}&hasta=${hasta}`)
}

export function getNotaCredito(id: number): Promise<NotaCreditoResponse> {
  return request<NotaCreditoResponse>(`/api/notas-credito/${id}`)
}

export function descargarNotaCreditoPdf(id: number): Promise<Blob> {
  return requestBlob(`/api/notas-credito/${id}/pdf`)
}

export function emitirNotaCreditoFiscal(id: number): Promise<NotaCreditoResponse> {
  return request<NotaCreditoResponse>(`/api/notas-credito/${id}/emitir-fiscal`, { method: 'POST' })
}

export function descargarNotaCreditoFacturaPdf(id: number): Promise<Blob> {
  return requestBlob(`/api/notas-credito/${id}/factura-pdf`)
}
