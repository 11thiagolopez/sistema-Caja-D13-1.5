import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { confirmarDescuento, descargarVentaPdf, enviarComprobanteEmail, getVentas } from '../api/ventas'
import { getEmpleados } from '../api/empleados'
import { ApiRequestError } from '../api/client'
import { ComprobanteInterno } from '../components/ComprobanteInterno'
import type { EmpleadoResponse, EstadoTrabajo, TipoVenta, VentaResponse } from '../types/api'

function hoyIso(): string {
  return new Date().toISOString().slice(0, 10)
}

function descargarBlob(blob: Blob, nombreArchivo: string) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  enlace.click()
  URL.revokeObjectURL(url)
}

const ESTADOS_TRABAJO: EstadoTrabajo[] = ['AGENDADO', 'EN_CURSO', 'COMPLETADO', 'COBRADO']

export function HistorialVentas() {
  const [desde, setDesde] = useState(hoyIso())
  const [hasta, setHasta] = useState(hoyIso())
  const [ventas, setVentas] = useState<VentaResponse[]>([])
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [codigos, setCodigos] = useState<Record<number, string>>({})
  const [comprobanteVenta, setComprobanteVenta] = useState<VentaResponse | null>(null)
  const [confirmandoId, setConfirmandoId] = useState<number | null>(null)

  const [filtroTipo, setFiltroTipo] = useState<'TODAS' | TipoVenta>('TODAS')
  const [filtroTecnico, setFiltroTecnico] = useState('')
  const [filtroEstadoTrabajo, setFiltroEstadoTrabajo] = useState('')

  const [emails, setEmails] = useState<Record<number, string>>({})
  const [enviandoEmailId, setEnviandoEmailId] = useState<number | null>(null)
  const [descargandoId, setDescargandoId] = useState<number | null>(null)

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
          </tr>
        </thead>
        <tbody>
          {ventasFiltradas.map((venta) => (
            <tr key={venta.idVenta}>
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
                {venta.tipoVenta === 'DOMICILIO' && venta.estado === 'EN_PROGRESO' && (
                  <Link to={`/ventas/domicilio?id=${venta.idVenta}`}> Abrir para editar</Link>
                )}
              </td>
              <td>
                {venta.estado === 'CONFIRMADA' && (
                  <>
                    <button type="button" onClick={() => setComprobanteVenta(venta)}>
                      Ver
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
            </tr>
          ))}
        </tbody>
      </table>

      {comprobanteVenta && (
        <ComprobanteInterno venta={comprobanteVenta} onCerrar={() => setComprobanteVenta(null)} />
      )}
    </div>
  )
}
