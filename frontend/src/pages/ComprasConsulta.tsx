import { useEffect, useState, type FormEvent } from 'react'
import { getCompras, getProductosMasComprados } from '../api/compras'
import { getMarcas } from '../api/marcas'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { CompraResponse, MarcaResponse, ProductoComprasRankingDTO } from '../types/api'

export function ComprasConsulta() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [compras, setCompras] = useState<CompraResponse[]>([])
  const [ranking, setRanking] = useState<ProductoComprasRankingDTO[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  const [filtroProveedor, setFiltroProveedor] = useState('')
  const [filtroDescripcion, setFiltroDescripcion] = useState('')
  const [filtroMarca, setFiltroMarca] = useState('')
  const [marcas, setMarcas] = useState<MarcaResponse[]>([])

  useEffect(() => {
    getMarcas().then(setMarcas)
  }, [])

  const nombrePorCodigoMarca = Object.fromEntries(marcas.map((m) => [m.codigo, m.nombre]))

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setCargando(true)
    try {
      const [listaCompras, listaRanking] = await Promise.all([
        getCompras(desde, hasta),
        getProductosMasComprados(desde, hasta),
      ])
      setCompras(listaCompras)
      setRanking(listaRanking)
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar las compras')
    } finally {
      setCargando(false)
    }
  }

  const comprasFiltradas = compras.filter((c) => {
    const matchProveedor = (c.nombreProveedor ?? '').toLowerCase().includes(filtroProveedor.toLowerCase())
    const matchDescripcion =
      filtroDescripcion === '' ||
      c.items.some((i) => i.descripcionProducto.toLowerCase().includes(filtroDescripcion.toLowerCase()))
    const matchMarca =
      filtroMarca === '' ||
      c.items.some((i) =>
        (nombrePorCodigoMarca[i.marcaProducto] ?? i.marcaProducto).toLowerCase().includes(filtroMarca.toLowerCase()),
      )
    return matchProveedor && matchDescripcion && matchMarca
  })

  return (
    <div>
      <h2>Consultar compras</h2>

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
          <section>
            <h3>Filtrar</h3>
            <input
              placeholder="Filtrar por proveedor"
              value={filtroProveedor}
              onChange={(e) => setFiltroProveedor(e.target.value)}
            />
            <input
              placeholder="Filtrar por descripción de producto"
              value={filtroDescripcion}
              onChange={(e) => setFiltroDescripcion(e.target.value)}
            />
            <input
              placeholder="Filtrar por marca"
              value={filtroMarca}
              onChange={(e) => setFiltroMarca(e.target.value)}
            />
          </section>

          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Proveedor</th>
                <th>Medio de pago</th>
                <th>Items</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {comprasFiltradas.map((c) => (
                <tr key={c.idCompra}>
                  <td>{c.fecha}</td>
                  <td>{c.nombreProveedor}</td>
                  <td>{c.medioPago}</td>
                  <td>{c.items.map((i) => `${i.descripcionProducto} (x${i.cantidad})`).join(', ')}</td>
                  <td>{c.totalCompra.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {ranking.length > 0 && (
            <section>
              <h3>Productos más comprados</h3>
              <table>
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th>Cantidad comprada</th>
                    <th>Total pagado</th>
                  </tr>
                </thead>
                <tbody>
                  {ranking.map((r) => (
                    <tr key={r.idProducto}>
                      <td>{r.descripcion}</td>
                      <td>{r.cantidadComprada}</td>
                      <td>{r.totalPagado.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
        </>
      )}
    </div>
  )
}
