import { request } from './client'
import type {
  ClienteRequest,
  ClienteResponse,
  ContribuyenteResponse,
  CuentaCorrienteClienteResponse,
} from '../types/api'

export function getClientes(): Promise<ClienteResponse[]> {
  return request<ClienteResponse[]>('/api/clientes')
}

export function buscarClientes(q: string): Promise<ClienteResponse[]> {
  return request<ClienteResponse[]>(`/api/clientes/buscar?q=${encodeURIComponent(q)}`)
}

export function consultarPadron(cuit: string): Promise<ContribuyenteResponse> {
  return request<ContribuyenteResponse>(`/api/clientes/padron/${cuit}`)
}

export function getCliente(id: number): Promise<ClienteResponse> {
  return request<ClienteResponse>(`/api/clientes/${id}`)
}

export function crearCliente(cliente: ClienteRequest): Promise<ClienteResponse> {
  return request<ClienteResponse>('/api/clientes', { method: 'POST', body: JSON.stringify(cliente) })
}

export function actualizarCliente(id: number, cliente: ClienteRequest): Promise<ClienteResponse> {
  return request<ClienteResponse>(`/api/clientes/${id}`, { method: 'PUT', body: JSON.stringify(cliente) })
}

export function eliminarCliente(id: number): Promise<void> {
  return request<void>(`/api/clientes/${id}`, { method: 'DELETE' })
}

export function getCuentaCorrienteCliente(id: number): Promise<CuentaCorrienteClienteResponse> {
  return request<CuentaCorrienteClienteResponse>(`/api/clientes/${id}/cuenta-corriente`)
}
