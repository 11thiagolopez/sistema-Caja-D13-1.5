import { request } from './client'
import type { ConfirmarDescuentoRequest, VentaRequest, VentaResponse } from '../types/api'

export function registrarVenta(venta: VentaRequest): Promise<VentaResponse> {
  return request<VentaResponse>('/api/ventas', {
    method: 'POST',
    body: JSON.stringify(venta),
  })
}

export function confirmarDescuento(req: ConfirmarDescuentoRequest): Promise<VentaResponse> {
  return request<VentaResponse>('/api/ventas/descuento/confirmar', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function getVentas(desde: string, hasta: string): Promise<VentaResponse[]> {
  return request<VentaResponse[]>(`/api/ventas?desde=${desde}&hasta=${hasta}`)
}
