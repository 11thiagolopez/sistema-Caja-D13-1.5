import { request } from './client'
import type { BalanceFinancieroResponse, ProductoRankingDTO } from '../types/api'

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
