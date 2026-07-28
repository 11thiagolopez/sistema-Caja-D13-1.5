import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { abrirCaja, cerrarCaja, confirmarRetiro, getResumenDia, solicitarRetiro } from '../api/caja'
import { ApiRequestError } from '../api/client'
import type { MedioPago, ResumenDiaResponse } from '../types/api'

export function Caja() {
  const { sesion } = useAuth()
  const [resumen, setResumen] = useState<ResumenDiaResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [montoInicial, setMontoInicial] = useState('')
  const [monto, setMonto] = useState('')
  const [motivo, setMotivo] = useState('')
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [idSolicitud, setIdSolicitud] = useState<number | null>(null)
  const [codigo, setCodigo] = useState('')

  function cargarResumen() {
    setError(null)
    getResumenDia()
      .then(setResumen)
      .catch((err) =>
        setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el resumen del día'),
      )
  }

  useEffect(cargarResumen, [])

  async function onAbrirCaja() {
    if (!sesion || !montoInicial) return
    setError(null)
    try {
      await abrirCaja({ idEmpleado: sesion.idEmpleado, montoInicial: Number(montoInicial) })
      setMontoInicial('')
      cargarResumen()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir la caja')
    }
  }

  async function onCerrarCaja() {
    setError(null)
    try {
      await cerrarCaja()
      cargarResumen()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cerrar la caja')
    }
  }

  async function onSolicitarRetiro() {
    if (!sesion || !monto || !motivo) return
    setError(null)
    try {
      const solicitud = await solicitarRetiro({
        idEmpleado: sesion.idEmpleado,
        monto: Number(monto),
        motivo,
        medioPago,
      })
      setIdSolicitud(solicitud.idSolicitud)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo solicitar el retiro')
    }
  }

  async function onConfirmarRetiro() {
    if (!idSolicitud || !codigo) return
    setError(null)
    try {
      await confirmarRetiro({ idSolicitud, codigo })
      setIdSolicitud(null)
      setCodigo('')
      setMonto('')
      setMotivo('')
      cargarResumen()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo confirmar el retiro')
    }
  }

  return (
    <div>
      <h2>Caja</h2>
      {error && <p className="error">{error}</p>}

      <section>
        <h3>Sesión de caja</h3>
        <input
          type="number"
          placeholder="Monto inicial"
          value={montoInicial}
          onChange={(e) => setMontoInicial(e.target.value)}
        />
        <button type="button" onClick={onAbrirCaja}>
          Abrir caja
        </button>
        <button type="button" onClick={onCerrarCaja}>
          Cerrar caja
        </button>
      </section>

      <section>
        <h3>Solicitar retiro</h3>
        {idSolicitud === null ? (
          <>
            <input
              type="number"
              placeholder="Monto"
              value={monto}
              onChange={(e) => setMonto(e.target.value)}
            />
            <input placeholder="Motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} />
            <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
              <option value="EFECTIVO">Efectivo</option>
              <option value="TRANSFERENCIA">Transferencia</option>
              <option value="TARJETA">Tarjeta</option>
            </select>
            <button type="button" onClick={onSolicitarRetiro}>
              Solicitar
            </button>
          </>
        ) : (
          <>
            <p>Se envió un código por email a los administradores.</p>
            <input placeholder="Código" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
            <button type="button" onClick={onConfirmarRetiro}>
              Confirmar retiro
            </button>
          </>
        )}
      </section>

      {resumen && (
        <section>
          <h3>Resumen del día</h3>
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
        </section>
      )}
    </div>
  )
}
