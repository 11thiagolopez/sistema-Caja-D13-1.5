import { request, requestBlob } from './client'
import type {
  TrabajoDomicilioRequest,
  VentaRequest,
  VentaResponse,
} from '../types/api'

export function registrarVenta(venta: VentaRequest): Promise<VentaResponse> {
  return request<VentaResponse>('/api/ventas', {
    method: 'POST',
    body: JSON.stringify(venta),
  })
}

export function getVentas(desde: string, hasta: string): Promise<VentaResponse[]> {
  return request<VentaResponse[]>(`/api/ventas?desde=${desde}&hasta=${hasta}`)
}

export function enviarComprobanteEmail(idVenta: number, email: string): Promise<VentaResponse> {
  return request<VentaResponse>(`/api/ventas/${idVenta}/enviar-comprobante`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function getVenta(id: number): Promise<VentaResponse> {
  return request<VentaResponse>(`/api/ventas/${id}`)
}

export function descargarVentaPdf(id: number): Promise<Blob> {
  return requestBlob(`/api/ventas/${id}/pdf`)
}

export function getAgendaTrabajos(desde: string, hasta: string): Promise<VentaResponse[]> {
  return request<VentaResponse[]>(`/api/ventas/trabajo-domicilio?desde=${desde}&hasta=${hasta}`)
}

export function anularTrabajoDomicilio(id: number): Promise<VentaResponse> {
  return request<VentaResponse>(`/api/ventas/trabajo-domicilio/${id}/anular`, { method: 'POST' })
}

export function guardarTrabajoDomicilio(req: TrabajoDomicilioRequest): Promise<VentaResponse> {
  if (req.idVenta != null) {
    return request<VentaResponse>(`/api/ventas/trabajo-domicilio/${req.idVenta}`, {
      method: 'PUT',
      body: JSON.stringify(req),
    })
  }
  return request<VentaResponse>('/api/ventas/trabajo-domicilio', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}
