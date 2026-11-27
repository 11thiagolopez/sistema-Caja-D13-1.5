import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getVentas } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import { formatoPesos } from '../utils/formato'
import { ETIQUETA_MEDIO_PAGO } from '../utils/recargo'
import type { VentaResponse } from '../types/api'
import { EstadoVenta } from './EstadoVenta'

type Filtro = 'TODAS' | 'EFECTIVO' | 'TARJETA'

const FILTROS: { valor: Filtro; texto: string }[] = [
  { valor: 'TODAS', texto: 'Todas' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
  { valor: 'TARJETA', texto: 'Tarjeta' },
]

const MAXIMO = 8

function pasaFiltro(v: VentaResponse, filtro: Filtro): boolean {
  if (filtro === 'EFECTIVO') return v.medioPago === 'EFECTIVO'
  if (filtro === 'TARJETA') return v.medioPago?.startsWith('TARJETA') ?? false
  return true
}

function cliente(v: VentaResponse): string {
  if (v.tipoVenta === 'DOMICILIO') return 'Trabajo a domicilio'
  return v.nombreCliente ?? v.clienteNombre ?? 'Consumidor final'
}

/** Las ventas de hoy, las más nuevas arriba, con filtro rápido por medio de pago. */
export function UltimasVentas({ version }: { version: number }) {
  const [ventas, setVentas] = useState<VentaResponse[]>([])
  const [filtro, setFiltro] = useState<Filtro>('TODAS')
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    const hoy = hoyIso()
    getVentas(hoy, hoy)
      .then((lista) => {
        setVentas([...lista].sort((a, b) => b.fecha.localeCompare(a.fecha)))
        setError(null)
      })
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar las ventas'))
      .finally(() => setCargando(false))
  }, [version])

  const visibles = ventas.filter((v) => pasaFiltro(v, filtro)).slice(0, MAXIMO)

  return (
    <section className="tarjeta ultimas-ventas">
      <header className="ultimas-ventas-encabezado">
        <h3>Últimas ventas</h3>
        <div className="segmentado" role="group" aria-label="Filtrar por medio de pago">
          {FILTROS.map((f) => (
            <button
              key={f.valor}
              type="button"
              aria-pressed={filtro === f.valor}
              onClick={() => setFiltro(f.valor)}
            >
              {f.texto}
            </button>
          ))}
        </div>
      </header>
      {error && <p className="alerta alerta-error ultimas-ventas-mensaje">{error}</p>}
      {!error && cargando && <p className="ultimas-ventas-mensaje">Cargando…</p>}
      {!error && !cargando && visibles.length === 0 && (
        <p className="ultimas-ventas-mensaje">
          {ventas.length === 0 ? 'Todavía no hay ventas hoy.' : 'No hay ventas con ese medio de pago.'}
        </p>
      )}
      {visibles.length > 0 && (
        <div className="tabla-scroll">
          <table>
            <thead>
              <tr>
                <th>N°</th>
                <th>Cliente</th>
                <th>Forma de pago</th>
                <th>Estado</th>
                <th className="celda-numero">Total</th>
                <th>
                  <span className="solo-lector">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {visibles.map((v) => (
                <tr key={v.idVenta}>
                  <td className="texto-apagado">#{v.idVenta}</td>
                  <td className="texto-fuerte">{cliente(v)}</td>
                  <td>{v.medioPago ? ETIQUETA_MEDIO_PAGO[v.medioPago] : '—'}</td>
                  <td>
                    <EstadoVenta venta={v} />
                  </td>
                  <td className={`celda-numero texto-fuerte ${v.estado === 'ANULADA' ? 'monto-anulado' : ''}`}>
                    {formatoPesos(v.totalCobrado ?? v.totalVenta)}
                  </td>
                  <td className="celda-numero">
                    <Link to={`/ventas/historial?venta=${v.idVenta}`} aria-label={`Ver la venta #${v.idVenta}`}>
                      Ver
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {ventas.length > MAXIMO && (
        <footer className="ultimas-ventas-pie">
          <Link to="/ventas/historial">Ver todas las ventas</Link>
        </footer>
      )}
    </section>
  )
}
