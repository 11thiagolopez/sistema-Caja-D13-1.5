import { request } from './client'
import type { EmpleadoRequest, EmpleadoResponse, EmpleadoUpdateRequest, TecnicoRequest } from '../types/api'

export function crearTecnico(tecnico: TecnicoRequest): Promise<EmpleadoResponse> {
  return request<EmpleadoResponse>('/api/empleados/tecnicos', { method: 'POST', body: JSON.stringify(tecnico) })
}

export function actualizarTecnico(id: number, tecnico: TecnicoRequest): Promise<EmpleadoResponse> {
  return request<EmpleadoResponse>(`/api/empleados/tecnicos/${id}`, { method: 'PUT', body: JSON.stringify(tecnico) })
}

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
