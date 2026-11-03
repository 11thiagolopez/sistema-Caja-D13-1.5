import { request } from './client'
import type { AutorizacionEstado, AutorizacionPeticion, TipoAutorizacion } from '../types/api'

export function getPeticiones(): Promise<AutorizacionPeticion[]> {
  return request<AutorizacionPeticion[]>('/api/autorizaciones')
}

export function getEstadoPeticiones(retiros: number[], descuentos: number[]): Promise<AutorizacionEstado[]> {
  return request<AutorizacionEstado[]>(
    `/api/autorizaciones/estado?retiros=${retiros.join(',')}&descuentos=${descuentos.join(',')}`,
  )
}

export function resolverPeticion(
  tipo: TipoAutorizacion,
  id: number,
  accion: 'aprobar' | 'rechazar',
): Promise<AutorizacionEstado> {
  const coleccion = tipo === 'RETIRO' ? 'retiros' : 'descuentos'
  return request<AutorizacionEstado>(`/api/autorizaciones/${coleccion}/${id}/${accion}`, { method: 'POST' })
}
