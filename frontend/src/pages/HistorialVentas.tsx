import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ETIQUETA_MEDIO_PAGO } from '../utils/recargo'
import { descargarVentaPdf, enviarComprobanteEmail, getVenta, getVentas } from '../api/ventas'
import { resolverPeticion } from '../api/autorizaciones'
import { getEmpleados } from '../api/empleados'
import { enviarFacturaEmail, verPdfFactura } from '../api/facturas'
import { ApiRequestError } from '../api/client'
import { BotonDescargarFactura } from '../components/BotonDescargarFactura'
import { ModalFacturacion } from '../components/ModalFacturacion'
import { ModalNotaCredito } from '../components/ModalNotaCredito'
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { EstadoVenta } from '../components/EstadoVenta'
import { hoyIso, restarDiasIso } from '../utils/date'
import { estadoVisibleTrabajo } from '../utils/estadoTrabajo'
import { formatoFechaHora, formatoPesos, numeroComprobante } from '../utils/formato'
import { descargarBlob, verBlob } from '../utils/descargarBlob'
import type {
  EmpleadoResponse,
  EstadoFactura,
  EstadoTrabajo,
  FacturaFiscalResponse,
  TipoVenta,
  VentaResponse,
} from '../types/api'

const ESTADOS_TRABAJO: EstadoTrabajo[] = [
  'AGENDADO',
  'EN_CAMINO',
  'EN_CURSO',
  'TERMINADO',
  'CERRADO_Y_COBRADO',
  'CANCELADO',
]
const ETIQUETA_ESTADO_TRABAJO: Record<string, string> = {
  AGENDADO: 'Agendado',
  EN_CAMINO: 'En camino',
  EN_CURSO: 'En curso',
  TERMINADO: 'Terminado',
  CERRADO_Y_COBRADO: 'Cerrado y cobrado',
  CANCELADO: 'Cancelado',
}


