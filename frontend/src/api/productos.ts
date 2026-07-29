import { request } from './client'
import type { CargarStockRequest, Producto, ProductoRequest } from '../types/api'

export function getProductos(): Promise<Producto[]> {
  return request<Producto[]>('/api/productos')
}

export function getProducto(idProducto: number): Promise<Producto> {
  return request<Producto>(`/api/productos/${idProducto}`)
}

export function buscarProductoPorCodigo(codigo: string): Promise<Producto> {
  return request<Producto>(`/api/productos/buscar-por-codigo?codigo=${encodeURIComponent(codigo)}`)
}

export function crearProducto(producto: ProductoRequest): Promise<Producto> {
  return request<Producto>('/api/productos', { method: 'POST', body: JSON.stringify(producto) })
}

export function eliminarProducto(idProducto: number): Promise<void> {
  return request<void>(`/api/productos/${idProducto}`, { method: 'DELETE' })
}

export function cargarStock(req: CargarStockRequest): Promise<Producto> {
  return request<Producto>('/api/productos/cargar-stock', { method: 'POST', body: JSON.stringify(req) })
}
