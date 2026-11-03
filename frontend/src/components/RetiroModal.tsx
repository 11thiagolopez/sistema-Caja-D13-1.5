import { useState } from 'react'
import { Modal } from './Modal'
import { useAutorizaciones } from './AutorizacionesPendientes'
import { solicitarRetiro } from '../api/caja'
import { ApiRequestError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { MedioPago } from '../types/api'

interface RetiroModalProps {
  onClose: () => void
}

export function RetiroModal({ onClose }: RetiroModalProps) {
  const { sesion } = useAuth()
  const { agregar } = useAutorizaciones()
  const [monto, setMonto] = useState('')
  const [motivo, setMotivo] = useState('')
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [error, setError] = useState<string | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const esAdmin = sesion?.rol === 'ADMIN'

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!sesion || !monto || !motivo) return
    setError(null)
    setMensaje(null)
    setEnviando(true)
    try {
      const solicitud = await solicitarRetiro({
        idEmpleado: sesion.idEmpleado,
        monto: Number(monto),
        motivo,
        medioPago,
      })
      if (solicitud.estado === 'CONFIRMADA') {
        setMensaje(`Retiro #${solicitud.idSolicitud} registrado.`)
      } else {
        agregar({ tipo: 'RETIRO', id: solicitud.idSolicitud, monto: solicitud.monto, motivo: solicitud.motivo })
        setMensaje(
          `Retiro #${solicitud.idSolicitud} solicitado: le llegó al administrador. Podés cerrar esta ventana y seguir trabajando; la respuesta aparece en "Mis peticiones", abajo a la derecha.`,
        )
      }
      setMonto('')
      setMotivo('')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar el retiro')
    } finally {
      setEnviando(false)
    }
  }

  const textoBoton = esAdmin ? 'Registrar retiro' : 'Solicitar retiro'

  return (
    <Modal title="Retiros de dinero" onClose={onClose}>
      <form onSubmit={onSubmit}>
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
        {error && <p className="error">{error}</p>}
        {mensaje && <p className="resultado">{mensaje}</p>}
        <button type="submit" disabled={enviando || !monto || !motivo}>
          {enviando && <span className="spinner" />}
          {enviando ? 'Enviando...' : textoBoton}
        </button>
      </form>
    </Modal>
  )
}
