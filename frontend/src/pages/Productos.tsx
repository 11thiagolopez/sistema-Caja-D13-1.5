import { useEffect, useState } from 'react'
import { getProductos } from '../api/productos'
import { ApiRequestError } from '../api/client'
import type { Producto } from '../types/api'

export function Productos() {
  const [productos, setProductos] = useState<Producto[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    getProductos()
      .then(setProductos)
      .catch((err) =>
        setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los productos'),
      )
      .finally(() => setCargando(false))
  }, [])

  if (cargando) return <p>Cargando productos...</p>
  if (error) return <p className="error">{error}</p>

  return (
    <div>
      <h2>Productos</h2>
      <table>
        <thead>
          <tr>
            <th>Descripción</th>
            <th>Marca</th>
            <th>Rubro</th>
            <th>Precio venta</th>
            <th>Stock</th>
          </tr>
        </thead>
        <tbody>
          {productos.map((producto) => (
            <tr key={producto.idProducto}>
              <td>{producto.descripcion}</td>
              <td>{producto.marca}</td>
              <td>{producto.rubro}</td>
              <td>{producto.precioVenta.toFixed(2)}</td>
              <td>{producto.stockActual}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
