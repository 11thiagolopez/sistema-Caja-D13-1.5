import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { confirmarDescuento, descargarVentaPdf, enviarComprobanteEmail, getVentas } from '../api/ventas'
import { getEmpleados } from '../api/empleados'
import { facturarVenta, getFactura, descargarPdfFactura, enviarFacturaEmail } from '../api/facturas'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import { descargarBlob, verBlob } from '../utils/descargarBlob'
import type {
  ClienteDocTipo,
  EmpleadoResponse,
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
  const [codigos, setCodigos] = useState<Record<number, string>>({})
  const [confirmandoId, setConfirmandoId] = useState<number | null>(null)
  // Fila expandida con el detalle de productos vendidos (y su proveedor) — un solo idVenta a la
  // vez, plegado por default para no alargar la tabla de entrada.
  const [expandidoId, setExpandidoId] = useState<number | null>(null)

  const [filtroTipo, setFiltroTipo] = useState<'TODAS' | TipoVenta>('TODAS')
  const [filtroTecnico, setFiltroTecnico] = useState('')
  const [filtroEstadoTrabajo, setFiltroEstadoTrabajo] = useState('')

  const [emails, setEmails] = useState<Record<number, string>>({})
  const [enviandoEmailId, setEnviandoEmailId] = useState<number | null>(null)
  const [descargandoId, setDescargandoId] = useState<number | null>(null)
  const [viendoId, setViendoId] = useState<number | null>(null)

  const [facturas, setFacturas] = useState<Record<number, FacturaFiscalResponse | null>>({})
  const [docTipos, setDocTipos] = useState<Record<number, ClienteDocTipo>>({})
  const [docNumeros, setDocNumeros] = useState<Record<number, string>>({})
  const [docNombres, setDocNombres] = useState<Record<number, string>>({})
  const [facturandoId, setFacturandoId] = useState<number | null>(null)

  useEffect(() => {
    getEmpleados().then(setEmpleados)
  }, [])

  async function buscar(event?: FormEvent) {
    event?.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const resultado = await getVentas(desde, hasta)
      setVentas(resultado)
      const confirmadas = resultado.filter((v) => v.estado === 'CONFIRMADA')
      const pares = await Promise.all(
        confirmadas.map(async (v) => [v.idVenta, await getFactura(v.idVenta)] as const),
      )
      setFacturas(Object.fromEntries(pares))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el historial')
    } finally {
      setCargando(false)
    }
  }

  async function facturar(idVenta: number) {
    const docTipo = docTipos[idVenta] ?? 99
    const docNro = docNumeros[idVenta]
    const docNombre = docNombres[idVenta]
    setError(null)
    setFacturandoId(idVenta)
    try {
      const factura = await facturarVenta(idVenta, {
        clienteDocTipo: docTipo,
        clienteDocNro: docNro,
        clienteNombre: docNombre,
      })
      setFacturas((actual) => ({ ...actual, [idVenta]: factura }))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo facturar la venta')
    } finally {
      setFacturandoId(null)
    }
  }

  async function confirmar(idVenta: number) {
    const codigo = codigos[idVenta]
    if (!codigo) return
    setError(null)
    setConfirmandoId(idVenta)
    try {
      const actualizada = await confirmarDescuento({ idVenta, codigo })
      setVentas((actual) => actual.map((v) => (v.idVenta === idVenta ? actualizada : v)))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo confirmar el código')
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
    return matchTipo && matchTecnico && matchEstadoTrabajo
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
            <th>Autorización</th>
            <th>Comprobante</th>
            <th>Enviar por mail</th>
            <th>Factura fiscal</th>
            <th>Productos</th>
          </tr>
        </thead>
        <tbody>
          {ventasFiltradas.map((venta) => (
            <Fragment key={venta.idVenta}>
              <tr>
                <td>{venta.idVenta}</td>
                <td>{venta.fecha}</td>
                <td>
                  {venta.tipoVenta === 'DOMICILIO' ? 'Domicilio' : 'Mostrador'}
                  {venta.tipoVenta === 'DOMICILIO' && venta.estadoTrabajo ? ` (${venta.estadoTrabajo})` : ''}
                </td>
                <td>
                  {venta.tipoVenta === 'DOMICILIO'
                    ? `${venta.clienteNombre ?? '—'}${venta.nombreTecnico ? ` / ${venta.nombreTecnico}` : ''}`
                    : '—'}
                </td>
                <td>{venta.medioPago}</td>
                <td>{venta.totalVenta.toFixed(2)}</td>
                <td>{venta.descuento.toFixed(2)}</td>
                <td>
                  {venta.estado}
                  {venta.tipoVenta === 'DOMICILIO' && venta.estado === 'EN_PROGRESO' && (
                    <Link to={`/ventas/domicilio?id=${venta.idVenta}`}> Abrir para editar</Link>
                  )}
                </td>
                <td>
                  {venta.estado === 'PENDIENTE_AUTORIZACION' && (
                    <span className="confirmar-descuento">
                      <input
                        placeholder="Código"
                        value={codigos[venta.idVenta] ?? ''}
                        onChange={(e) =>
                          setCodigos((actual) => ({ ...actual, [venta.idVenta]: e.target.value }))
                        }
                      />
                      <button
                        type="button"
                        onClick={() => confirmar(venta.idVenta)}
                        disabled={confirmandoId === venta.idVenta}
                      >
                        {confirmandoId === venta.idVenta && <span className="spinner" />}
                        {confirmandoId === venta.idVenta ? 'Confirmando...' : 'Confirmar'}
                      </button>
                    </span>
                  )}
                </td>
                <td>
                  {venta.estado === 'CONFIRMADA' && (
                    <>
                      <button
                        type="button"
                        onClick={() => ver(venta.idVenta)}
                        disabled={viendoId === venta.idVenta}
                      >
                        {viendoId === venta.idVenta ? 'Abriendo...' : 'Ver'}
                      </button>
                      <button
                        type="button"
                        onClick={() => descargar(venta.idVenta)}
                        disabled={descargandoId === venta.idVenta}
                      >
                        {descargandoId === venta.idVenta ? 'Descargando...' : 'Descargar'}
                      </button>
                    </>
                  )}
                </td>
                <td>
                  {venta.estado === 'CONFIRMADA' && (
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
                            ? 'Reenviar'
                            : 'Enviar'}
                      </button>
                    </span>
                  )}
                </td>
                <td>
                  {venta.estado === 'CONFIRMADA' && (
                    <FacturaFiscalCelda
                      factura={facturas[venta.idVenta]}
                      docTipo={docTipos[venta.idVenta] ?? 99}
                      docNro={docNumeros[venta.idVenta] ?? ''}
                      docNombre={docNombres[venta.idVenta] ?? ''}
                      facturando={facturandoId === venta.idVenta}
                      onCambiarDocTipo={(docTipo) =>
                        setDocTipos((actual) => ({ ...actual, [venta.idVenta]: docTipo }))
                      }
                      onCambiarDocNro={(docNro) =>
                        setDocNumeros((actual) => ({ ...actual, [venta.idVenta]: docNro }))
                      }
                      onCambiarDocNombre={(docNombre) =>
                        setDocNombres((actual) => ({ ...actual, [venta.idVenta]: docNombre }))
                      }
                      onFacturar={() => facturar(venta.idVenta)}
                    />
                  )}
                </td>
                <td>
                  <button
                    type="button"
                    onClick={() => setExpandidoId((actual) => (actual === venta.idVenta ? null : venta.idVenta))}
                  >
                    {expandidoId === venta.idVenta ? 'Ocultar' : 'Ver productos'}
                  </button>
                </td>
              </tr>
              {expandidoId === venta.idVenta && (
                <tr>
                  <td colSpan={13}>
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
                        {venta.detalles.map((detalle, i) => (
                          <tr key={i}>
                            <td>{detalle.descripcionProducto}</td>
                            <td>{detalle.cantidad}</td>
                            <td>{detalle.proveedorProducto || '—'}</td>
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
    </div>
  )
}

interface FacturaFiscalCeldaProps {
  factura: FacturaFiscalResponse | null | undefined
  docTipo: ClienteDocTipo
  docNro: string
  docNombre: string
  facturando: boolean
  onCambiarDocTipo: (docTipo: ClienteDocTipo) => void
  onCambiarDocNro: (docNro: string) => void
  onCambiarDocNombre: (docNombre: string) => void
  onFacturar: () => void
}

// Acción aparte del comprobante interno (no lo reemplaza) — emite una Factura C real vía ARCA/WSFE.
function FacturaFiscalCelda({
  factura,
  docTipo,
  docNro,
  docNombre,
  facturando,
  onCambiarDocTipo,
  onCambiarDocNro,
  onCambiarDocNombre,
  onFacturar,
}: FacturaFiscalCeldaProps) {
  // Estados locales para la descarga y envío de esta factura puntual
  const [descargando, setDescargando] = useState(false)
  const [enviandoMail, setEnviandoMail] = useState(false)
  const [emailCliente, setEmailCliente] = useState('')
  const [mensajeExito, setMensajeExito] = useState<string | null>(null)
  const [errorFactura, setErrorFactura] = useState<string | null>(null)

  async function onDescargarPdf() {
    if (!factura) return
    setDescargando(true)
    setErrorFactura(null)
    try {
      await descargarPdfFactura(factura.idVenta)
    } catch (err) {
      setErrorFactura(err instanceof Error ? err.message : 'Error al abrir el PDF')
    } finally {
      setDescargando(false)
    }
  }

  async function onEnviarMail() {
    if (!factura || !emailCliente) return
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

  if (factura?.estado === 'EMITIDA') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <span>
          Nº {String(factura.puntoVenta).padStart(4, '0')}-{String(factura.numero).padStart(8, '0')}
          <br />
          {factura.clienteNombre && (
            <>
              {factura.clienteNombre}
              <br />
            </>
          )}
          CAE {factura.cae}
        </span>
        
        <div>
          <button type="button" onClick={onDescargarPdf} disabled={descargando}>
            {descargando && <span className="spinner" />}
            {descargando ? 'Abriendo...' : 'Ver PDF Fiscal'}
          </button>
        </div>

        <span className="confirmar-descuento">
          <input
            type="email"
            placeholder="Email del cliente"
            value={emailCliente}
            onChange={(e) => setEmailCliente(e.target.value)}
          />
          <button
            type="button"
            onClick={onEnviarMail}
            disabled={enviandoMail || !emailCliente}
          >
            {enviandoMail && <span className="spinner" />}
            {enviandoMail ? 'Enviando...' : 'Enviar PDF'}
          </button>
        </span>
        
        {errorFactura && <p className="error">{errorFactura}</p>}
        {mensajeExito && <p className="resultado" style={{ margin: 0, padding: '4px' }}>{mensajeExito}</p>}
      </div>
    )
  }

  return (
    <span className="confirmar-descuento">
      <select value={docTipo} onChange={(e) => onCambiarDocTipo(Number(e.target.value) as ClienteDocTipo)}>
        <option value={99}>Consumidor Final</option>
        <option value={80}>CUIT</option>
        <option value={96}>DNI</option>
      </select>
      {docTipo !== 99 && (
        <>
          <input
            placeholder={docTipo === 80 ? 'CUIT' : 'DNI'}
            value={docNro}
            onChange={(e) => onCambiarDocNro(e.target.value)}
          />
          <input
            placeholder="Nombre / Razón social"
            value={docNombre}
            onChange={(e) => onCambiarDocNombre(e.target.value)}
          />
        </>
      )}
      <button
        type="button"
        onClick={onFacturar}
        disabled={facturando || (docTipo !== 99 && (!docNro || !docNombre))}
      >
        {facturando && <span className="spinner" />}
        {facturando ? 'Facturando...' : factura?.estado === 'ERROR' ? 'Reintentar' : 'Facturar'}
      </button>
      {factura?.estado === 'ERROR' && <p className="error">{factura.errorDetalle}</p>}
    </span>
  )
}