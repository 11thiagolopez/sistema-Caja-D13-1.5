import { request } from './client'
import type {
  CuentaCorrienteProveedorResponse,
  DevolucionProveedorRequest,
  DevolucionProveedorResponse,
  ProveedorRequest,
  ProveedorResponse,
  RegistrarPagoProveedorRequest,
} from '../types/api'

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

export function getCuentaCorrienteProveedor(id: number): Promise<CuentaCorrienteProveedorResponse> {
  return request<CuentaCorrienteProveedorResponse>(`/api/proveedores/${id}/cuenta-corriente`)
}

export function registrarPagoAProveedor(
  id: number,
  pago: RegistrarPagoProveedorRequest,
): Promise<CuentaCorrienteProveedorResponse> {
  return request<CuentaCorrienteProveedorResponse>(`/api/proveedores/${id}/pagos`, {
    method: 'POST',
    body: JSON.stringify(pago),
  })
}

export function registrarDevolucionProveedor(
  devolucion: DevolucionProveedorRequest,
): Promise<DevolucionProveedorResponse> {
  return request<DevolucionProveedorResponse>('/api/proveedores/devoluciones', {
    method: 'POST',
    body: JSON.stringify(devolucion),
  })
}

export function getDevolucionesProveedor(desde: string, hasta: string): Promise<DevolucionProveedorResponse[]> {
  return request<DevolucionProveedorResponse[]>(`/api/proveedores/devoluciones?desde=${desde}&hasta=${hasta}`)
}
