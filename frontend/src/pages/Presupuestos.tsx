import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { crearPresupuesto, descargarPresupuestoPdf, enviarPresupuestoEmail, getPresupuestos } from '../api/presupuestos'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { DetallePresupuestoRequest, Producto, PresupuestoResponse } from '../types/api'

function etiquetaProducto(producto: Producto): string {
  return `${producto.descripcion} - ${producto.marca ?? 'sin marca'} (${producto.codigoInterno})`
}

function descargarBlob(blob: Blob, nombreArchivo: string) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  enlace.click()
  URL.revokeObjectURL(url)
}

interface ItemCarrito extends DetallePresupuestoRequest {
  descripcionProducto: string
  stockActual: number | null
}

type Vista = 'nuevo' | 'consultar'

export function Presupuestos() {
  const { sesion } = useAuth()
  const [vista, setVista] = useState<Vista>('nuevo')

  return (
    <div>
      <h2>Presupuestos</h2>
      <p>
        Cotización informativa para un cliente: usa los mismos productos y precios que Cobros, pero no descuenta
        stock ni genera una venta.
      </p>
      <div className="tabs">
        <button type="button" onClick={() => setVista('nuevo')} disabled={vista === 'nuevo'}>
          Nuevo presupuesto
        </button>
        <button type="button" onClick={() => setVista('consultar')} disabled={vista === 'consultar'}>
          Consultar presupuestos
        </button>
      </div>

      {vista === 'nuevo' && sesion && <NuevoPresupuesto idEmpleado={sesion.idEmpleado} />}
      {vista === 'consultar' && <ConsultarPresupuestos />}
    </div>
  )
}

