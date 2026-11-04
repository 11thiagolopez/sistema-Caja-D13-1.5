import { request } from './client'
import type {
  ClienteRequest,
  ClienteResponse,
  ContribuyenteResponse,
  CuentaCorrienteClienteResponse,
  MovimientoCuentaCorrienteClienteResponse,
  PagoClienteRequest,
  VentaResponse,
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

// Lo único de la cuenta corriente que ve el VENDEDOR: el saldo del cliente que eligió en Cobros.
export function getSaldoCliente(id: number): Promise<{ saldo: number }> {
  return request<{ saldo: number }>(`/api/clientes/${id}/saldo`)
}

export function getVentasCliente(id: number): Promise<VentaResponse[]> {
  return request<VentaResponse[]>(`/api/clientes/${id}/ventas`)
}

export function registrarPagoCliente(
  id: number,
  pago: PagoClienteRequest,
): Promise<MovimientoCuentaCorrienteClienteResponse> {
  return request<MovimientoCuentaCorrienteClienteResponse>(`/api/clientes/${id}/pagos`, {
    method: 'POST',
    body: JSON.stringify(pago),
  })
}
