import { request, requestBlob } from './client'
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

// A diferencia de descargarVentaPdf/descargarPresupuestoPdf (que fuerzan la descarga), esta abre
// el PDF fiscal en una pestaña nueva — deliberado: es el comprobante legal, útil poder verlo antes
// de guardarlo/imprimirlo.
export async function descargarPdfFactura(idVenta: number): Promise<void> {
  const blob = await requestBlob(`/api/ventas/${idVenta}/factura/pdf`)

  const blobUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.target = '_blank'

  document.body.appendChild(link)
  link.click()
  link.parentNode?.removeChild(link)
  URL.revokeObjectURL(blobUrl)
}

export async function enviarFacturaEmail(idVenta: number, email: string): Promise<void> {
  // El backend valida el email como JSON body (antes era un query param sin validar).
  return request(`/api/ventas/${idVenta}/factura/enviar-email`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}