function NuevoPresupuesto({ idEmpleado }: { idEmpleado: number }) {
  const [productos, setProductos] = useState<Producto[]>([])
  const [productoTexto, setProductoTexto] = useState('')
  const [cantidad, setCantidad] = useState(1)
  const [carrito, setCarrito] = useState<ItemCarrito[]>([])

  const [manualDescripcion, setManualDescripcion] = useState('')
  const [manualCantidad, setManualCantidad] = useState(1)
  const [manualPrecio, setManualPrecio] = useState('')

  const [clienteNombre, setClienteNombre] = useState('')
  const [clienteEmail, setClienteEmail] = useState('')
  const [clienteTelefono, setClienteTelefono] = useState('')

  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<PresupuestoResponse | null>(null)

  const [enviandoEmail, setEnviandoEmail] = useState(false)
  const [errorEmail, setErrorEmail] = useState<string | null>(null)
  const [mensajeEmail, setMensajeEmail] = useState<string | null>(null)

  const [descargandoPdf, setDescargandoPdf] = useState(false)
  const [errorPdf, setErrorPdf] = useState<string | null>(null)

  useEffect(() => {
    getProductos()
      .then(setProductos)
      .catch(() => setError('No se pudieron cargar los productos'))
  }, [])

  const productoSeleccionado = productos.find((p) => etiquetaProducto(p) === productoTexto)

  function agregarAlCarrito() {
    if (!productoSeleccionado || cantidad <= 0) return
    const precioVenta = productoSeleccionado.precioVenta
    if (precioVenta == null) {
      setError(`"${productoSeleccionado.descripcion}" no tiene precio de venta cargado`)
      return
    }
    setCarrito((actual) => {
      const indiceExistente = actual.findIndex((item) => item.idProducto === productoSeleccionado.idProducto)
      if (indiceExistente >= 0) {
        const copia = [...actual]
        copia[indiceExistente] = { ...copia[indiceExistente], cantidad: copia[indiceExistente].cantidad + cantidad }
        return copia
      }
      return [
        ...actual,
        {
          idProducto: productoSeleccionado.idProducto,
          descripcionProducto: productoSeleccionado.descripcion,
          cantidad,
          precioUnitario: precioVenta,
          stockActual: productoSeleccionado.stockActual,
        },
      ]
    })
    setCantidad(1)
    setProductoTexto('')
  }

  function agregarItemManual() {
    if (!manualDescripcion.trim() || manualCantidad <= 0 || !manualPrecio) return
    setCarrito((actual) => [
      ...actual,
      {
        descripcion: manualDescripcion.trim(),
        descripcionProducto: manualDescripcion.trim(),
        cantidad: manualCantidad,
        precioUnitario: Number(manualPrecio),
        stockActual: null,
      },
    ])
    setManualDescripcion('')
    setManualCantidad(1)
    setManualPrecio('')
  }

  function quitarDelCarrito(index: number) {
    setCarrito((actual) => actual.filter((_, i) => i !== index))
  }

  const total = carrito.reduce((acc, item) => acc + item.cantidad * item.precioUnitario, 0)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (carrito.length === 0 || !clienteNombre) return
    setError(null)
    setEnviando(true)
    try {
      const presupuesto = await crearPresupuesto({
        idEmpleado,
        clienteNombre,
        clienteEmail: clienteEmail || undefined,
        clienteTelefono: clienteTelefono || undefined,
        detalles: carrito.map(({ idProducto, descripcion, cantidad, precioUnitario }) => ({
          idProducto,
          descripcion,
          cantidad,
          precioUnitario,
        })),
      })
      setResultado(presupuesto)
      setMensajeEmail(null)
      setErrorEmail(null)
      setErrorPdf(null)
      setCarrito([])
      setClienteNombre('')
      setClienteEmail('')
      setClienteTelefono('')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo guardar el presupuesto')
    } finally {
      setEnviando(false)
    }
  }

  async function onEnviarEmail() {
    if (!resultado) return
    setErrorEmail(null)
    setMensajeEmail(null)
    setEnviandoEmail(true)
    try {
      const actualizado = await enviarPresupuestoEmail(resultado.idPresupuesto)
      setResultado(actualizado)
      setMensajeEmail(`Presupuesto enviado a ${actualizado.clienteEmail}.`)
    } catch (err) {
      setErrorEmail(err instanceof ApiRequestError ? err.message : 'No se pudo enviar el presupuesto por email')
    } finally {
      setEnviandoEmail(false)
    }
  }

  async function onDescargarPdf() {
    if (!resultado) return
    setErrorPdf(null)
    setDescargandoPdf(true)
    try {
      const blob = await descargarPresupuestoPdf(resultado.idPresupuesto)
      descargarBlob(blob, `presupuesto-${resultado.idPresupuesto}.pdf`)
    } catch (err) {
      setErrorPdf(err instanceof ApiRequestError ? err.message : 'No se pudo descargar el PDF')
    } finally {
      setDescargandoPdf(false)
    }
  }

  return (
    <div>
      <div className="agregar-producto">
        <input
          list="productos-datalist-presupuesto"
          placeholder="Buscar producto por descripción o marca"
          value={productoTexto}
          onChange={(e) => setProductoTexto(e.target.value)}
        />
        <datalist id="productos-datalist-presupuesto">
          {productos.map((producto) => (
            <option key={producto.idProducto} value={etiquetaProducto(producto)} />
          ))}
        </datalist>
        <input type="number" min={1} value={cantidad} onChange={(e) => setCantidad(Number(e.target.value))} />
        <button type="button" onClick={agregarAlCarrito} disabled={!productoSeleccionado}>
          Agregar
        </button>
      </div>

      <div className="agregar-producto">
        <input
          placeholder="Trabajo sin precio fijo (ej. Apertura de cerradura)"
          value={manualDescripcion}
          onChange={(e) => setManualDescripcion(e.target.value)}
        />
        <input
          type="number"
          min={1}
          value={manualCantidad}
          onChange={(e) => setManualCantidad(Number(e.target.value))}
        />
        <input
          type="number"
          min={0}
          step="0.01"
          placeholder="Precio"
          value={manualPrecio}
          onChange={(e) => setManualPrecio(e.target.value)}
        />
        <button
          type="button"
          onClick={agregarItemManual}
          disabled={!manualDescripcion.trim() || !manualPrecio}
        >
          Agregar ítem manual
        </button>
      </div>

      <table>
        <thead>
          <tr>
            <th>Producto</th>
            <th>Cantidad</th>
            <th>Precio unitario</th>
            <th>Subtotal</th>
            <th>Stock actual</th>
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
              <td>{item.stockActual ?? '—'}</td>
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
          Nombre del cliente
          <input value={clienteNombre} onChange={(e) => setClienteNombre(e.target.value)} required />
        </label>
        <label>
          Email del cliente (opcional, necesario para enviarlo por mail)
          <input type="email" value={clienteEmail} onChange={(e) => setClienteEmail(e.target.value)} />
        </label>
        <label>
          Teléfono del cliente (opcional)
          <input value={clienteTelefono} onChange={(e) => setClienteTelefono(e.target.value)} />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={enviando || carrito.length === 0 || !clienteNombre}>
          {enviando && <span className="spinner" />}
          {enviando ? 'Guardando...' : 'Guardar presupuesto'}
        </button>
      </form>

      {resultado && (
        <div className="resultado">
          <p>
            Presupuesto #{resultado.idPresupuesto} guardado para {resultado.clienteNombre}. Total:{' '}
            {resultado.totalPresupuesto.toFixed(2)}.
          </p>
          <button type="button" onClick={onDescargarPdf} disabled={descargandoPdf}>
            {descargandoPdf && <span className="spinner" />}
            {descargandoPdf ? 'Descargando...' : 'Descargar PDF'}
          </button>
          {resultado.clienteEmail && (
            <button type="button" onClick={onEnviarEmail} disabled={enviandoEmail}>
              {enviandoEmail && <span className="spinner" />}
              {enviandoEmail ? 'Enviando...' : resultado.enviadoPorEmail ? 'Reenviar por email' : 'Enviar por email'}
            </button>
          )}
          {errorPdf && <p className="error">{errorPdf}</p>}
          {errorEmail && <p className="error">{errorEmail}</p>}
          {mensajeEmail && <p className="resultado">{mensajeEmail}</p>}
        </div>
      )}
    </div>
  )
}

