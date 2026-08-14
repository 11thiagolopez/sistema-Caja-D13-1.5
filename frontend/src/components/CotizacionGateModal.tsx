import { useEffect, useState } from 'react'
import { Modal } from './Modal'
import { cargarCotizacion, cargarCotizacionManual } from '../api/cotizacion'
import { ApiRequestError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { CotizacionResponse } from '../types/api'

interface CotizacionGateModalProps {
  onExito: (cotizacion: CotizacionResponse) => void
}

/**
 * Bloqueo obligatorio post-login (ambos roles, sin onClose): nadie opera el sistema hasta que
 * exista una cotización del dólar cargada para hoy. Se intenta cargar sola primero (mismas dos
 * APIs de CotizacionApiClient); si las dos fallan, solo ADMIN puede tipearla a mano — VENDEDOR
 * solo puede reintentar (por si un ADMIN ya la cargó desde otra sesión mientras tanto).
 */
export function CotizacionGateModal({ onExito }: CotizacionGateModalProps) {
  const { sesion } = useAuth()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [estado, setEstado] = useState<'cargando' | 'error'>('cargando')
  const [valorManual, setValorManual] = useState('')
  const [errorManual, setErrorManual] = useState<string | null>(null)
  const [cargandoManual, setCargandoManual] = useState(false)

  async function intentarAutomatico() {
    setEstado('cargando')
    try {
      const cotizacion = await cargarCotizacion()
      onExito(cotizacion)
    } catch {
      setEstado('error')
    }
  }

  useEffect(() => {
    intentarAutomatico()
    // Solo al montar: reintentos posteriores los dispara el usuario a mano.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function onSubmitManual(e: React.FormEvent) {
    e.preventDefault()
    if (!valorManual) return
    setErrorManual(null)
    setCargandoManual(true)
    try {
      const cotizacion = await cargarCotizacionManual({ valorVenta: Number(valorManual) })
      onExito(cotizacion)
    } catch (err) {
      setErrorManual(err instanceof ApiRequestError ? err.message : 'No se pudo cargar la cotización')
    } finally {
      setCargandoManual(false)
    }
  }

  return (
    <Modal title="Cotización del dólar del día">
      {estado === 'cargando' && (
        <p>
          <span className="spinner" /> Cargando la cotización del día...
        </p>
      )}

      {estado === 'error' && esAdmin && (
        <>
          <p className="error">No se pudo obtener la cotización del dólar automáticamente. Cargala a mano:</p>
          <form onSubmit={onSubmitManual}>
            <label>
              Cotización USD venta (manual)
              <input
                type="number"
                min="0"
                step="0.01"
                value={valorManual}
                onChange={(e) => setValorManual(e.target.value)}
                autoFocus
              />
            </label>
            {errorManual && <p className="error">{errorManual}</p>}
            <button type="submit" disabled={cargandoManual || !valorManual}>
              {cargandoManual && <span className="spinner" />}
              {cargandoManual ? 'Cargando...' : 'Cargar cotización'}
            </button>
          </form>
          <button type="button" onClick={intentarAutomatico}>
            Reintentar automático
          </button>
        </>
      )}

      {estado === 'error' && !esAdmin && (
        <>
          <p className="error">Todavía no se cargó la cotización del día. Pedile a un ADMIN que la cargue.</p>
          <button type="button" onClick={intentarAutomatico}>
            Reintentar
          </button>
        </>
      )}
    </Modal>
  )
}
