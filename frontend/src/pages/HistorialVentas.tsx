import { useState, type FormEvent } from 'react'
import { confirmarDescuento, getVentas } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { ComprobanteInterno } from '../components/ComprobanteInterno'
import type { VentaResponse } from '../types/api'

function hoyIso(): string {
  return new Date().toISOString().slice(0, 10)
}

export function HistorialVentas() {
  const [desde, setDesde] = useState(hoyIso())
  const [hasta, setHasta] = useState(hoyIso())
  const [ventas, setVentas] = useState<VentaResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [codigos, setCodigos] = useState<Record<number, string>>({})
  const [comprobanteVenta, setComprobanteVenta] = useState<VentaResponse | null>(null)

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
    try {
      const actualizada = await confirmarDescuento({ idVenta, codigo })
      setVentas((actual) => actual.map((v) => (v.idVenta === idVenta ? actualizada : v)))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo confirmar el código')
    }
  }

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
          Buscar
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Fecha</th>
            <th>Medio de pago</th>
            <th>Total</th>
            <th>Descuento</th>
            <th>Estado</th>
            <th>Autorización</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {ventas.map((venta) => (
            <tr key={venta.idVenta}>
              <td>{venta.idVenta}</td>
              <td>{venta.fecha}</td>
              <td>{venta.medioPago}</td>
              <td>{venta.totalVenta.toFixed(2)}</td>
              <td>{venta.descuento.toFixed(2)}</td>
              <td>{venta.estado}</td>
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
                    <button type="button" onClick={() => confirmar(venta.idVenta)}>
                      Confirmar
                    </button>
                  </span>
                )}
              </td>
              <td>
                {venta.estado === 'CONFIRMADA' && (
                  <button type="button" onClick={() => setComprobanteVenta(venta)}>
                    Comprobante
                  </button>
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