function ConsultarPresupuestos() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [presupuestos, setPresupuestos] = useState<PresupuestoResponse[]>([])
  const [expandidoId, setExpandidoId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  const [enviandoEmailId, setEnviandoEmailId] = useState<number | null>(null)
  const [descargandoId, setDescargandoId] = useState<number | null>(null)
  const [errorEmail, setErrorEmail] = useState<string | null>(null)

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      setPresupuestos(await getPresupuestos(desde, hasta))
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los presupuestos')
    } finally {
      setCargando(false)
    }
  }

  async function onReenviar(id: number) {
    setErrorEmail(null)
    setEnviandoEmailId(id)
    try {
      const actualizado = await enviarPresupuestoEmail(id)
      setPresupuestos((actual) => actual.map((p) => (p.idPresupuesto === id ? actualizado : p)))
    } catch (err) {
      setErrorEmail(err instanceof ApiRequestError ? err.message : 'No se pudo reenviar el presupuesto')
    } finally {
      setEnviandoEmailId(null)
    }
  }

  async function onDescargar(id: number) {
    setErrorEmail(null)
    setDescargandoId(id)
    try {
      const blob = await descargarPresupuestoPdf(id)
      descargarBlob(blob, `presupuesto-${id}.pdf`)
    } catch (err) {
      setErrorEmail(err instanceof ApiRequestError ? err.message : 'No se pudo descargar el PDF')
    } finally {
      setDescargandoId(null)
    }
  }

  return (
    <div>
      <form onSubmit={onBuscar}>
        <label>
          Desde
          <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" max={hoyIso()} value={hasta} onChange={(e) => setHasta(e.target.value)} />
        </label>
        <button type="submit" disabled={cargando}>
          {cargando && <span className="spinner" />}
          {cargando ? 'Buscando...' : 'Buscar'}
        </button>
      </form>

      {error && <p className="error">{error}</p>}
      {errorEmail && <p className="error">{errorEmail}</p>}

      {buscado && (
        <table>
          <thead>
            <tr>
              <th>Presupuesto</th>
              <th>Fecha</th>
              <th>Cliente</th>
              <th>Vendedor</th>
              <th>Total</th>
              <th>Enviado por email</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {presupuestos.map((p) => (
              <Fragment key={p.idPresupuesto}>
                <tr>
                  <td>
                    <button type="button" onClick={() => setExpandidoId(expandidoId === p.idPresupuesto ? null : p.idPresupuesto)}>
                      #{p.idPresupuesto}
                    </button>
                  </td>
                  <td>{new Date(p.fecha).toLocaleString('es-AR')}</td>
                  <td>{p.clienteNombre}</td>
                  <td>{p.nombreEmpleado}</td>
                  <td>{p.totalPresupuesto.toFixed(2)}</td>
                  <td>{p.enviadoPorEmail ? 'Sí' : 'No'}</td>
                  <td>
                    <button
                      type="button"
                      onClick={() => onDescargar(p.idPresupuesto)}
                      disabled={descargandoId === p.idPresupuesto}
                    >
                      {descargandoId === p.idPresupuesto ? 'Descargando...' : 'Descargar PDF'}
                    </button>
                    {p.clienteEmail && (
                      <button
                        type="button"
                        onClick={() => onReenviar(p.idPresupuesto)}
                        disabled={enviandoEmailId === p.idPresupuesto}
                      >
                        {enviandoEmailId === p.idPresupuesto ? 'Enviando...' : 'Reenviar por email'}
                      </button>
                    )}
                  </td>
                </tr>
                {expandidoId === p.idPresupuesto && (
                  <tr>
                    <td colSpan={7}>
                      <table>
                        <thead>
                          <tr>
                            <th>Producto</th>
                            <th>Cantidad</th>
                            <th>Precio unitario</th>
                            <th>Subtotal</th>
                          </tr>
                        </thead>
                        <tbody>
                          {p.detalles.map((d, i) => (
                            <tr key={i}>
                              <td>{d.descripcionProducto}</td>
                              <td>{d.cantidad}</td>
                              <td>{d.precioUnitario.toFixed(2)}</td>
                              <td>{d.subtotal.toFixed(2)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
