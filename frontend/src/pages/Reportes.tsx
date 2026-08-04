import { useState, type FormEvent } from 'react'
import { getBalance, getProductosGanadores } from '../api/reportes'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { BalanceFinancieroResponse, ProductoRankingDTO } from '../types/api'

export function Reportes() {
  const [desde, setDesde] = useState(hoyIso())
  const [hasta, setHasta] = useState(hoyIso())
  const [ranking, setRanking] = useState<ProductoRankingDTO[]>([])
  const [balance, setBalance] = useState<BalanceFinancieroResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)

  async function buscar(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const [rankingResultado, balanceResultado] = await Promise.all([
        getProductosGanadores(desde, hasta),
        getBalance(desde, hasta),
      ])
      setRanking(rankingResultado)
      setBalance(balanceResultado)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los reportes')
    } finally {
      setCargando(false)
    }
  }

  return (
    <div>
      <h2>Reportes</h2>
      <form onSubmit={buscar}>
        <label>
          Desde
          <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" max={hoyIso()} value={hasta} onChange={(e) => setHasta(e.target.value)} />
        </label>
        <button type="submit" disabled={cargando}>
          Buscar
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      {balance && (
        <section>
          <h3>Balance financiero</h3>
          <ul>
            <li>Ingresos por ventas: {balance.ingresosPorVentas.toFixed(2)}</li>
            <li>Costo de mercadería: {balance.costoMercaderia.toFixed(2)}</li>
            <li>Gastos operativos (luz, alquiler, etc.): {balance.gastosOperativos.toFixed(2)}</li>
            <li>Comisiones pagadas a vendedores: {balance.comisionesPagadas.toFixed(2)}</li>
            <li>
              <strong>Ganancia neta: {balance.gananciaNeta.toFixed(2)}</strong>
            </li>
          </ul>
          <p>
            <em>
              Los retiros de caja no se descuentan acá (quedan solo como arqueo en la sección Caja) — esta
              ganancia neta resta costo de mercadería, gastos operativos reales y comisiones.
            </em>
          </p>
        </section>
      )}

      {ranking.length > 0 && (
        <section>
          <h3>Productos más vendidos</h3>
          <table>
            <thead>
              <tr>
                <th>Producto</th>
                <th>Cantidad vendida</th>
                <th>Total facturado</th>
              </tr>
            </thead>
            <tbody>
              {ranking.map((item) => (
                <tr key={item.idProducto}>
                  <td>{item.descripcion}</td>
                  <td>{item.cantidadVendida}</td>
                  <td>{item.totalFacturado.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  )
}