export function HistorialVentas() {
  // Desde el 1° del mes actual, igual que el resto de las pantallas de reportes/consulta
  // (Gastos, Compras, Comisiones, Presupuestos, etc.) — antes esta pantalla arrancaba filtrando
  // solo "hoy", inconsistente con todas las demás.
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [ventas, setVentas] = useState<VentaResponse[]>([])
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [confirmandoId, setConfirmandoId] = useState<number | null>(null)
  // Fila expandida con el detalle (productos, comprobante, factura y nota de crédito) — un solo
  // idVenta a la vez, plegado por default para no alargar la tabla de entrada.
  const [expandidoId, setExpandidoId] = useState<number | null>(null)

  const [filtroTipo, setFiltroTipo] = useState<'TODAS' | TipoVenta>('TODAS')
  const [filtroTecnico, setFiltroTecnico] = useState('')
  const [filtroEstadoTrabajo, setFiltroEstadoTrabajo] = useState('')
  const [filtroFactura, setFiltroFactura] = useState<'' | EstadoFactura>('')

  const [emails, setEmails] = useState<Record<number, string>>({})
  const [enviandoEmailId, setEnviandoEmailId] = useState<number | null>(null)
  const [descargandoId, setDescargandoId] = useState<number | null>(null)
  const [viendoId, setViendoId] = useState<number | null>(null)

  const [ventaAFacturar, setVentaAFacturar] = useState<VentaResponse | null>(null)
  const [ventaANotaCredito, setVentaANotaCredito] = useState<VentaResponse | null>(null)

  useEffect(() => {
    getEmpleados().then(setEmpleados)
  }, [])

  // El buscador de la barra de arriba y "Ver" en Últimas ventas llegan con ?venta=N: se muestra
  // esa venta sola, con el detalle abierto.
  const [searchParams] = useSearchParams()
  const ventaEnUrl = searchParams.get('venta')
  useEffect(() => {
    if (!ventaEnUrl) return
    const id = Number(ventaEnUrl)
    setError(null)
    setCargando(true)
    getVenta(id)
      .then((venta) => {
        setVentas([venta])
        setExpandidoId(venta.idVenta)
        const dia = venta.fecha.slice(0, 10)
        setDesde(dia)
        setHasta(dia)
      })
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : `No se encontró la venta #${id}`))
      .finally(() => setCargando(false))
  }, [ventaEnUrl])

  async function buscar(event?: FormEvent) {
    event?.preventDefault()
    setError(null)
    setCargando(true)
    try {
      setVentas(await getVentas(desde, hasta))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el historial')
    } finally {
      setCargando(false)
    }
  }

  // Trae la venta de nuevo después de facturar o acreditar: cliente, factura y estado cambian juntos.
  async function refrescarVenta(idVenta: number) {
    try {
      const actualizada = await getVenta(idVenta)
      setVentas((actual) => actual.map((v) => (v.idVenta === idVenta ? actualizada : v)))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo actualizar la venta')
    }
  }

  async function resolverDescuento(idVenta: number, accion: 'aprobar' | 'rechazar') {
    if (accion === 'rechazar' && !window.confirm(`Se va a anular la venta #${idVenta} y a devolver el stock. ¿Rechazar el descuento?`)) {
      return
    }
    setError(null)
    setConfirmandoId(idVenta)
    try {
      await resolverPeticion('DESCUENTO', idVenta, accion)
      await refrescarVenta(idVenta)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo resolver el descuento')
    } finally {
      setConfirmandoId(null)
    }
  }

  async function descargar(idVenta: number) {
    setError(null)
    setDescargandoId(idVenta)
    try {
      const blob = await descargarVentaPdf(idVenta)
      descargarBlob(blob, `comprobante-${idVenta}.pdf`)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo descargar el comprobante')
    } finally {
      setDescargandoId(null)
    }
  }

  // "Ver" y "Descargar" pegan al mismo PDF generado por el backend (TicketHtmlBuilder) — antes
  // "Ver" armaba su propia vista en HTML/CSS con ComprobanteInterno, un formato distinto del PDF
  // real y con el mismo problema de impresión en la Xprinter 58mm que costó varias vueltas
  // arreglar del lado del PDF. Unificado: acá también se pide el PDF, solo que se abre en una
  // pestaña nueva (visor nativo del navegador) en vez de forzar la descarga.
  async function ver(idVenta: number) {
    setError(null)
    setViendoId(idVenta)
    try {
      const blob = await descargarVentaPdf(idVenta)
      verBlob(blob)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir el comprobante')
    } finally {
      setViendoId(null)
    }
  }

  async function enviarPorMail(idVenta: number) {
    const email = emails[idVenta]
    if (!email) return
    setError(null)
    setEnviandoEmailId(idVenta)
    try {
      const actualizada = await enviarComprobanteEmail(idVenta, email)
      setVentas((actual) => actual.map((v) => (v.idVenta === idVenta ? actualizada : v)))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo enviar el comprobante')
    } finally {
      setEnviandoEmailId(null)
    }
  }

  const ventasFiltradas = ventas.filter((v) => {
    const matchTipo = filtroTipo === 'TODAS' || v.tipoVenta === filtroTipo
    const matchTecnico = !filtroTecnico || String(v.idEmpleadoTecnico ?? '') === filtroTecnico
    const matchEstadoTrabajo = !filtroEstadoTrabajo || v.estadoTrabajo === filtroEstadoTrabajo
    const matchFactura = !filtroFactura || (v.estado === 'CONFIRMADA' && v.estadoFactura === filtroFactura)
    return matchTipo && matchTecnico && matchEstadoTrabajo && matchFactura
  })

  function rangoRapido(rango: 'hoy' | 'semana' | 'mes') {
    const hoy = hoyIso()
    const nuevoDesde = rango === 'hoy' ? hoy : rango === 'semana' ? restarDiasIso(hoy, 6) : hoy.slice(0, 8) + '01'
    setDesde(nuevoDesde)
    setHasta(hoy)
    setError(null)
    setCargando(true)
    getVentas(nuevoDesde, hoy)
      .then(setVentas)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el historial'))
      .finally(() => setCargando(false))
  }

  const hayFiltros = Boolean(filtroTecnico || filtroEstadoTrabajo || filtroFactura || filtroTipo !== 'TODAS')
  const totalFiltrado = ventasFiltradas
    .filter((v) => v.estado === 'CONFIRMADA')
    .reduce((acc, v) => acc + v.totalVenta, 0)

  return (
    <div className="historial">
      <EncabezadoPagina
        titulo="Historial de ventas"
        subtitulo={
          ventas.length > 0
            ? `${ventasFiltradas.length} ventas · ${formatoPesos(totalFiltrado)} cobrado`
            : 'Elegí un rango de fechas y tocá Buscar'
        }
      />

      <section className="tarjeta historial-filtros">
        <form onSubmit={buscar} className="historial-fechas">
          <label>
            Desde
            <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
          </label>
          <label>
            Hasta
            <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
          </label>
          <button type="submit" disabled={cargando}>
            {cargando && <span className="spinner" aria-hidden="true" />}
            {cargando ? 'Buscando…' : 'Buscar'}
          </button>
          <div className="segmentado" role="group" aria-label="Rangos rápidos">
            <button type="button" onClick={() => rangoRapido('hoy')}>
              Hoy
            </button>
            <button type="button" onClick={() => rangoRapido('semana')}>
              Últimos 7 días
            </button>
            <button type="button" onClick={() => rangoRapido('mes')}>
              Este mes
            </button>
          </div>
        </form>

        <div className="productos-filtros-fila">
          <label>
            Facturación
            <select value={filtroFactura} onChange={(e) => setFiltroFactura(e.target.value as '' | EstadoFactura)}>
              <option value="">Todas</option>
              <option value="SIN_FACTURAR">Sin facturar</option>
              <option value="FACTURADA">Facturadas</option>
            </select>
          </label>
          <label>
            Tipo
            <select value={filtroTipo} onChange={(e) => setFiltroTipo(e.target.value as 'TODAS' | TipoVenta)}>
              <option value="TODAS">Todas</option>
              <option value="MOSTRADOR">Mostrador</option>
              <option value="DOMICILIO">Trabajo a domicilio</option>
            </select>
          </label>
          <label>
            Técnico
            <select value={filtroTecnico} onChange={(e) => setFiltroTecnico(e.target.value)}>
              <option value="">Todos</option>
              {empleados.map((e) => (
                <option key={e.idEmpleado} value={e.idEmpleado}>
                  {e.nombre}
                </option>
              ))}
            </select>
          </label>
          <label>
            Estado del trabajo
            <select value={filtroEstadoTrabajo} onChange={(e) => setFiltroEstadoTrabajo(e.target.value)}>
              <option value="">Todos</option>
              {ESTADOS_TRABAJO.map((estado) => (
                <option key={estado} value={estado}>
                  {ETIQUETA_ESTADO_TRABAJO[estado]}
                </option>
              ))}
            </select>
          </label>
          {hayFiltros && (
            <button
              type="button"
              className="btn-suave"
              onClick={() => {
                setFiltroFactura('')
                setFiltroTipo('TODAS')
                setFiltroTecnico('')
                setFiltroEstadoTrabajo('')
              }}
            >
              Limpiar filtros
            </button>
          )}
        </div>
      </section>

      {error && <p className="alerta alerta-error">{error}</p>}

      <section className="tarjeta productos-tabla historial-tabla">
        {ventasFiltradas.length === 0 ? (
          <p className="cobros-vacio">
            {cargando
              ? 'Buscando ventas…'
              : ventas.length === 0
                ? 'No hay ventas en ese rango de fechas.'
                : 'Ninguna venta coincide con los filtros.'}
          </p>
        ) : (
          <div className="tabla-scroll">
            <table>
              <thead>
                <tr>
                  <th>N°</th>
                  <th>Fecha</th>
                  <th>Tipo</th>
                  <th>Cliente</th>
                  <th>Forma de pago</th>
                  <th className="celda-numero">Total</th>
                  <th>Estado</th>
                  <th>Facturación</th>
                  <th>
                    <span className="solo-lector">Detalle</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {ventasFiltradas.map((venta) => (
                  <Fragment key={venta.idVenta}>
                    <tr className={expandidoId === venta.idVenta ? 'fila-expandida' : undefined}>
                      <td className="texto-apagado">#{venta.idVenta}</td>
                      <td className="celda-codigo">{formatoFechaHora(venta.fecha)}</td>
                      <td>
                        {venta.tipoVenta === 'DOMICILIO' ? 'Domicilio' : 'Mostrador'}
                        {venta.tipoVenta === 'DOMICILIO' && (
                          <small className="celda-sub">{estadoVisibleTrabajo(venta).etiqueta}</small>
                        )}
                      </td>
                      <td className="texto-fuerte">
                        {venta.tipoVenta === 'DOMICILIO' ? (venta.clienteNombre ?? '—') : (venta.nombreCliente ?? 'Consumidor final')}
                        {venta.tipoVenta === 'DOMICILIO' && venta.nombreTecnico && (
                          <small className="celda-sub">Técnico: {venta.nombreTecnico}</small>
                        )}
                      </td>
                      <td>{venta.medioPago ? (ETIQUETA_MEDIO_PAGO[venta.medioPago] ?? venta.medioPago) : '—'}</td>
                      <td className="celda-numero">
                        <span className={`texto-fuerte ${venta.estado === 'ANULADA' ? 'monto-anulado' : ''}`}>
                          {formatoPesos(venta.totalVenta)}
                        </span>
                        {venta.descuento > 0 && <small className="celda-sub">Descuento {formatoPesos(venta.descuento)}</small>}
                        {venta.recargoTarjeta != null && venta.recargoTarjeta > 0 && (
                          <small className="celda-sub">Cobrado {formatoPesos(venta.totalCobrado)} con recargo</small>
                        )}
                      </td>
                      <td>
                        <EstadoVenta venta={venta} />
                        {venta.tipoVenta === 'DOMICILIO' && venta.estado === 'EN_PROGRESO' && (
                          <Link className="celda-sub" to={`/ventas/domicilio?id=${venta.idVenta}`}>
                            Abrir para editar
                          </Link>
                        )}
                        {venta.estado === 'PENDIENTE_AUTORIZACION' && (
                          <span className="historial-autorizar">
                            <button
                              type="button"
                              className="btn-exito btn-chico"
                              onClick={() => resolverDescuento(venta.idVenta, 'aprobar')}
                              disabled={confirmandoId === venta.idVenta}
                            >
                              {confirmandoId === venta.idVenta && <span className="spinner" aria-hidden="true" />}
                              Aprobar
                            </button>
                            <button
                              type="button"
                              className="btn-peligro-borde btn-chico"
                              onClick={() => resolverDescuento(venta.idVenta, 'rechazar')}
                              disabled={confirmandoId === venta.idVenta}
                            >
                              Rechazar
                            </button>
                          </span>
                        )}
                      </td>
                      <td>
                        {venta.estado === 'CONFIRMADA' && (
                          <EstadoFacturacion venta={venta} onFacturar={() => setVentaAFacturar(venta)} />
                        )}
                      </td>
                      <td className="celda-numero">
                        <button
                          type="button"
                          className="btn-suave btn-chico"
                          aria-expanded={expandidoId === venta.idVenta}
                          onClick={() => setExpandidoId((actual) => (actual === venta.idVenta ? null : venta.idVenta))}
                        >
                          {expandidoId === venta.idVenta ? 'Ocultar' : 'Ver'}
                        </button>
                      </td>
                    </tr>
                    {expandidoId === venta.idVenta && (
                      <tr className="fila-detalle">
                        <td colSpan={9}>
                          <div className="detalle-venta">
                            {venta.estado === 'CONFIRMADA' && (
                              <>
                                <div className="comprobante-acciones">
                                  <button
                                    type="button"
                                    className="btn-secundario btn-chico"
                                    onClick={() => ver(venta.idVenta)}
                                    disabled={viendoId === venta.idVenta}
                                  >
                                    {viendoId === venta.idVenta ? 'Abriendo…' : 'Ver comprobante'}
                                  </button>
                                  <button
                                    type="button"
                                    className="btn-secundario btn-chico"
                                    onClick={() => descargar(venta.idVenta)}
                                    disabled={descargandoId === venta.idVenta}
                                  >
                                    {descargandoId === venta.idVenta ? 'Descargando…' : 'Descargar'}
                                  </button>
                                  <button
                                    type="button"
                                    className="btn-peligro-borde btn-chico"
                                    onClick={() => setVentaANotaCredito(venta)}
                                  >
                                    Nota de crédito
                                  </button>
                                </div>
                                <span className="confirmar-descuento">
                                  <input
                                    type="email"
                                    aria-label="Email del cliente"
                                    autoComplete="off"
                                    spellCheck={false}
                                    placeholder="Email del cliente"
                                    value={emails[venta.idVenta] ?? venta.clienteEmail ?? ''}
                                    onChange={(e) => setEmails((actual) => ({ ...actual, [venta.idVenta]: e.target.value }))}
                                  />
                                  <button
                                    type="button"
                                    className="btn-chico"
                                    onClick={() => enviarPorMail(venta.idVenta)}
                                    disabled={enviandoEmailId === venta.idVenta || !(emails[venta.idVenta] ?? venta.clienteEmail)}
                                  >
                                    {enviandoEmailId === venta.idVenta
                                      ? 'Enviando…'
                                      : venta.comprobanteEnviadoPorEmail
                                        ? 'Reenviar comprobante'
                                        : 'Enviar comprobante'}
                                  </button>
                                </span>
                                {venta.estadoFactura === 'FACTURADA' && venta.factura && <DetalleFactura factura={venta.factura} />}
                              </>
                            )}
                            {/* Proveedor por producto vendido: si una venta falla o hay un reclamo, permite
                                identificar rápido a qué proveedor corresponde cada producto sin tener que
                                ir a buscarlo a Productos (ver DetalleVentaResponse.proveedorProducto). */}
                            <table className="tabla-detalle">
                              <thead>
                                <tr>
                                  <th>Producto</th>
                                  <th className="celda-numero">Cantidad</th>
                                  <th>Proveedor</th>
                                </tr>
                              </thead>
                              <tbody>
                                {venta.detalles.map((detalle) => (
                                  <tr key={detalle.idDetalle}>
                                    <td>{detalle.descripcionProducto}</td>
                                    <td className="celda-numero">{detalle.cantidad}</td>
                                    <td>{detalle.proveedorProducto || '—'}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {ventaAFacturar && (
        <ModalFacturacion
          venta={ventaAFacturar}
          onCerrar={() => setVentaAFacturar(null)}
          onResultado={(factura) => refrescarVenta(factura.idVenta)}
        />
      )}
      {ventaANotaCredito && (
        <ModalNotaCredito
          venta={ventaANotaCredito}
          onCerrar={() => setVentaANotaCredito(null)}
          onCreada={() => refrescarVenta(ventaANotaCredito.idVenta)}
        />
      )}
    </div>
  )
}

interface EstadoFacturacionProps {
  venta: VentaResponse
  onFacturar: () => void
}

function EstadoFacturacion({ venta, onFacturar }: EstadoFacturacionProps) {
  if (venta.estadoFactura === 'FACTURADA' && venta.factura) {
    return (
      <span className="celda-pila">
        <span className="badge badge-info">Facturada</span>
        <small className="celda-sub">{numeroComprobante(venta.factura.puntoVenta, venta.factura.numero)}</small>
      </span>
    )
  }
  return (
    <span className="celda-pila">
      <span className="badge">Sin facturar</span>
      {venta.tieneNotaCredito ? (
        <small className="celda-sub">Tiene nota de crédito</small>
      ) : (
        <button type="button" className="btn-chico" onClick={onFacturar}>
          {venta.factura?.estado === 'ERROR' ? 'Reintentar' : 'Facturar'}
        </button>
      )}
      {venta.factura?.estado === 'ERROR' && <small className="error">{venta.factura.errorDetalle}</small>}
    </span>
  )
}

function DetalleFactura({ factura }: { factura: FacturaFiscalResponse }) {
  const [abriendoPdf, setAbriendoPdf] = useState(false)
  const [enviandoMail, setEnviandoMail] = useState(false)
  const [emailCliente, setEmailCliente] = useState('')
  const [mensajeExito, setMensajeExito] = useState<string | null>(null)
  const [errorFactura, setErrorFactura] = useState<string | null>(null)

  async function onVerPdf() {
    setAbriendoPdf(true)
    setErrorFactura(null)
    try {
      await verPdfFactura(factura.idVenta)
    } catch (err) {
      setErrorFactura(err instanceof Error ? err.message : 'Error al abrir el PDF')
    } finally {
      setAbriendoPdf(false)
    }
  }

  async function onEnviarMail() {
    if (!emailCliente) return
    setEnviandoMail(true)
    setErrorFactura(null)
    setMensajeExito(null)
    try {
      await enviarFacturaEmail(factura.idVenta, emailCliente)
      setMensajeExito(`Enviada a ${emailCliente}`)
      setEmailCliente('')
    } catch (err) {
      setErrorFactura(err instanceof ApiRequestError ? err.message : 'Error al enviar email')
    } finally {
      setEnviandoMail(false)
    }
  }

  return (
    <div>
      <p>
        Factura C Nº {numeroComprobante(factura.puntoVenta, factura.numero)}
        {factura.clienteNombre ? ` a ${factura.clienteNombre}` : ' a Consumidor Final'}. CAE {factura.cae}
      </p>
      <span className="confirmar-descuento">
        <button type="button" className="btn-secundario btn-chico" onClick={onVerPdf} disabled={abriendoPdf}>
          {abriendoPdf && <span className="spinner" />}
          {abriendoPdf ? 'Abriendo...' : 'Ver PDF fiscal'}
        </button>
        <BotonDescargarFactura factura={factura} onError={setErrorFactura} />
        <input
          type="email"
          placeholder="Email del cliente"
          value={emailCliente}
          onChange={(e) => setEmailCliente(e.target.value)}
        />
        <button type="button" className="btn-chico" onClick={onEnviarMail} disabled={enviandoMail || !emailCliente}>
          {enviandoMail && <span className="spinner" />}
          {enviandoMail ? 'Enviando...' : 'Enviar factura'}
        </button>
      </span>
      {errorFactura && <p className="error">{errorFactura}</p>}
      {mensajeExito && <p>{mensajeExito}</p>}
    </div>
  )
}
