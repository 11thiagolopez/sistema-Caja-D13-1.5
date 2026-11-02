import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getVenta, getVentas } from '../api/ventas'
import {
  crearNotaCredito,
  descargarNotaCreditoFacturaPdf,
  descargarNotaCreditoPdf,
  emitirNotaCreditoFiscal,
} from '../api/notasCredito'
import { ApiRequestError } from '../api/client'
import { descargarBlob, verBlob } from '../utils/descargarBlob'
import { BuscadorClienteSelect } from '../components/BuscadorClienteSelect'
import { hoyIso } from '../utils/date'
import { formatoFechaHora } from '../utils/formato'
import type { AlcanceNotaCredito, ClienteResponse, MotivoNotaCredito, NotaCreditoResponse, VentaResponse } from '../types/api'

const MOTIVOS: { value: MotivoNotaCredito; label: string }[] = [
  { value: 'DEVOLUCION_MERCADERIA', label: 'Devolución de mercadería (devuelve stock)' },
  { value: 'ANULACION_TOTAL', label: 'Anulación total (devuelve stock)' },
  { value: 'ERROR_TIPEO', label: 'Error de tipeo (no toca stock)' },
  { value: 'DESCUENTO_POSTERIOR', label: 'Descuento posterior (no toca stock)' },
]

