import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { buscarProductoPorCodigo, getProductos } from '../api/productos'
import { confirmarDescuento, registrarVenta } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { BarcodeInput } from '../components/BarcodeInput'
import { ComprobanteInterno } from '../components/ComprobanteInterno'
import type { DetalleVentaRequest, MedioPago, Producto, VentaResponse } from '../types/api'

function etiquetaProducto(producto: Producto): string {
  return `${producto.descripcion} - ${producto.marca ?? 'sin marca'} (${producto.codigoInterno})`
}

interface ItemCarrito extends DetalleVentaRequest {
  descripcionProducto: string
}

export function RegistrarVenta() {
  const { sesion } = useAuth()
  const [productos, setProductos] = useState<Producto[]>([])
  const [productoTexto, setProductoTexto] = useState('')
  const [cantidad, setCantidad] = useState(1)
  const [carrito, setCarrito] = useState<ItemCarrito[]>([])
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [descuento, setDescuento] = useState('')
  const [motivoDescuento, setMotivoDescuento] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<VentaResponse | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [mostrarComprobante, setMostrarComprobante] = useState(false)

  const [idVentaConfirmar, setIdVentaConfirmar] = useState('')
  const [codigoConfirmar, setCodigoConfirmar] = useState('')
  const [confirmando, setConfirmando] = useState(false)
  const [errorConfirmacion, setErrorConfirmacion] = useState<string | null>(null)
  const [mensajeConfirmacion, setMensajeConfirmacion] = useState<string | null>(null)

  const [errorEscaneo, setErrorEscaneo] = useState<string | null>(null)

  useEffect(() => {
    getProductos()
      .then(setProductos)
      .catch(() => setError('No se pudieron cargar los productos'))
  }, [])

  const productoSeleccionado = productos.find((p) => etiquetaProducto(p) === productoTexto)

  function agregarProductoAlCarrito(producto: Producto, cantidadAAgregar: number): boolean {
    const precioVenta = producto.precioVenta
    if (precioVenta == null) return false
    setCarrito((actual) => {
      const indiceExistente = actual.findIndex((item) => item.idProducto === producto.idProducto)
      if (indiceExistente >= 0) {
        const copia = [...actual]
        copia[indiceExistente] = {
          ...copia[indiceExistente],
          cantidad: copia[indiceExistente].cantidad + cantidadAAgregar,
        }
        return copia
      }
      return [
        ...actual,
        {
          idProducto: producto.idProducto,
          descripcionProducto: producto.descripcion,
          cantidad: cantidadAAgregar,
          precioUnitario: precioVenta,
        },
      ]
    })
    return true
  }

  function agregarAlCarrito() {
    if (!productoSeleccionado || cantidad <= 0) return
    if (!agregarProductoAlCarrito(productoSeleccionado, cantidad)) {
      setError(`"${productoSeleccionado.descripcion}" no tiene precio de venta cargado, no se puede vender`)
      return
    }
    setCantidad(1)
    setProductoTexto('')
  }

  async function onEscanear(codigo: string) {
    setErrorEscaneo(null)
    try {
      const producto = await buscarProductoPorCodigo(codigo)
      if (!agregarProductoAlCarrito(producto, 1)) {
        setErrorEscaneo(`"${producto.descripcion}" no tiene precio de venta cargado, no se puede vender`)
      }
    } catch {
      setErrorEscaneo(`Producto no encontrado para el código ${codigo}`)
    }
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
      setMostrarComprobante(false)
      setCarrito([])
      setDescuento('')
      setMotivoDescuento('')
      // Precargamos el id de venta abajo (sigue editable): quien registró la venta con
      // descuento es normalmente quien la va a confirmar apenas el admin le pase el código.
      if (venta.estado === 'PENDIENTE_AUTORIZACION') {
        setIdVentaConfirmar(String(venta.idVenta))
        setMensajeConfirmacion(null)
      }
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar la venta')
    } finally {
      setEnviando(false)
    }
  }

  async function onConfirmarDescuento() {
    if (!idVentaConfirmar || !codigoConfirmar) return
    setErrorConfirmacion(null)
    setMensajeConfirmacion(null)
    setConfirmando(true)
    try {
      const venta = await confirmarDescuento({ idVenta: Number(idVentaConfirmar), codigo: codigoConfirmar })
      setMensajeConfirmacion(`Venta #${venta.idVenta} confirmada.`)
      setIdVentaConfirmar('')
      setCodigoConfirmar('')
    } catch (err) {
      setErrorConfirmacion(err instanceof ApiRequestError ? err.message : 'No se pudo confirmar el descuento')
    } finally {
      setConfirmando(false)
    }
  }

  return (
    <div>
      <h2>Registrar venta</h2>

      <section>
        <h3>Escanear código de barras</h3>
        <BarcodeInput onScan={onEscanear} />
        {errorEscaneo && <p className="error">{errorEscaneo}</p>}
      </section>

      <div className="agregar-producto">
        <input
          list="productos-datalist"
          placeholder="Buscar producto por descripción o marca"
          value={productoTexto}
          onChange={(e) => setProductoTexto(e.target.value)}
        />
        <datalist id="productos-datalist">
          {productos.map((producto) => (
            <option key={producto.idProducto} value={etiquetaProducto(producto)} />
          ))}
        </datalist>
        <input
          type="number"
          min={1}
          value={cantidad}
          onChange={(e) => setCantidad(Number(e.target.value))}
        />
        <button type="button" onClick={agregarAlCarrito} disabled={!productoSeleccionado}>
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
          {enviando && <span className="spinner" />}
          {enviando ? 'Registrando...' : 'Registrar venta'}
        </button>
      </form>

      {resultado && (
        <div className="resultado">
          <p>
            {resultado.estado === 'PENDIENTE_AUTORIZACION'
              ? `Venta #${resultado.idVenta} registrada, pendiente de autorización: se envió un código a los administradores por email.`
              : `Venta #${resultado.idVenta} confirmada.`}
          </p>
          {resultado.estado === 'CONFIRMADA' && (
            <button type="button" onClick={() => setMostrarComprobante(true)}>
              Generar comprobante
            </button>
          )}
        </div>
      )}

      {resultado && mostrarComprobante && (
        <ComprobanteInterno venta={resultado} onCerrar={() => setMostrarComprobante(false)} />
      )}

      <section>
        <h3>Confirmar descuento de venta</h3>
        <p>
          El código de autorización le llega por email al ADMIN — pedíselo (llamada, WhatsApp,
          etc.) y escribilo acá para terminar la venta.
        </p>
        <input
          type="number"
          placeholder="N° de venta"
          value={idVentaConfirmar}
          onChange={(e) => setIdVentaConfirmar(e.target.value)}
        />
        <input
          placeholder="Código"
          value={codigoConfirmar}
          onChange={(e) => setCodigoConfirmar(e.target.value)}
        />
        <button
          type="button"
          onClick={onConfirmarDescuento}
          disabled={confirmando || !idVentaConfirmar || !codigoConfirmar}
        >
          {confirmando && <span className="spinner" />}
          {confirmando ? 'Confirmando...' : 'Confirmar descuento'}
        </button>
        {errorConfirmacion && <p className="error">{errorConfirmacion}</p>}
        {mensajeConfirmacion && <p className="resultado">{mensajeConfirmacion}</p>}
      </section>
    </div>
  )
}
