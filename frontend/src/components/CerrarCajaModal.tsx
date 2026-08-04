import { useState } from 'react'
import { Modal } from './Modal'
import { cerrarCaja } from '../api/caja'
import { ApiRequestError } from '../api/client'

interface CerrarCajaModalProps {
  onClose: () => void
}

export function CerrarCajaModal({ onClose }: CerrarCajaModalProps) {
  const [error, setError] = useState<string | null>(null)
  const [cerrando, setCerrando] = useState(false)
  const [cerrada, setCerrada] = useState(false)

  async function onConfirmar() {
    setError(null)
    setCerrando(true)
    try {
      await cerrarCaja()
      setCerrada(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cerrar la caja')
    } finally {
      setCerrando(false)
    }
  }

  return (
    <Modal title="Cerrar caja" onClose={onClose}>
      {cerrada ? (
        <>
          <p className="resultado">Caja cerrada.</p>
          <button type="button" onClick={onClose}>
            Cerrar
          </button>
        </>
      ) : (
        <>
          <p>¿Confirmás que querés cerrar la caja del turno actual?</p>
          {error && <p className="error">{error}</p>}
          <button type="button" onClick={onConfirmar} disabled={cerrando}>
            {cerrando && <span className="spinner" />}
            {cerrando ? 'Cerrando...' : 'Cerrar caja'}
          </button>
        </>
      )}
    </Modal>
  )
}