export function NotaCredito() {
  const { sesion } = useAuth()
  const [searchParams] = useSearchParams()

  // Paso 1: buscar la venta original.
  const [idVentaBuscado, setIdVentaBuscado] = useState('')
  const [desde, setDesde] = useState(hoyIso())
  const [hasta, setHasta] = useState(hoyIso())
  const [resultadosRango, setResultadosRango] = useState<VentaResponse[]>([])
  const [venta, setVenta] = useState<VentaResponse | null>(null)
  const [errorBusqueda, setErrorBusqueda] = useState<string | null>(null)
  const [buscando, setBuscando] = useState(false);

  // Paso 2: motivo + alcance + líneas (si es parcial) + cliente.
  const [motivo, setMotivo] = useState<MotivoNotaCredito>('DEVOLUCION_MERCADERIA')
  const [alcance, setAlcance] = useState<AlcanceNotaCredito>('TOTAL')
  const [cantidades, setCantidades] = useState<Record<number, number>>({})
  const [cliente, setCliente] = useState<ClienteResponse | null>(null)

  // Paso 3: confirmación.
  const [confirmando, setConfirmando] = useState(false)
  const [errorConfirmar, setErrorConfirmar] = useState<string | null>(null)
  const [resultado, setResultado] = useState<NotaCreditoResponse | null>(null)
  const [emitiendoFiscal, setEmitiendoFiscal] = useState(false)
  const [errorFiscal, setErrorFiscal] = useState<string | null>(null)

  async function cargarVenta(id: number) {
    setErrorBusqueda(null)
    setBuscando(true)
    try {
      const v = await getVenta(id)
      if (v.estado !== 'CONFIRMADA') {
        setErrorBusqueda(`La venta #${id} no está CONFIRMADA (estado: ${v.estado}) — no se le puede emitir una nota de crédito`)
        return
      }
      seleccionarVenta(v)
    } catch (err) {
      setErrorBusqueda(err instanceof ApiRequestError ? err.message : 'No se pudo encontrar esa venta')
    } finally {
      setBuscando(false)
    }
  }

  useEffect(() => {
    const idParam = searchParams.get('idVenta')
    if (idParam) {
      cargarVenta(Number(idParam))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function seleccionarVenta(v: VentaResponse) {
    setVenta(v)
    setResultadosRango([])
    const porDefecto: Record<number, number> = {}
    v.detalles.forEach((d) => (porDefecto[d.idDetalle] = d.cantidad))
    setCantidades(porDefecto)
  }

  async function buscarPorNumero() {
    if (!idVentaBuscado) return
    await cargarVenta(Number(idVentaBuscado))
  }

  async function buscarPorRango() {
    setErrorBusqueda(null)
    setBuscando(true)
    try {
      const ventas = await getVentas(desde, hasta)
      setResultadosRango(ventas.filter((v) => v.estado === 'CONFIRMADA'))
    } catch (err) {
      setErrorBusqueda(err instanceof ApiRequestError ? err.message : 'No se pudieron buscar las ventas')
    } finally {
      setBuscando(false)
    }
  }

  async function onConfirmar() {
    if (!venta || !sesion) return
    if (!cliente) {
      setErrorConfirmar('Elegí o creá un cliente antes de confirmar')
      return
    }
    setErrorConfirmar(null)
    setConfirmando(true)
    try {
      const nc = await crearNotaCredito({
        idVentaOriginal: venta.idVenta,
        idEmpleado: sesion.idEmpleado,
        idCliente: cliente.idCliente,
        motivo,
        alcance,
        detalles:
          alcance === 'PARCIAL'
            ? venta.detalles
                .filter((d) => (cantidades[d.idDetalle] ?? 0) > 0)
                .map((d) => ({ idDetalleVentaOriginal: d.idDetalle, cantidad: cantidades[d.idDetalle] }))
            : undefined,
      })
      setResultado(nc)
    } catch (err) {
      setErrorConfirmar(err instanceof ApiRequestError ? err.message : 'No se pudo crear la nota de crédito')
    } finally {
      setConfirmando(false)
    }
  }

  async function onVerPdf() {
    if (!resultado) return
    verBlob(await descargarNotaCreditoPdf(resultado.idNotaCredito))
  }

  async function onDescargarPdf() {
    if (!resultado) return
    descargarBlob(await descargarNotaCreditoPdf(resultado.idNotaCredito), `nota-credito-${resultado.idNotaCredito}.pdf`)
  }

  async function onEmitirFiscal() {
    if (!resultado) return
    setErrorFiscal(null)
    setEmitiendoFiscal(true)
    try {
      setResultado(await emitirNotaCreditoFiscal(resultado.idNotaCredito))
    } catch (err) {
      setErrorFiscal(err instanceof ApiRequestError ? err.message : 'No se pudo emitir ante ARCA')
    } finally {
      setEmitiendoFiscal(false)
    }
  }

  async function onVerFacturaPdf() {
    if (!resultado) return
    verBlob(await descargarNotaCreditoFacturaPdf(resultado.idNotaCredito))
  }

  if (resultado) {
    return (
      <div>
        <h2>Nota de crédito #{resultado.idNotaCredito} creada</h2>
        <p>
          Cliente: {resultado.nombreCliente} — Monto total: ${resultado.montoTotal.toFixed(2)} — Venta original: #
          {resultado.idVentaOriginal}
        </p>
        <button type="button" onClick={onVerPdf}>
          Ver PDF
        </button>
        <button type="button" onClick={onDescargarPdf}>
          Descargar PDF
        </button>

        {resultado.esFiscal && (
          <section>
            <h3>Emisión fiscal (ARCA)</h3>
            {resultado.estado === 'PENDIENTE_FISCAL' && (
              <>
                <p>La venta original tiene factura fiscal — esta nota de crédito todavía no se emitió ante ARCA.</p>
                <button type="button" onClick={onEmitirFiscal} disabled={emitiendoFiscal}>
                  {emitiendoFiscal && <span className="spinner" />}
                  {emitiendoFiscal ? 'Emitiendo...' : 'Emitir ante ARCA'}
                </button>
              </>
            )}
            {resultado.estado === 'ERROR' && (
              <>
                <p className="error">ARCA rechazó la emisión: {resultado.errorDetalle}</p>
                <button type="button" onClick={onEmitirFiscal} disabled={emitiendoFiscal}>
                  {emitiendoFiscal ? 'Reintentando...' : 'Reintentar'}
                </button>
              </>
            )}
            {resultado.estado === 'EMITIDA' && resultado.cae && (
              <>
                <p>
                  CAE: {resultado.cae} (vence {resultado.caeVencimiento}) — Comprobante {resultado.puntoVenta}-
                  {resultado.numero}
                </p>
                <button type="button" onClick={onVerFacturaPdf}>
                  Ver factura fiscal
                </button>
              </>
            )}
            {errorFiscal && <p className="error">{errorFiscal}</p>}
          </section>
        )}
      </div>
    )
  }

  return (
    <div>
      <h2>Nueva nota de crédito</h2>

      {!venta && (
        <section>
          <h3>1. Buscar la venta original</h3>
          <label>
            Número de venta
            <input value={idVentaBuscado} onChange={(e) => setIdVentaBuscado(e.target.value)} />
          </label>
          <button type="button" onClick={buscarPorNumero} disabled={buscando}>
            Buscar
          </button>

          <p>— o por rango de fecha —</p>
          <label>
            Desde
            <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
          </label>
          <label>
            Hasta
            <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
          </label>
          <button type="button" onClick={buscarPorRango} disabled={buscando}>
            Buscar por fecha
          </button>

          {errorBusqueda && <p className="error">{errorBusqueda}</p>}

          {resultadosRango.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Fecha</th>
                  <th>Cliente</th>
                  <th>Total</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {resultadosRango.map((v) => (
                  <tr key={v.idVenta}>
                    <td>{v.idVenta}</td>
                    <td>{formatoFechaHora(v.fecha)}</td>
                    <td>{v.clienteNombre ?? '—'}</td>
                    <td>${v.totalVenta.toFixed(2)}</td>
                    <td>
                      <button type="button" onClick={() => seleccionarVenta(v)}>
                        Elegir
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {venta && (
        <section>
          <h3>2. Motivo y alcance — venta #{venta.idVenta}</h3>
          <button type="button" onClick={() => setVenta(null)}>
            ← Elegir otra venta
          </button>

          <label>
            Motivo
            <select value={motivo} onChange={(e) => setMotivo(e.target.value as MotivoNotaCredito)}>
              {MOTIVOS.map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </select>
          </label>

          <fieldset>
            <label>
              <input type="radio" checked={alcance === 'TOTAL'} onChange={() => setAlcance('TOTAL')} />
              Total (toda la venta)
            </label>
            <label>
              <input type="radio" checked={alcance === 'PARCIAL'} onChange={() => setAlcance('PARCIAL')} />
              Parcial (elegir cantidades por línea)
            </label>
          </fieldset>

          <table>
            <thead>
              <tr>
                <th>Producto</th>
                <th>Cantidad vendida</th>
                {alcance === 'PARCIAL' && <th>Cantidad a acreditar</th>}
                <th>Precio unitario</th>
              </tr>
            </thead>
            <tbody>
              {venta.detalles.map((d) => (
                <tr key={d.idDetalle}>
                  <td>{d.descripcionProducto}</td>
                  <td>{d.cantidad}</td>
                  {alcance === 'PARCIAL' && (
                    <td>
                      <input
                        type="number"
                        min={0}
                        max={d.cantidad}
                        value={cantidades[d.idDetalle] ?? 0}
                        onChange={(e) =>
                          setCantidades((actual) => ({ ...actual, [d.idDetalle]: Number(e.target.value) }))
                        }
                      />
                    </td>
                  )}
                  <td>${d.precioUnitario.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <BuscadorClienteSelect idClienteSeleccionado={cliente?.idCliente ?? null} onSeleccionar={setCliente} />

          {errorConfirmar && <p className="error">{errorConfirmar}</p>}
          <button type="button" onClick={onConfirmar} disabled={confirmando}>
            {confirmando && <span className="spinner" />}
            {confirmando ? 'Creando...' : 'Confirmar nota de crédito'}
          </button>
        </section>
      )}
    </div>
  )
}
