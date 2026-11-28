import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { crearPresupuesto, descargarPresupuestoPdf, enviarPresupuestoEmail, getPresupuestos } from '../api/presupuestos'
import { ApiRequestError } from '../api/client'
import { etiquetaProducto } from '../components/BuscadorProductoCarrito'
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { claseFilaStock } from '../utils/stockColor'
import { useCarrito } from '../hooks/useCarrito'
import { hoyIso } from '../utils/date'
import { formatoFechaHora, formatoPesos } from '../utils/formato'
import { descargarBlob } from '../utils/descargarBlob'
import type { DetallePresupuestoRequest, Producto, PresupuestoResponse } from '../types/api'

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
      <EncabezadoPagina
        titulo="Presupuestos"
        subtitulo="Cotización para un cliente con los mismos productos y precios que Cobros. No descuenta stock ni genera una venta."
      />
      <div className="segmentado presupuestos-vista" role="group" aria-label="Qué querés hacer">
        <button type="button" aria-pressed={vista === 'nuevo'} onClick={() => setVista('nuevo')}>
          Nuevo presupuesto
        </button>
        <button type="button" aria-pressed={vista === 'consultar'} onClick={() => setVista('consultar')}>
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
  const { carrito, agregarOFusionar, quitar: quitarDelCarrito, vaciar: vaciarCarrito, total } =
    useCarrito<ItemCarrito>()

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
    agregarOFusionar({
      idProducto: productoSeleccionado.idProducto,
      descripcionProducto: productoSeleccionado.descripcion,
      cantidad,
      precioUnitario: precioVenta,
      stockActual: productoSeleccionado.stockActual,
    })
    setCantidad(1)
    setProductoTexto('')
  }

  function agregarItemManual() {
    if (!manualDescripcion.trim() || manualCantidad <= 0 || !manualPrecio) return
    agregarOFusionar({
      descripcion: manualDescripcion.trim(),
      descripcionProducto: manualDescripcion.trim(),
      cantidad: manualCantidad,
      precioUnitario: Number(manualPrecio),
      stockActual: null,
    })
    setManualDescripcion('')
    setManualCantidad(1)
    setManualPrecio('')
  }

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
      vaciarCarrito()
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
    <div className="cobros">
      {resultado && (
        <section className="tarjeta cobro-resultado" role="status">
          <div className="cobro-resultado-titulo">
            <strong>Presupuesto #{resultado.idPresupuesto} guardado</strong>
            <span>
              Para {resultado.clienteNombre} · Total {formatoPesos(resultado.totalPresupuesto)}
            </span>
          </div>
          <div className="cobro-resultado-acciones">
            <button type="button" onClick={onDescargarPdf} disabled={descargandoPdf}>
              {descargandoPdf && <span className="spinner" aria-hidden="true" />}
              {descargandoPdf ? 'Descargando…' : 'Descargar PDF'}
            </button>
            {resultado.clienteEmail && (
              <button type="button" className="btn-secundario" onClick={onEnviarEmail} disabled={enviandoEmail}>
                {enviandoEmail && <span className="spinner" aria-hidden="true" />}
                {enviandoEmail ? 'Enviando…' : resultado.enviadoPorEmail ? 'Reenviar por email' : 'Enviar por email'}
              </button>
            )}
          </div>
          {errorPdf && <p className="error">{errorPdf}</p>}
          {errorEmail && <p className="error">{errorEmail}</p>}
          {mensajeEmail && <p className="alerta alerta-ok">{mensajeEmail}</p>}
        </section>
      )}

      <div className="cobros-grilla">
        <section className="tarjeta cobros-productos">
          <header className="tarjeta-encabezado">
            <h3>Productos y trabajos</h3>
          </header>
          <div className="cobros-carga">
            <div className="cobros-buscar">
              <span className="cobros-buscar-titulo">Producto del stock</span>
              <div className="agregar-producto">
                <input
                  className="buscador-producto-texto"
                  aria-label="Producto"
                  list="productos-datalist-presupuesto"
                  placeholder="Descripción o marca…"
                  value={productoTexto}
                  onChange={(e) => setProductoTexto(e.target.value)}
                />
                <datalist id="productos-datalist-presupuesto">
                  {productos.map((producto) => (
                    <option key={producto.idProducto} value={etiquetaProducto(producto)} />
                  ))}
                </datalist>
                <input
                  type="number"
                  inputMode="numeric"
                  className="buscador-producto-cantidad"
                  aria-label="Cantidad"
                  min={1}
                  value={cantidad}
                  onChange={(e) => setCantidad(Number(e.target.value))}
                />
                <button type="button" onClick={agregarAlCarrito} disabled={!productoSeleccionado}>
                  Agregar
                </button>
              </div>
            </div>
            <div className="cobros-buscar">
              <span className="cobros-buscar-titulo">Trabajo sin precio fijo</span>
              <div className="agregar-producto">
                <input
                  className="buscador-producto-texto"
                  aria-label="Descripción del trabajo"
                  placeholder="Ej. Apertura de cerradura"
                  value={manualDescripcion}
                  onChange={(e) => setManualDescripcion(e.target.value)}
                />
                <input
                  type="number"
                  inputMode="numeric"
                  className="buscador-producto-cantidad"
                  aria-label="Cantidad"
                  min={1}
                  value={manualCantidad}
                  onChange={(e) => setManualCantidad(Number(e.target.value))}
                />
                <input
                  type="number"
                  inputMode="decimal"
                  className="trabajo-precio-manual"
                  aria-label="Precio"
                  min={0}
                  step="0.01"
                  placeholder="Precio"
                  value={manualPrecio}
                  onChange={(e) => setManualPrecio(e.target.value)}
                />
                <button type="button" onClick={agregarItemManual} disabled={!manualDescripcion.trim() || !manualPrecio}>
                  Agregar
                </button>
              </div>
            </div>
          </div>

          {carrito.length === 0 ? (
            <p className="cobros-vacio">Agregá productos o trabajos para armar el presupuesto.</p>
          ) : (
            <div className="tabla-scroll">
              <table className="cobros-carrito">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th className="celda-numero">Cantidad</th>
                    <th className="celda-numero col-precio">Precio</th>
                    <th className="celda-numero">Subtotal</th>
                    <th className="celda-numero">Stock</th>
                    <th>
                      <span className="solo-lector">Acciones</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {carrito.map((item, index) => (
                    <tr
                      key={item.clientId}
                      className={item.stockActual != null ? claseFilaStock(item.stockActual) : undefined}
                    >
                      <td className="texto-fuerte">{item.descripcionProducto}</td>
                      <td className="celda-numero">{item.cantidad}</td>
                      <td className="celda-numero col-precio">{formatoPesos(item.precioUnitario)}</td>
                      <td className="celda-numero texto-fuerte">{formatoPesos(item.cantidad * item.precioUnitario)}</td>
                      <td className="celda-numero">{item.stockActual ?? '—'}</td>
                      <td className="celda-numero">
                        <button
                          type="button"
                          className="btn-peligro-borde btn-chico"
                          aria-label={`Quitar ${item.descripcionProducto}`}
                          onClick={() => quitarDelCarrito(index)}
                        >
                          Quitar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <form className="tarjeta cobros-panel" onSubmit={onSubmit}>
          <div className="cobros-total">
            <span>Total del presupuesto</span>
            <strong>{formatoPesos(total)}</strong>
          </div>
          <label>
            Nombre del cliente
            <input
              autoComplete="off"
              value={clienteNombre}
              onChange={(e) => setClienteNombre(e.target.value)}
              required
            />
          </label>
          <label>
            Email (opcional, para enviarlo por mail)
            <input
              type="email"
              autoComplete="off"
              spellCheck={false}
              value={clienteEmail}
              onChange={(e) => setClienteEmail(e.target.value)}
            />
          </label>
          <label>
            Teléfono (opcional)
            <input
              type="tel"
              autoComplete="off"
              value={clienteTelefono}
              onChange={(e) => setClienteTelefono(e.target.value)}
            />
          </label>
          {error && (
            <p className="alerta alerta-error" role="alert">
              {error}
            </p>
          )}
          <button
            type="submit"
            className="cobros-confirmar"
            disabled={enviando || carrito.length === 0 || !clienteNombre}
          >
            {enviando && <span className="spinner" aria-hidden="true" />}
            {enviando ? 'Guardando…' : 'Guardar presupuesto'}
          </button>
        </form>
      </div>
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
      <form onSubmit={onBuscar} className="tarjeta filtros-fechas">
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

      {error && <p className="alerta alerta-error">{error}</p>}
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
                    <button type="button" className="btn-suave btn-chico" aria-expanded={expandidoId === p.idPresupuesto} onClick={() => setExpandidoId(expandidoId === p.idPresupuesto ? null : p.idPresupuesto)}>
                      #{p.idPresupuesto}
                    </button>
                  </td>
                  <td>{formatoFechaHora(p.fecha)}</td>
                  <td>{p.clienteNombre}</td>
                  <td>{p.nombreEmpleado}</td>
                  <td>{formatoPesos(p.totalPresupuesto)}</td>
                  <td>{p.enviadoPorEmail ? <span className="badge badge-ok">Enviado</span> : <span className="badge">No enviado</span>}</td>
                  <td>
                    <button
                      type="button"
                      className="btn-secundario btn-chico"
                      onClick={() => onDescargar(p.idPresupuesto)}
                      disabled={descargandoId === p.idPresupuesto}
                    >
                      {descargandoId === p.idPresupuesto ? 'Descargando...' : 'Descargar PDF'}
                    </button>
                    {p.clienteEmail && (
                      <button
                        type="button"
                        className="btn-suave btn-chico"
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
                              <td>{formatoPesos(d.precioUnitario)}</td>
                              <td>{formatoPesos(d.subtotal)}</td>
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
