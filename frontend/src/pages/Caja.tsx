import { useEffect, useState } from 'react'
import { getResumenDia } from '../api/caja'
import { ApiRequestError } from '../api/client'
import { formatoFechaHora } from '../utils/formato'
import type { ResumenDiaResponse } from '../types/api'

export function Caja() {
  const [resumen, setResumen] = useState<ResumenDiaResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    getResumenDia()
      .then(setResumen)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el resumen del día'))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) return <p>Cargando...</p>
  if (error) return <p className="error">{error}</p>
  if (!resumen) return null

  return (
    <div>
      <h2>Resumen de caja del día</h2>
      <ul>
        <li>Monto inicial: {resumen.montoInicial.toFixed(2)}</li>
        <li>Ventas efectivo: {resumen.ventasEfectivo.toFixed(2)}</li>
        <li>Ventas transferencia: {resumen.ventasTransferencia.toFixed(2)}</li>
        <li>Ventas tarjeta: {resumen.ventasTarjeta.toFixed(2)}</li>
        <li>Retiros efectivo: {resumen.retirosEfectivo.toFixed(2)}</li>
        <li>Retiros transferencia: {resumen.retirosTransferencia.toFixed(2)}</li>
        <li>Pagos a proveedores en efectivo de caja: {resumen.pagosProveedorEfectivoCaja.toFixed(2)}</li>
        <li>Pagos a proveedores por transferencia de caja: {resumen.pagosProveedorTransferenciaCaja.toFixed(2)}</li>
        <li>Gastos en efectivo de caja: {resumen.gastosEfectivoCaja.toFixed(2)}</li>
        <li>Gastos por transferencia de caja: {resumen.gastosTransferenciaCaja.toFixed(2)}</li>
        <li>
          <strong>Saldo esperado en efectivo: {resumen.efectivoFinal.toFixed(2)}</strong>
        </li>
        <li>Total digital: {resumen.totalDigital.toFixed(2)}</li>
        <li>
          <strong>Caja total del día: {resumen.cajaTotalDelDia.toFixed(2)}</strong>
        </li>
        {resumen.ventasPagadasConSaldo > 0 && (
          <li>
            Pagado con saldo de cuenta corriente (no es dinero real cobrado):{' '}
            {resumen.ventasPagadasConSaldo.toFixed(2)}
          </li>
        )}
      </ul>

      <h3>Total vendido en el día</h3>
      <p>Los trabajos a domicilio no pasan por la caja: se suman acá, pero no al arqueo de arriba.</p>
      <ul>
        <li>Ventas de mostrador: {(resumen.totalDelDia - resumen.totalTrabajosDomicilio).toFixed(2)}</li>
        <li>Trabajos a domicilio: {resumen.totalTrabajosDomicilio.toFixed(2)}</li>
        <li>
          <strong>Total del día: {resumen.totalDelDia.toFixed(2)}</strong>
        </li>
      </ul>

      <h3>Compras y pagos a proveedores</h3>
      <table>
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Proveedor</th>
            <th>Descripción</th>
            <th>Medio de pago</th>
            <th>Origen</th>
            <th>Monto</th>
            <th>Resta del saldo esperado</th>
          </tr>
        </thead>
        <tbody>
          {resumen.movimientosProveedor.length === 0 && (
            <tr>
              <td colSpan={7}>Sin movimientos hoy.</td>
            </tr>
          )}
          {resumen.movimientosProveedor.map((m) => (
            <tr key={m.idMovimiento}>
              <td>{formatoFechaHora(m.fecha)}</td>
              <td>{m.nombreProveedor}</td>
              <td>{m.descripcion ?? '—'}</td>
              <td>{m.medioPago}</td>
              <td>{m.origen ?? '—'}</td>
              <td>${m.monto.toFixed(2)}</td>
              <td>{m.restaDelSaldoEsperado ? 'Sí' : 'No'}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3>Gastos operativos</h3>
      <table>
        <thead>
          <tr>
            <th>Registrado</th>
            <th>Nombre</th>
            <th>Categoría</th>
            <th>Medio de pago</th>
            <th>Origen</th>
            <th>Importe</th>
            <th>Resta del saldo esperado</th>
          </tr>
        </thead>
        <tbody>
          {resumen.gastos.length === 0 && (
            <tr>
              <td colSpan={7}>Sin gastos hoy.</td>
            </tr>
          )}
          {resumen.gastos.map((g) => (
            <tr key={g.idGasto}>
              <td>{formatoFechaHora(g.fecha)}</td>
              <td>{g.nombre}</td>
              <td>{g.categoria ?? '—'}</td>
              <td>{g.medioPago ?? '—'}</td>
              <td>{g.origen ?? '—'}</td>
              <td>${g.importe.toFixed(2)}</td>
              <td>{g.restaDelSaldoEsperado ? 'Sí' : 'No'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
