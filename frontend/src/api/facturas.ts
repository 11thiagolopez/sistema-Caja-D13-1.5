

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

export async function descargarPdfFactura(idVenta: number): Promise<void> {
  // Usamos tu requestBlob que ya se encarga de inyectar el token y manejar errores
  const blob = await requestBlob(`/api/ventas/${idVenta}/factura/pdf`);
  
  const blobUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = blobUrl;
  link.target = '_blank'; // Abre el PDF en una pestaña nueva
  
  document.body.appendChild(link);
  link.click();
  link.parentNode?.removeChild(link);
  window.URL.revokeObjectURL(blobUrl);
}

export async function enviarFacturaEmail(idVenta: number, email: string): Promise<void> {
  // Usamos tu función request estándar para el POST
  return request(`/api/ventas/${idVenta}/factura/enviar-email?email=${encodeURIComponent(email)}`, {
    method: 'POST'
  });
}