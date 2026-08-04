import { request } from './client'
import type { BalanceFinancieroResponse, ComisionEmpleadoDTO, ProductoRankingDTO, VentaResponse } from '../types/api'

export function getProductosGanadores(
  desde: string,
  hasta: string,
  limit = 10,
): Promise<ProductoRankingDTO[]> {
  return request<ProductoRankingDTO[]>(
    `/api/reportes/productos-ganadores?desde=${desde}&hasta=${hasta}&limit=${limit}`,
  )
}

export function getBalance(desde: string, hasta: string): Promise<BalanceFinancieroResponse> {
  return request<BalanceFinancieroResponse>(`/api/reportes/balance?desde=${desde}&hasta=${hasta}`)
}

export function getComisiones(desde: string, hasta: string): Promise<ComisionEmpleadoDTO[]> {
  return request<ComisionEmpleadoDTO[]>(`/api/reportes/comisiones?desde=${desde}&hasta=${hasta}`)
}

export function getVentasPorVendedor(desde: string, hasta: string, idEmpleado: number): Promise<VentaResponse[]> {
  return request<VentaResponse[]>(
    `/api/reportes/ventas-por-vendedor?desde=${desde}&hasta=${hasta}&idEmpleado=${idEmpleado}`,
  )
}
