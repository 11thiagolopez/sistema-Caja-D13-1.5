import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { registrarVenta } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import type { DetalleVentaRequest, MedioPago, Producto, VentaResponse } from '../types/api'

interface ItemCarrito extends DetalleVentaRequest {
  descripcionProducto: string
}

export function RegistrarVenta() {
  const { sesion } = useAuth()
  const [productos, setProductos] = useState<Producto[]>([])
  const [idProductoSeleccionado, setIdProductoSeleccionado] = useState<number | null>(null)
  const [cantidad, setCantidad] = useState(1)
  const [carrito, setCarrito] = useState<ItemCarrito[]>([])
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [descuento, setDescuento] = useState('')
  const [motivoDescuento, setMotivoDescuento] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<VentaResponse | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    getProductos()
      .then((lista) => {
        setProductos(lista)
        setIdProductoSeleccionado(lista[0]?.idProducto ?? null)
      })
      .catch(() => setError('No se pudieron cargar los productos'))
  }, [])

  function agregarAlCarrito() {
    const producto = productos.find((p) => p.idProducto === idProductoSeleccionado)
    if (!producto || cantidad <= 0) return
    setCarrito((actual) => [
      ...actual,
      {
        idProducto: producto.idProducto,
        descripcionProducto: producto.descripcion,
        cantidad,
        precioUnitario: producto.precioVenta,
      },
    ])
    setCantidad(1)
  }

  function quitarDelCarrito(index: number) {
    setCarrito((actual) => actual.filter((_, i) => i !== index))
  }

  const total = carrito.reduce((acc, item) => acc + item.cantidad * item.precioUnitario, 0)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!sesion || carrito.length === 0) return
    setError(null)
    setEnviando(true)
    try {
      const descuentoNumero = descuento ? Number(descuento) : undefined
      const venta = await registrarVenta({
        idEmpleado: sesion.idEmpleado,
        medioPago,
        detalles: carrito.map(({ idProducto, cantidad, precioUnitario }) => ({
          idProducto,
          cantidad,
          precioUnitario,
        })),
        descuento: descuentoNumero,
        motivoDescuento: descuentoNumero ? motivoDescuento : undefined,
      })
      setResultado(venta)
      setCarrito([])
      setDescuento('')
      setMotivoDescuento('')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar la venta')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div>
      <h2>Registrar venta</h2>

      <div className="agregar-producto">
        <select
          value={idProductoSeleccionado ?? ''}
          onChange={(e) => setIdProductoSeleccionado(Number(e.target.value))}
        >
          {productos.map((producto) => (
            <option key={producto.idProducto} value={producto.idProducto}>
              {producto.descripcion} (stock: {producto.stockActual})
            </option>
          ))}
        </select>
        <input
          type="number"
          min={1}
          value={cantidad}
          onChange={(e) => setCantidad(Number(e.target.value))}
        />
        <button type="button" onClick={agregarAlCarrito}>
          Agregar
        </button>
      </div>

      <table>
        <thead>
          <tr>
            <th>Producto</th>
            <th>Cantidad</th>
            <th>Precio unitario</th>
            <th>Subtotal</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {carrito.map((item, index) => (
            <tr key={index}>
              <td>{item.descripcionProducto}</td>
              <td>{item.cantidad}</td>
              <td>{item.precioUnitario.toFixed(2)}</td>
              <td>{(item.cantidad * item.precioUnitario).toFixed(2)}</td>
              <td>
                <button type="button" onClick={() => quitarDelCarrito(index)}>
                  Quitar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <strong>Total: {total.toFixed(2)}</strong>
      </p>

      <form onSubmit={onSubmit}>
        <label>
          Medio de pago
          <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
            <option value="TARJETA">Tarjeta</option>
          </select>
        </label>
        <label>
          Descuento (opcional)
          <input
            type="number"
            min={0}
            step="0.01"
            value={descuento}
            onChange={(e) => setDescuento(e.target.value)}
          />
        </label>
        {descuento && Number(descuento) > 0 && (
          <label>
            Motivo del descuento
            <input
              value={motivoDescuento}
              onChange={(e) => setMotivoDescuento(e.target.value)}
              required
            />
          </label>
        )}
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={enviando || carrito.length === 0}>
          {enviando ? 'Registrando...' : 'Registrar venta'}
        </button>
      </form>

      {resultado && (
        <p className="resultado">
          {resultado.estado === 'PENDIENTE_AUTORIZACION'
            ? `Venta #${resultado.idVenta} registrada, pendiente de autorización: se envió un código a los administradores por email.`
            : `Venta #${resultado.idVenta} confirmada.`}
        </p>
      )}
    </div>
  )
}
