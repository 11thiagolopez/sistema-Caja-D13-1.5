import { request } from './client'
import type { EmpleadoRequest, EmpleadoResponse, EmpleadoUpdateRequest } from '../types/api'

export function getEmpleados(): Promise<EmpleadoResponse[]> {
  return request<EmpleadoResponse[]>('/api/empleados')
}

export function crearEmpleado(empleado: EmpleadoRequest): Promise<EmpleadoResponse> {
  return request<EmpleadoResponse>('/api/empleados', { method: 'POST', body: JSON.stringify(empleado) })
}

export function actualizarEmpleado(id: number, empleado: EmpleadoUpdateRequest): Promise<EmpleadoResponse> {
  return request<EmpleadoResponse>(`/api/empleados/${id}`, { method: 'PUT', body: JSON.stringify(empleado) })
}

export function desactivarEmpleado(id: number): Promise<void> {
  return request<void>(`/api/empleados/${id}`, { method: 'DELETE' })
}
