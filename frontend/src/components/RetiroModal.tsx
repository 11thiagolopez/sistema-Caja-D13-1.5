import { useState } from 'react'
import { Modal } from './Modal'
import { confirmarRetiro, solicitarRetiro } from '../api/caja'
import { ApiRequestError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { MedioPago } from '../types/api'

interface RetiroModalProps {
  onClose: () => void
}

export function RetiroModal({ onClose }: RetiroModalProps) {
  const { sesion } = useAuth()
  const [monto, setMonto] = useState('')
  const [motivo, setMotivo] = useState('')
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [error, setError] = useState<string | null>(null)
  const [mensajeSolicitud, setMensajeSolicitud] = useState<string | null>(null)
  const [solicitando, setSolicitando] = useState(false)

  const [idSolicitud, setIdSolicitud] = useState('')
  const [codigo, setCodigo] = useState('')
  const [errorConfirmar, setErrorConfirmar] = useState<string | null>(null)
  const [mensajeConfirmar, setMensajeConfirmar] = useState<string | null>(null)
  const [confirmando, setConfirmando] = useState(false)

  async function onSolicitar(e: React.FormEvent) {
    e.preventDefault()
    if (!sesion || !monto || !motivo) return
    setError(null)
    setMensajeSolicitud(null)
    setSolicitando(true)
    try {
      const solicitud = await solicitarRetiro({
        idEmpleado: sesion.idEmpleado,
        monto: Number(monto),
        motivo,
        medioPago,
      })
      setMensajeSolicitud(
        `Retiro solicitado (#${solicitud.idSolicitud}). Se envió un código de autorización a los administradores.`,
      )
      setIdSolicitud(String(solicitud.idSolicitud))
      setMonto('')
      setMotivo('')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo solicitar el retiro')
    } finally {
      setSolicitando(false)
    }
  }

  async function onConfirmar(e: React.FormEvent) {
    e.preventDefault()
    if (!idSolicitud || !codigo) return
    setErrorConfirmar(null)
    setConfirmando(true)
    try {
      await confirmarRetiro({ idSolicitud: Number(idSolicitud), codigo })
      setMensajeConfirmar('Retiro confirmado.')
      setIdSolicitud('')
      setCodigo('')
    } catch (err) {
      setErrorConfirmar(err instanceof ApiRequestError ? err.message : 'No se pudo confirmar el retiro')
    } finally {
      setConfirmando(false)
    }
  }

  return (
    <Modal title="Retiros de dinero" onClose={onClose}>
      <section>
        <h4>Solicitar retiro</h4>
        <form onSubmit={onSolicitar}>
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
          {mensajeSolicitud && <p className="resultado">{mensajeSolicitud}</p>}
          <button type="submit" disabled={solicitando || !monto || !motivo}>
            {solicitando && <span className="spinner" />}
            {solicitando ? 'Solicitando...' : 'Solicitar'}
          </button>
        </form>
      </section>

      <section>
        <h4>Confirmar retiro</h4>
        <p>
          El código de autorización le llega por email al ADMIN — pedíselo y escribilo acá para
          terminar el retiro.
        </p>
        <form onSubmit={onConfirmar}>
          <input
            type="number"
            placeholder="N° de solicitud"
            value={idSolicitud}
            onChange={(e) => setIdSolicitud(e.target.value)}
          />
          <input placeholder="Código" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
          {errorConfirmar && <p className="error">{errorConfirmar}</p>}
          {mensajeConfirmar && <p className="resultado">{mensajeConfirmar}</p>}
          <button type="submit" disabled={confirmando || !idSolicitud || !codigo}>
            {confirmando && <span className="spinner" />}
            {confirmando ? 'Confirmando...' : 'Confirmar retiro'}
          </button>
        </form>
      </section>
    </Modal>
  )
}
