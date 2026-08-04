import { useState, type FormEvent } from 'react'
import { getComisiones } from '../api/reportes'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { ComisionEmpleadoDTO } from '../types/api'

export function ComisionesVendedores() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [comisiones, setComisiones] = useState<ComisionEmpleadoDTO[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      setComisiones(await getComisiones(desde, hasta))
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar las comisiones')
    } finally {
      setCargando(false)
    }
  }

  const totalComisiones = comisiones.reduce((acc, c) => acc + c.comisionCalculada, 0)

  return (
    <div>
      <h2>Comisiones de vendedores</h2>

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
                <th>Vendedor</th>
                <th>% Comisión</th>
                <th>Ganancia generada</th>
                <th>Cantidad de ventas</th>
                <th>Comisión a pagar</th>
              </tr>
            </thead>
            <tbody>
              {comisiones.map((c) => (
                <tr key={c.idEmpleado}>
                  <td>{c.nombreEmpleado}</td>
                  <td>{c.comisionPorcentaje ?? '—'}</td>
                  <td>{c.gananciaGenerada.toFixed(2)}</td>
                  <td>{c.cantidadVentas}</td>
                  <td>{c.comisionCalculada.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            <strong>Total comisiones del período: {totalComisiones.toFixed(2)}</strong>
          </p>
        </>
      )}
    </div>
  )
}
