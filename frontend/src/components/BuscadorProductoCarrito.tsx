import { useState } from 'react'
import type { Producto } from '../types/api'

export function etiquetaProducto(producto: Producto): string {
  return `${producto.descripcion} - ${producto.marca ?? 'sin marca'} (${producto.codigoInterno})`
}

interface BuscadorProductoCarritoProps {
  productos: Producto[]
  datalistId: string
  // Devuelve false si no se pudo agregar (ej. sin precio de venta cargado) — el buscador no se
  // limpia en ese caso, así el que llama puede mostrar el error que corresponda.
  onAgregar: (producto: Producto, cantidad: number) => boolean
}

// Buscador de producto del catálogo + cantidad + botón "Agregar", compartido entre Cobros y
// Trabajo a domicilio (mismo flujo exacto, "artículos utilizados" en un trabajo es esto mismo).
export function BuscadorProductoCarrito({ productos, datalistId, onAgregar }: BuscadorProductoCarritoProps) {
  const [productoTexto, setProductoTexto] = useState('')
  const [cantidad, setCantidad] = useState(1)

  const productoSeleccionado = productos.find((p) => etiquetaProducto(p) === productoTexto)

  function agregar() {
    if (!productoSeleccionado || cantidad <= 0) return
    if (onAgregar(productoSeleccionado, cantidad)) {
      setCantidad(1)
      setProductoTexto('')
    }
  }

  return (
    <div className="agregar-producto">
      <input
        list={datalistId}
        aria-label="Producto"
        className="buscador-producto-texto"
        placeholder="Descripción o marca…"
        value={productoTexto}
        onChange={(e) => setProductoTexto(e.target.value)}
      />
      <datalist id={datalistId}>
        {productos.map((producto) => (
          <option key={producto.idProducto} value={etiquetaProducto(producto)} />
        ))}
      </datalist>
      <input
        type="number"
        inputMode="numeric"
        aria-label="Cantidad"
        className="buscador-producto-cantidad"
        min={1}
        value={cantidad}
        onChange={(e) => setCantidad(Number(e.target.value))}
      />
      <button type="button" onClick={agregar} disabled={!productoSeleccionado}>
        Agregar
      </button>
    </div>
  )
}
