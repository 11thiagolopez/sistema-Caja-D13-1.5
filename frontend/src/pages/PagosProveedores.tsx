import { useEffect, useState, type FormEvent } from 'react'
import { getCompras, getPagosPorProveedor } from '../api/compras'
import { getProveedores } from '../api/proveedores'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { CompraResponse, PagoProveedorDTO, ProveedorResponse } from '../types/api'

const TODOS = 'TODOS'

export function PagosProveedores() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([])
  const [idProveedor, setIdProveedor] = useState<string>(TODOS)
  const [pagos, setPagos] = useState<PagoProveedorDTO[]>([])
  const [compras, setCompras] = useState<CompraResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  useEffect(() => {
    getProveedores().then(setProveedores)
  }, [])

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const [listaPagos, listaCompras] = await Promise.all([
        getPagosPorProveedor(desde, hasta),
        getCompras(desde, hasta),
      ])
      setPagos(listaPagos)
      setCompras(listaCompras)
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los pagos a proveedores')
    } finally {
      setCargando(false)
    }
  }

  const comprasDelProveedor =
    idProveedor === TODOS ? [] : compras.filter((c) => String(c.idProveedor) === idProveedor)
  const totalDelProveedor = comprasDelProveedor.reduce((acc, c) => acc + c.totalCompra, 0)

  return (
    <div>
      <h2>Pagos a proveedores</h2>

      <form onSubmit={onBuscar}>
        <label>
          Proveedor
          <select value={idProveedor} onChange={(e) => setIdProveedor(e.target.value)}>
            <option value={TODOS}>Todos (resumen general)</option>
            {proveedores.map((p) => (
              <option key={p.idProveedor} value={p.idProveedor}>
                {p.nombre}
              </option>
            ))}
          </select>
        </label>
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

      {buscado && idProveedor === TODOS && (
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

      {buscado && idProveedor !== TODOS && (
        <>
          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Medio de pago</th>
                <th>Items</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {comprasDelProveedor.map((c) => (
                <tr key={c.idCompra}>
                  <td>{c.fecha}</td>
                  <td>{c.medioPago}</td>
                  <td>{c.items.map((i) => `${i.descripcionProducto} (x${i.cantidad})`).join(', ')}</td>
                  <td>{c.totalCompra.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            <strong>Total pagado a este proveedor: {totalDelProveedor.toFixed(2)}</strong>
          </p>
        </>
      )}
    </div>
  )
}
