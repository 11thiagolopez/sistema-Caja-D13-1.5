import { useState, type FormEvent } from 'react'
import { getVentasPorMarca } from '../api/reportes'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { MarcaRankingDTO } from '../types/api'

export function VentasPorMarca() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [ranking, setRanking] = useState<MarcaRankingDTO[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      setRanking(await getVentasPorMarca(desde, hasta))
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar las ventas por marca')
    } finally {
      setCargando(false)
    }
  }

  const total = ranking.reduce((acc, r) => acc + r.totalFacturado, 0)

  return (
    <div>
      <h2>Ventas por marca</h2>

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
                <th>Marca</th>
                <th>Cantidad vendida</th>
                <th>Total facturado</th>
              </tr>
            </thead>
            <tbody>
              {ranking.map((r) => (
                <tr key={r.marca}>
                  <td>{r.marca}</td>
                  <td>{r.cantidadVendida}</td>
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
