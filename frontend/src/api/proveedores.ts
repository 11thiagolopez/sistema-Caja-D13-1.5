import { request } from './client'
import type { ProveedorRequest, ProveedorResponse } from '../types/api'

export function getProveedores(): Promise<ProveedorResponse[]> {
  return request<ProveedorResponse[]>('/api/proveedores')
}

export function crearProveedor(proveedor: ProveedorRequest): Promise<ProveedorResponse> {
  return request<ProveedorResponse>('/api/proveedores', { method: 'POST', body: JSON.stringify(proveedor) })
}

export function actualizarProveedor(id: number, proveedor: ProveedorRequest): Promise<ProveedorResponse> {
  return request<ProveedorResponse>(`/api/proveedores/${id}`, { method: 'PUT', body: JSON.stringify(proveedor) })
}

export function eliminarProveedor(id: number): Promise<void> {
  return request<void>(`/api/proveedores/${id}`, { method: 'DELETE' })
}
