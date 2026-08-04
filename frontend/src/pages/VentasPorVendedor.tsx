import { useEffect, useState, type FormEvent } from 'react'
import { getEmpleados } from '../api/empleados'
import { getVentasPorVendedor } from '../api/reportes'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { EmpleadoResponse, VentaResponse } from '../types/api'

export function VentasPorVendedor() {
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])
  const [idEmpleado, setIdEmpleado] = useState<number | null>(null)
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [ventas, setVentas] = useState<VentaResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)
  const [buscado, setBuscado] = useState(false)

  useEffect(() => {
    getEmpleados().then((lista) => {
      setEmpleados(lista)
      setIdEmpleado(lista[0]?.idEmpleado ?? null)
    })
  }, [])

  async function onBuscar(e: FormEvent) {
    e.preventDefault()
    if (!idEmpleado) return
    setError(null)
    setCargando(true)
    try {
      setVentas(await getVentasPorVendedor(desde, hasta, idEmpleado))
      setBuscado(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar las ventas')
    } finally {
      setCargando(false)
    }
  }

  const total = ventas.reduce((acc, v) => acc + v.totalVenta, 0)

  return (
    <div>
      <h2>Ventas por vendedor</h2>

      <form onSubmit={onBuscar}>
        <label>
          Vendedor
          <select value={idEmpleado ?? ''} onChange={(e) => setIdEmpleado(Number(e.target.value))}>
            {empleados.map((emp) => (
              <option key={emp.idEmpleado} value={emp.idEmpleado}>
                {emp.nombre} ({emp.rol})
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
        <button type="submit" disabled={cargando || !idEmpleado}>
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
                <th>Venta</th>
                <th>Fecha</th>
                <th>Medio de pago</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {ventas.map((v) => (
                <tr key={v.idVenta}>
                  <td>#{v.idVenta}</td>
                  <td>{new Date(v.fecha).toLocaleString('es-AR')}</td>
                  <td>{v.medioPago}</td>
                  <td>{v.totalVenta.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            <strong>Total vendido: {total.toFixed(2)}</strong>
          </p>
        </>
      )}
    </div>
  )
}
