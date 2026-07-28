import { request } from './client'
import type { Producto } from '../types/api'

export function getProductos(): Promise<Producto[]> {
  return request<Producto[]>('/api/productos')
}

export function getProducto(idProducto: number): Promise<Producto> {
  return request<Producto>(`/api/productos/${idProducto}`)
}
