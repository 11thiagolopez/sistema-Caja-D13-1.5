import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getCuentaCorrienteProveedor, getProveedores, registrarDevolucionProveedor } from '../api/proveedores'
import { getCompras, registrarPagoProveedor } from '../api/compras'
import { getProductos } from '../api/productos'
import { ApiRequestError } from '../api/client'
import { BuscadorProductoCarrito } from '../components/BuscadorProductoCarrito'
import { useCarrito } from '../hooks/useCarrito'
import { hoyIso } from '../utils/date'
import type {
  CompraResponse,
  CuentaCorrienteProveedorResponse,
  DetalleDevolucionProveedorRequest,
  MedioPago,
  Producto,
  ProveedorResponse,
} from '../types/api'

interface ItemDevolucion extends DetalleDevolucionProveedorRequest {
  descripcionProducto: string
}

function haceNoventaDiasIso(): string {
  const fecha = new Date()
  fecha.setDate(fecha.getDate() - 90)
  return fecha.toISOString().slice(0, 10)
}

export function CuentaCorrienteProveedores() {
  const { sesion } = useAuth()
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([])
  const [idProveedor, setIdProveedor] = useState<number | ''>('')
  const [cuenta, setCuenta] = useState<CuentaCorrienteProveedorResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [desde, setDesde] = useState(haceNoventaDiasIso())
  const [hasta, setHasta] = useState(hoyIso())
  const [compras, setCompras] = useState<CompraResponse[]>([])
  const [montosPago, setMontosPago] = useState<Record<number, string>>({})
  const [medioPagoPago, setMedioPagoPago] = useState<MedioPago>('EFECTIVO')
  const [pagandoId, setPagandoId] = useState<number | null>(null)

  const [productos, setProductos] = useState<Producto[]>([])
  const { carrito, agregarOFusionar, quitar, vaciar, total } = useCarrito<ItemDevolucion>()
  const [precioLinea, setPrecioLinea] = useState('')
  const [motivoDevolucion, setMotivoDevolucion] = useState('')
  const [registrandoDevolucion, setRegistrandoDevolucion] = useState(false)
  const [errorDevolucion, setErrorDevolucion] = useState<string | null>(null)

  useEffect(() => {
    getProveedores().then(setProveedores)
    getProductos().then(setProductos)
  }, [])

  function cargarCuenta(id: number) {
    setError(null)
    getCuentaCorrienteProveedor(id)
      .then(setCuenta)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar la cuenta corriente'))
  }

  function cargarCompras(id: number) {
    getCompras(desde, hasta)
      .then((todas) => setCompras(todas.filter((c) => c.idProveedor === id && c.saldoPendiente > 0)))
      .catch(() => setCompras([]))
  }

  function onElegirProveedor(id: number) {
    setIdProveedor(id)
    cargarCuenta(id)
    cargarCompras(id)
  }

  async function onPagar(compra: CompraResponse) {
    const monto = Number(montosPago[compra.idCompra] ?? 0)
    if (!sesion || !monto) return
    setError(null)
    setPagandoId(compra.idCompra)
    try {
      await registrarPagoProveedor(compra.idCompra, {
        idEmpleado: sesion.idEmpleado,
        monto,
        medioPago: medioPagoPago,
      })
      setMontosPago((actual) => ({ ...actual, [compra.idCompra]: '' }))
      if (typeof idProveedor === 'number') {
        cargarCuenta(idProveedor)
        cargarCompras(idProveedor)
      }
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar el pago')
    } finally {
      setPagandoId(null)
    }
  }

  function agregarProductoADevolucion(producto: Producto, cantidad: number): boolean {
    const precio = Number(precioLinea) || producto.precioCompra
    if (precio == null) {
      setErrorDevolucion(`"${producto.descripcion}" no tiene precio de compra — escribí uno en "Precio unitario"`)
      return false
    }
    setErrorDevolucion(null)
    agregarOFusionar({
      idProducto: producto.idProducto,
      descripcionProducto: producto.descripcion,
      cantidad,
      precioUnitario: precio,
    })
    setPrecioLinea('')
    return true
  }

  async function onRegistrarDevolucion(e: FormEvent) {
    e.preventDefault()
    if (!sesion || typeof idProveedor !== 'number' || carrito.length === 0) return
    setErrorDevolucion(null)
    setRegistrandoDevolucion(true)
    try {
      await registrarDevolucionProveedor({
        idProveedor,
        idEmpleado: sesion.idEmpleado,
        motivo: motivoDevolucion || undefined,
        detalles: carrito.map(({ idProducto, cantidad, precioUnitario }) => ({
          idProducto: idProducto as number,
          cantidad,
          precioUnitario,
        })),
      })
      vaciar()
      setMotivoDevolucion('')
      cargarCuenta(idProveedor)
    } catch (err) {
      setErrorDevolucion(err instanceof ApiRequestError ? err.message : 'No se pudo registrar la devolución')
    } finally {
      setRegistrandoDevolucion(false)
    }
  }

  return (
    <div>
      <h2>Cuenta corriente de proveedores</h2>

      <label>
        Proveedor
        <select
          value={idProveedor}
          onChange={(e) => onElegirProveedor(Number(e.target.value))}
        >
          <option value="">Elegir...</option>
          {proveedores.map((p) => (
            <option key={p.idProveedor} value={p.idProveedor}>
              {p.nombre}
            </option>
          ))}
        </select>
      </label>

      {error && <p className="error">{error}</p>}

      {cuenta && (
        <>
          <p>
            <strong>
              {cuenta.saldoPendiente >= 0
                ? `Le debemos a ${cuenta.nombreProveedor}: $${cuenta.saldoPendiente.toFixed(2)}`
                : `${cuenta.nombreProveedor} nos debe (a favor nuestro): $${Math.abs(cuenta.saldoPendiente).toFixed(2)}`}
            </strong>
          </p>

          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Tipo</th>
                <th>Monto</th>
                <th>Descripción</th>
              </tr>
            </thead>
            <tbody>
              {cuenta.movimientos.length === 0 && (
                <tr>
                  <td colSpan={4}>Sin movimientos todavía.</td>
                </tr>
              )}
              {cuenta.movimientos.map((m) => (
                <tr key={m.idMovimiento}>
                  <td>{new Date(m.fecha).toLocaleString()}</td>
                  <td>{m.tipo}</td>
                  <td>${m.monto.toFixed(2)}</td>
                  <td>{m.descripcion ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <section>
            <h3>Registrar pago contra una compra</h3>
            <label>
              Desde
              <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
            </label>
            <label>
              Hasta
              <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
            </label>
            <button type="button" onClick={() => onElegirProveedor(idProveedor as number)}>
              Buscar compras con saldo pendiente
            </button>

            {compras.length > 0 && (
              <table>
                <thead>
                  <tr>
                    <th>Compra #</th>
                    <th>Fecha</th>
                    <th>Total</th>
                    <th>Pagado</th>
                    <th>Pendiente</th>
                    <th>Monto a pagar</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {compras.map((c) => (
                    <tr key={c.idCompra}>
                      <td>{c.idCompra}</td>
                      <td>{c.fecha}</td>
                      <td>${c.totalCompra.toFixed(2)}</td>
                      <td>${c.montoPagado.toFixed(2)}</td>
                      <td>${c.saldoPendiente.toFixed(2)}</td>
                      <td>
                        <input
                          type="number"
                          min={0}
                          max={c.saldoPendiente}
                          step="0.01"
                          value={montosPago[c.idCompra] ?? ''}
                          onChange={(e) =>
                            setMontosPago((actual) => ({ ...actual, [c.idCompra]: e.target.value }))
                          }
                        />
                      </td>
                      <td>
                        <button type="button" onClick={() => onPagar(c)} disabled={pagandoId === c.idCompra}>
                          {pagandoId === c.idCompra ? 'Pagando...' : 'Pagar'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <label>
              Medio de pago
              <select value={medioPagoPago} onChange={(e) => setMedioPagoPago(e.target.value as MedioPago)}>
                <option value="EFECTIVO">Efectivo</option>
                <option value="TRANSFERENCIA">Transferencia</option>
                <option value="TARJETA">Tarjeta</option>
              </select>
            </label>
          </section>

          <section>
            <h3>Registrar devolución de mercadería</h3>
            <label>
              Precio unitario (si no se completa, usa el precio de compra cargado del producto)
              <input value={precioLinea} onChange={(e) => setPrecioLinea(e.target.value)} />
            </label>
            <BuscadorProductoCarrito
              productos={productos}
              datalistId="productos-devolucion-datalist"
              onAgregar={agregarProductoADevolucion}
            />
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
                  <tr key={item.clientId}>
                    <td>{item.descripcionProducto}</td>
                    <td>{item.cantidad}</td>
                    <td>{item.precioUnitario.toFixed(2)}</td>
                    <td>{(item.cantidad * item.precioUnitario).toFixed(2)}</td>
                    <td>
                      <button type="button" onClick={() => quitar(index)}>
                        Quitar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p>Total a devolver: ${total.toFixed(2)}</p>
            <form onSubmit={onRegistrarDevolucion}>
              <label>
                Motivo
                <input value={motivoDevolucion} onChange={(e) => setMotivoDevolucion(e.target.value)} />
              </label>
              {errorDevolucion && <p className="error">{errorDevolucion}</p>}
              <button type="submit" disabled={registrandoDevolucion || carrito.length === 0}>
                {registrandoDevolucion && <span className="spinner" />}
                {registrandoDevolucion ? 'Registrando...' : 'Registrar devolución'}
              </button>
            </form>
          </section>
        </>
      )}
    </div>
  )
}
