import { request } from './client'
import type { MarcaResponse } from '../types/api'

export function getMarcas(): Promise<MarcaResponse[]> {
  return request<MarcaResponse[]>('/api/marcas')
}
