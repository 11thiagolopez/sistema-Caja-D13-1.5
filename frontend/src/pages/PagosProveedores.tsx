import { useState, type FormEvent } from 'react'
import { getPagosPorProveedor } from '../api/compras'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { PagoProveedorDTO } from '../types/api'

export function PagosProveedores() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [pagos, setPagos] = useState<PagoProveedorDTO[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      setPagos(await getPagosPorProveedor(desde, hasta))
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los pagos a proveedores')
    } finally {
      setCargando(false)
    }
  }

  return (
    <div>
      <h2>Pagos a proveedores</h2>

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
        <table>
          <thead>
            <tr>
              <th>Proveedor</th>
              <th>Cantidad de compras</th>
              <th>Total pagado</th>
            </tr>
          </thead>
          <tbody>
            {pagos.map((p) => (
              <tr key={p.idProveedor}>
                <td>{p.nombreProveedor}</td>
                <td>{p.cantidadCompras}</td>
                <td>{p.totalPagado.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
