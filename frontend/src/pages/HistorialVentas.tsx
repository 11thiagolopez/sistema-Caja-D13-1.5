import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ETIQUETA_MEDIO_PAGO } from '../utils/recargo'
import { descargarVentaPdf, enviarComprobanteEmail, getVenta, getVentas } from '../api/ventas'
import { resolverPeticion } from '../api/autorizaciones'
import { getEmpleados } from '../api/empleados'
import { enviarFacturaEmail, verPdfFactura } from '../api/facturas'
import { ApiRequestError } from '../api/client'
import { BotonDescargarFactura } from '../components/BotonDescargarFactura'
import { ModalFacturacion } from '../components/ModalFacturacion'
import { ModalNotaCredito } from '../components/ModalNotaCredito'
import { hoyIso } from '../utils/date'
import { formatoFechaHora, numeroComprobante } from '../utils/formato'
import { descargarBlob, verBlob } from '../utils/descargarBlob'
import type {
  EmpleadoResponse,
  EstadoFactura,
  EstadoTrabajo,
  FacturaFiscalResponse,
  TipoVenta,
  VentaResponse,
} from '../types/api'

const ESTADOS_TRABAJO: EstadoTrabajo[] = ['AGENDADO', 'EN_CURSO', 'COMPLETADO', 'COBRADO']

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

  return (
    <div>
      <h2>Historial de ventas</h2>
      <form onSubmit={buscar}>
        <label>
          Desde
          <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
        </label>
        <button type="submit" disabled={cargando}>
          {cargando && <span className="spinner" />}
          {cargando ? 'Buscando...' : 'Buscar'}
        </button>
      </form>

      <div className="agregar-producto">
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
            <option value="DOMICILIO">Domicilio</option>
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
                {estado}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Fecha</th>
            <th>Tipo</th>
            <th>Cliente / técnico</th>
            <th>Medio de pago</th>
            <th>Total</th>
            <th>Descuento</th>
            <th>Estado</th>
            <th>Facturación</th>
            <th>Detalle</th>
          </tr>
        </thead>
        <tbody>
          {ventasFiltradas.map((venta) => (
            <Fragment key={venta.idVenta}>
              <tr>
                <td>{venta.idVenta}</td>
                <td>{formatoFechaHora(venta.fecha)}</td>
                <td>
                  {venta.tipoVenta === 'DOMICILIO' ? 'Domicilio' : 'Mostrador'}
                  {venta.tipoVenta === 'DOMICILIO' && venta.estadoTrabajo ? ` (${venta.estadoTrabajo})` : ''}
                </td>
                <td>
                  {venta.tipoVenta === 'DOMICILIO'
                    ? `${venta.clienteNombre ?? '—'}${venta.nombreTecnico ? ` / ${venta.nombreTecnico}` : ''}`
                    : (venta.nombreCliente ?? '—')}
                </td>
                <td>{ETIQUETA_MEDIO_PAGO[venta.medioPago] ?? venta.medioPago}</td>
                <td>
                  {venta.totalVenta.toFixed(2)}
                  {venta.recargoTarjeta != null && venta.recargoTarjeta > 0 && (
                    <small className="recargo-nota">Cobrado {venta.totalCobrado.toFixed(2)} con recargo</small>
                  )}
                </td>
                <td>{venta.descuento.toFixed(2)}</td>
                <td>
                  {venta.estado}
                  {venta.tipoVenta === 'DOMICILIO' && venta.estado === 'EN_PROGRESO' && (
                    <Link to={`/ventas/domicilio?id=${venta.idVenta}`}> Abrir para editar</Link>
                  )}
                  {venta.estado === 'PENDIENTE_AUTORIZACION' && (
                    <span className="confirmar-descuento">
                      <button
                        type="button"
                        onClick={() => resolverDescuento(venta.idVenta, 'aprobar')}
                        disabled={confirmandoId === venta.idVenta}
                      >
                        {confirmandoId === venta.idVenta && <span className="spinner" />}
                        Aprobar
                      </button>
                      <button
                        type="button"
                        className="boton-rechazar"
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
                <td>
                  <button
                    type="button"
                    onClick={() => setExpandidoId((actual) => (actual === venta.idVenta ? null : venta.idVenta))}
                  >
                    {expandidoId === venta.idVenta ? 'Ocultar' : 'Ver'}
                  </button>
                </td>
              </tr>
              {expandidoId === venta.idVenta && (
                <tr>
                  <td colSpan={10}>
                    <div className="detalle-venta">
                      {venta.estado === 'CONFIRMADA' && (
                        <>
                          <div className="comprobante-acciones">
                            <button type="button" onClick={() => ver(venta.idVenta)} disabled={viendoId === venta.idVenta}>
                              {viendoId === venta.idVenta ? 'Abriendo...' : 'Ver comprobante'}
                            </button>
                            <button
                              type="button"
                              onClick={() => descargar(venta.idVenta)}
                              disabled={descargandoId === venta.idVenta}
                            >
                              {descargandoId === venta.idVenta ? 'Descargando...' : 'Descargar'}
                            </button>
                            <button type="button" onClick={() => setVentaANotaCredito(venta)}>
                              Nota de crédito
                            </button>
                          </div>
                          <span className="confirmar-descuento">
                            <input
                              type="email"
                              placeholder="Email del cliente"
                              value={emails[venta.idVenta] ?? venta.clienteEmail ?? ''}
                              onChange={(e) => setEmails((actual) => ({ ...actual, [venta.idVenta]: e.target.value }))}
                            />
                            <button
                              type="button"
                              onClick={() => enviarPorMail(venta.idVenta)}
                              disabled={enviandoEmailId === venta.idVenta || !(emails[venta.idVenta] ?? venta.clienteEmail)}
                            >
                              {enviandoEmailId === venta.idVenta
                                ? 'Enviando...'
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
                      <table>
                        <thead>
                          <tr>
                            <th>Producto</th>
                            <th>Cantidad</th>
                            <th>Proveedor</th>
                          </tr>
                        </thead>
                        <tbody>
                          {venta.detalles.map((detalle) => (
                            <tr key={detalle.idDetalle}>
                              <td>{detalle.descripcionProducto}</td>
                              <td>{detalle.cantidad}</td>
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
      <>
        <span className="etiqueta-factura etiqueta-facturada">Facturada</span>
        <br />
        {numeroComprobante(venta.factura.puntoVenta, venta.factura.numero)}
      </>
    )
  }
  return (
    <>
      <span className="etiqueta-factura">Sin facturar</span>
      <br />
      {venta.tieneNotaCredito ? (
        <small>Tiene nota de crédito</small>
      ) : (
        <button type="button" onClick={onFacturar}>
          {venta.factura?.estado === 'ERROR' ? 'Reintentar' : 'Facturar'}
        </button>
      )}
      {venta.factura?.estado === 'ERROR' && <p className="error">{venta.factura.errorDetalle}</p>}
    </>
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
        <button type="button" onClick={onVerPdf} disabled={abriendoPdf}>
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
        <button type="button" onClick={onEnviarMail} disabled={enviandoMail || !emailCliente}>
          {enviandoMail && <span className="spinner" />}
          {enviandoMail ? 'Enviando...' : 'Enviar factura'}
        </button>
      </span>
      {errorFactura && <p className="error">{errorFactura}</p>}
      {mensajeExito && <p>{mensajeExito}</p>}
    </div>
  )
}
