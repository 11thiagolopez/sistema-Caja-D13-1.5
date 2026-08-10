import { useState, type FormEvent } from 'react'
import { getVentasPorFormaPago } from '../api/reportes'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { FormaPagoResumenDTO } from '../types/api'

export function VentasPorFormaPago() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [resumen, setResumen] = useState<FormaPagoResumenDTO[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      setResumen(await getVentasPorFormaPago(desde, hasta))
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el resumen por forma de pago')
    } finally {
      setCargando(false)
    }
  }

  const total = resumen.reduce((acc, r) => acc + r.totalFacturado, 0)

  return (
    <div>
      <h2>Ventas por forma de pago</h2>

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

      {buscado && (
        <>
          <table>
            <thead>
              <tr>
                <th>Forma de pago</th>
                <th>Cantidad de ventas</th>
                <th>Total facturado</th>
              </tr>
            </thead>
            <tbody>
              {resumen.map((r) => (
                <tr key={r.medioPago}>
                  <td>{r.medioPago}</td>
                  <td>{r.cantidadVentas}</td>
                  <td>{r.totalFacturado.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            <strong>Total facturado: {total.toFixed(2)}</strong>
          </p>
        </>
      )}
    </div>
  )
}
