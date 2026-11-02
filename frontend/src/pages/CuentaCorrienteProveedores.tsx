import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import {
  getCuentaCorrienteProveedor,
  getProveedores,
  registrarDevolucionProveedor,
  registrarPagoAProveedor,
} from '../api/proveedores'
import { getProductos } from '../api/productos'
import { ApiRequestError } from '../api/client'
import { BuscadorProductoCarrito } from '../components/BuscadorProductoCarrito'
import { useCarrito } from '../hooks/useCarrito'
import { formatoFechaHora } from '../utils/formato'
import type {
  CuentaCorrienteProveedorResponse,
  DetalleDevolucionProveedorRequest,
  MedioPagoProveedor,
  OrigenPago,
  Producto,
  ProveedorResponse,
} from '../types/api'

interface ItemDevolucion extends DetalleDevolucionProveedorRequest {
  descripcionProducto: string
}

export function CuentaCorrienteProveedores() {
  const { sesion } = useAuth()
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([])
  const [idProveedor, setIdProveedor] = useState<number | ''>('')
  const [cuenta, setCuenta] = useState<CuentaCorrienteProveedorResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [montoPago, setMontoPago] = useState('')
  const [medioPagoPago, setMedioPagoPago] = useState<MedioPagoProveedor>('EFECTIVO')
  // Sin valor por defecto: un origen elegido sin querer descuadraría el arqueo de caja.
  const [origenPago, setOrigenPago] = useState<OrigenPago | ''>('')
  const [pagando, setPagando] = useState(false)
  const [mensajePago, setMensajePago] = useState<string | null>(null)

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

  function onElegirProveedor(id: number) {
    setIdProveedor(id)
    setMontoPago('')
    setOrigenPago('')
    setMensajePago(null)
    cargarCuenta(id)
  }

  async function onPagar(e: FormEvent) {
    e.preventDefault()
    const monto = Number(montoPago)
    if (!sesion || typeof idProveedor !== 'number' || !monto || !origenPago) return
    setError(null)
    setMensajePago(null)
    setPagando(true)
    try {
      const actualizada = await registrarPagoAProveedor(idProveedor, {
        idEmpleado: sesion.idEmpleado,
        monto,
        medioPago: medioPagoPago,
        origen: origenPago,
      })
      setCuenta(actualizada)
      setMontoPago('')
      setOrigenPago('')
      setMensajePago(`Pago de $${monto.toFixed(2)} registrado.`)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar el pago')
    } finally {
      setPagando(false)
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
                <th>Medio de pago</th>
                <th>Origen</th>
                <th>Descripción</th>
              </tr>
            </thead>
            <tbody>
              {cuenta.movimientos.length === 0 && (
                <tr>
                  <td colSpan={6}>Sin movimientos todavía.</td>
                </tr>
              )}
              {cuenta.movimientos.map((m) => (
                <tr key={m.idMovimiento}>
                  <td>{formatoFechaHora(m.fecha)}</td>
                  <td>{m.tipo}</td>
                  <td>${m.monto.toFixed(2)}</td>
                  <td>{m.medioPago ?? '—'}</td>
                  <td>{m.origen ?? '—'}</td>
                  <td>{m.descripcion ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <section>
            <h3>Saldar deuda</h3>
            {mensajePago && <p className="resultado">{mensajePago}</p>}
            {cuenta.saldoPendiente <= 0 ? (
              <p>No hay deuda pendiente con este proveedor.</p>
            ) : (
              <>
                <table>
                  <thead>
                    <tr>
                      <th>Compra #</th>
                      <th>Fecha</th>
                      <th>Total</th>
                      <th>Pendiente</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cuenta.comprasPendientes.map((c) => (
                      <tr key={c.idCompra}>
                        <td>{c.idCompra}</td>
                        <td>{c.fecha}</td>
                        <td>${c.totalCompra.toFixed(2)}</td>
                        <td>${c.saldoPendiente.toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <form onSubmit={onPagar}>
                  <label>
                    Monto a pagar (se aplica primero a la compra más vieja)
                    <input
                      type="number"
                      required
                      min={0.01}
                      max={cuenta.saldoPendiente}
                      step="0.01"
                      value={montoPago}
                      onChange={(e) => setMontoPago(e.target.value)}
                    />
                  </label>
                  <button type="button" onClick={() => setMontoPago(cuenta.saldoPendiente.toFixed(2))}>
                    Completar deuda total
                  </button>
                  <label>
                    Medio de pago
                    <select
                      value={medioPagoPago}
                      onChange={(e) => setMedioPagoPago(e.target.value as MedioPagoProveedor)}
                    >
                      <option value="EFECTIVO">Efectivo</option>
                      <option value="TRANSFERENCIA">Transferencia</option>
                    </select>
                  </label>
                  <label>
                    Origen del dinero
                    <select
                      required
                      value={origenPago}
                      onChange={(e) => setOrigenPago(e.target.value as OrigenPago | '')}
                    >
                      <option value="">Elegir...</option>
                      <option value="CAJA">Caja</option>
                      <option value="EXTERNO">Externo</option>
                    </select>
                  </label>
                  <button type="submit" disabled={pagando}>
                    {pagando && <span className="spinner" />}
                    {pagando ? 'Registrando...' : 'Registrar pago'}
                  </button>
                </form>
              </>
            )}
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
