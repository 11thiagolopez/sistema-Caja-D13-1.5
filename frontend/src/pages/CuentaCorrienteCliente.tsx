import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getCuentaCorrienteCliente } from '../api/clientes'
import { ApiRequestError } from '../api/client'
import type { CuentaCorrienteClienteResponse } from '../types/api'

export function CuentaCorrienteCliente() {
  const { id } = useParams<{ id: string }>()
  const [cuenta, setCuenta] = useState<CuentaCorrienteClienteResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    if (!id) return
    setCargando(true)
    setError(null)
    getCuentaCorrienteCliente(Number(id))
      .then(setCuenta)
      .catch((err) =>
        setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar la cuenta corriente'),
      )
      .finally(() => setCargando(false))
  }, [id])

  if (cargando) return <p>Cargando...</p>
  if (error) return <p className="error">{error}</p>
  if (!cuenta) return null

  return (
    <div>
      <p>
        <Link to="/clientes">← Volver a Clientes</Link>
      </p>
      <h2>Cuenta corriente — {cuenta.nombreCliente}</h2>
      <p>
        <strong>Saldo a favor: ${cuenta.saldoActual.toFixed(2)}</strong>
      </p>

      <table>
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Tipo</th>
            <th>Monto</th>
            <th>Descripción</th>
          </tr>
        </thead>
        <tbody>
          {cuenta.movimientos.length === 0 && (
            <tr>
              <td colSpan={4}>Sin movimientos todavía.</td>
            </tr>
          )}
          {cuenta.movimientos.map((m) => (
            <tr key={m.idMovimiento}>
              <td>{new Date(m.fecha).toLocaleString()}</td>
              <td>{m.tipo === 'CREDITO' ? 'Crédito' : 'Débito'}</td>
              <td>${m.monto.toFixed(2)}</td>
              <td>{m.descripcion ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
