import { request, requestBlob } from './client'
import { descargarBlob, verBlob } from '../utils/descargarBlob'
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

// Abre el PDF fiscal en una pestaña nueva. La URL queda viva (ver verBlob): el visor la vuelve a
// pedir al apretar su botón de guardar. Antes se revocaba enseguida: se podía ver e imprimir, pero
// guardar fallaba con un error de red del navegador.
export async function verPdfFactura(idVenta: number): Promise<void> {
  verBlob(await requestBlob(`/api/ventas/${idVenta}/factura/pdf`))
}

// Guarda el PDF en la computadora, para compartirlo por WhatsApp u otro medio. numero es el que
// devuelve numeroComprobante, para que el archivo se reconozca en la carpeta de descargas.
export async function descargarPdfFactura(idVenta: number, numero: string): Promise<void> {
  descargarBlob(await requestBlob(`/api/ventas/${idVenta}/factura/pdf`), `factura-C-${numero}.pdf`)
}

export async function enviarFacturaEmail(idVenta: number, email: string): Promise<void> {
  // El backend valida el email como JSON body (antes era un query param sin validar).
  return request(`/api/ventas/${idVenta}/factura/enviar-email`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}
