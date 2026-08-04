import { request } from './client'
import type { GastoRequest, GastoResponse } from '../types/api'

export function getGastos(desde: string, hasta: string): Promise<GastoResponse[]> {
  return request<GastoResponse[]>(`/api/gastos?desde=${desde}&hasta=${hasta}`)
}

export function crearGasto(gasto: GastoRequest): Promise<GastoResponse> {
  return request<GastoResponse>('/api/gastos', { method: 'POST', body: JSON.stringify(gasto) })
}
