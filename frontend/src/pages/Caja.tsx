import { useEffect, useState } from 'react'
import { getResumenDia } from '../api/caja'
import { ApiRequestError } from '../api/client'
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
        <li>
          <strong>Efectivo final: {resumen.efectivoFinal.toFixed(2)}</strong>
        </li>
        <li>Total digital: {resumen.totalDigital.toFixed(2)}</li>
        <li>
          <strong>Caja total del día: {resumen.cajaTotalDelDia.toFixed(2)}</strong>
        </li>
      </ul>
    </div>
  )
}
