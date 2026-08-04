import { request } from './client'
import type { CompraRequest, CompraResponse, PagoProveedorDTO, ProductoComprasRankingDTO } from '../types/api'

export function registrarCompra(compra: CompraRequest): Promise<CompraResponse> {
  return request<CompraResponse>('/api/compras', { method: 'POST', body: JSON.stringify(compra) })
}

export function getCompras(desde: string, hasta: string): Promise<CompraResponse[]> {
  return request<CompraResponse[]>(`/api/compras?desde=${desde}&hasta=${hasta}`)
}

export function getPagosPorProveedor(desde: string, hasta: string): Promise<PagoProveedorDTO[]> {
  return request<PagoProveedorDTO[]>(`/api/compras/pagos-proveedor?desde=${desde}&hasta=${hasta}`)
}

export function getProductosMasComprados(
  desde: string,
  hasta: string,
  limit = 10,
): Promise<ProductoComprasRankingDTO[]> {
  return request<ProductoComprasRankingDTO[]>(
    `/api/compras/productos-mas-comprados?desde=${desde}&hasta=${hasta}&limit=${limit}`,
  )
}
