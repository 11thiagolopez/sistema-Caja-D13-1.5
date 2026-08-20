import { request } from './client'
import type { FacturaFiscalResponse, FacturarVentaRequest } from '../types/api'

export function facturarVenta(idVenta: number, req: FacturarVentaRequest): Promise<FacturaFiscalResponse> {
  return request<FacturaFiscalResponse>(`/api/ventas/${idVenta}/factura`, {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

// null si la venta todavía no tiene factura fiscal (backend responde 204).
export function getFactura(idVenta: number): Promise<FacturaFiscalResponse | null> {
  return request<FacturaFiscalResponse | null>(`/api/ventas/${idVenta}/factura`)
}
