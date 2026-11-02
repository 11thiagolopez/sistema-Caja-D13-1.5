import { request } from './client'
import type {
  BalanceFinancieroResponse,
  ComisionEmpleadoDTO,
  FormaPagoResumenDTO,
  MarcaRankingDTO,
  OrdenRanking,
  PuntoGraficoDTO,
  RankingItemDTO,
  RankingMarcasResponse,
  ResumenVentasDTO,
  VentaResponse,
  VentasPorDiaResponse,
} from '../types/api'

export function getResumenVentas(desde: string, hasta: string): Promise<ResumenVentasDTO> {
  return request<ResumenVentasDTO>(`/api/reportes/resumen?desde=${desde}&hasta=${hasta}`)
}

export function getMarcasGrafico(desde: string, hasta: string, orden: OrdenRanking): Promise<RankingMarcasResponse> {
  return request<RankingMarcasResponse>(`/api/reportes/marcas?desde=${desde}&hasta=${hasta}&orden=${orden}`)
}

export function getProductosGrafico(desde: string, hasta: string, orden: OrdenRanking): Promise<RankingItemDTO[]> {
  return request<RankingItemDTO[]>(`/api/reportes/productos?desde=${desde}&hasta=${hasta}&orden=${orden}`)
}

export function getVentasPorDia(desde: string, hasta: string): Promise<VentasPorDiaResponse> {
  return request<VentasPorDiaResponse>(`/api/reportes/ventas-por-dia?desde=${desde}&hasta=${hasta}`)
}

export function getVentasPorDiaSemana(desde: string, hasta: string): Promise<PuntoGraficoDTO[]> {
  return request<PuntoGraficoDTO[]>(`/api/reportes/ventas-por-dia-semana?desde=${desde}&hasta=${hasta}`)
}

export function getMediosPago(desde: string, hasta: string): Promise<PuntoGraficoDTO[]> {
  return request<PuntoGraficoDTO[]>(`/api/reportes/medios-pago?desde=${desde}&hasta=${hasta}`)
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

export function getVentasPorMarca(desde: string, hasta: string): Promise<MarcaRankingDTO[]> {
  return request<MarcaRankingDTO[]>(`/api/reportes/ventas-por-marca?desde=${desde}&hasta=${hasta}`)
}

export function getVentasPorFormaPago(desde: string, hasta: string): Promise<FormaPagoResumenDTO[]> {
  return request<FormaPagoResumenDTO[]>(`/api/reportes/ventas-por-forma-pago?desde=${desde}&hasta=${hasta}`)
}
