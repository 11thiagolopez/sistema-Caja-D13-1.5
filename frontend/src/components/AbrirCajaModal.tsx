import { useState } from 'react'
import { Modal } from './Modal'
import { abrirCaja } from '../api/caja'
import { ApiRequestError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

interface AbrirCajaModalProps {
  onClose?: () => void
  onExito: () => void
}

export function AbrirCajaModal({ onClose, onExito }: AbrirCajaModalProps) {
  const { sesion } = useAuth()
  const [montoInicial, setMontoInicial] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [abriendo, setAbriendo] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!sesion || !montoInicial) return
    setError(null)
    setAbriendo(true)
    try {
      await abrirCaja({ idEmpleado: sesion.idEmpleado, montoInicial: Number(montoInicial) })
      onExito()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir la caja')
    } finally {
      setAbriendo(false)
    }
  }

  return (
    <Modal title="Abrir caja" onClose={onClose}>
      {!onClose && <p>Hay que abrir la caja para empezar a cobrar.</p>}
      <form onSubmit={onSubmit}>
        <label>
          Monto inicial
          <input
            type="number"
            min="0"
            step="0.01"
            value={montoInicial}
            onChange={(e) => setMontoInicial(e.target.value)}
            autoFocus
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={abriendo || !montoInicial}>
          {abriendo && <span className="spinner" />}
          {abriendo ? 'Abriendo...' : 'Abrir caja'}
        </button>
      </form>
    </Modal>
  )
}
