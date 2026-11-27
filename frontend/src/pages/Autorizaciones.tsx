import { useState } from 'react'
import { ApiRequestError } from '../api/client'
import { resolverPeticion } from '../api/autorizaciones'
import { ActivarNotificaciones } from '../components/ActivarNotificaciones'
import { useAutorizaciones } from '../components/AutorizacionesPendientes'
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { formatoFechaHora, formatoPesos } from '../utils/formato'
import type { AutorizacionPeticion } from '../types/api'

export function Autorizaciones() {
  const { peticiones, refrescarPeticiones } = useAutorizaciones()
  const [error, setError] = useState<string | null>(null)
  const [resolviendo, setResolviendo] = useState<string | null>(null)

  async function resolver(p: AutorizacionPeticion, accion: 'aprobar' | 'rechazar') {
    if (
      accion === 'rechazar' &&
      p.tipo === 'DESCUENTO' &&
      !window.confirm(`Se va a anular la venta #${p.id} y a devolver el stock. ¿Rechazar el descuento?`)
    ) {
      return
    }
    setError(null)
    setResolviendo(`${p.tipo}-${p.id}`)
    try {
      await resolverPeticion(p.tipo, p.id, accion)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo resolver la petición')
    } finally {
      // También si falló: lo más común es que otro admin ya la haya resuelto.
      await refrescarPeticiones().catch(() => {})
      setResolviendo(null)
    }
  }

  return (
    <div className="peticiones">
      <EncabezadoPagina
        titulo="Peticiones"
        subtitulo={peticiones.length === 0 ? 'No hay nada para aprobar' : `${peticiones.length} esperando tu respuesta`}
      />
      <ActivarNotificaciones />
      {error && <p className="alerta alerta-error">{error}</p>}
      {peticiones.length === 0 && <p className="cobros-vacio">No hay peticiones pendientes.</p>}
      {peticiones.map((p) => {
        const clave = `${p.tipo}-${p.id}`
        return (
          <article key={clave} className="tarjeta peticion">
            <header>
              <strong>{p.tipo === 'RETIRO' ? `Retiro #${p.id}` : `Descuento en venta #${p.id}`}</strong>
              <span className="peticion-monto">{formatoPesos(p.monto)}</span>
            </header>
            <p>{p.motivo}</p>
            <p className="peticion-detalle">
              {p.solicitante ?? 'Sin empleado'} · {formatoFechaHora(p.fecha)} · {p.medioPago}
              {p.totalVenta != null && ` · Total con descuento: ${formatoPesos(p.totalVenta)}`}
            </p>
            <div className="peticion-acciones">
              <button
                type="button"
                className="btn-exito"
                onClick={() => resolver(p, 'aprobar')}
                disabled={resolviendo === clave}
              >
                {resolviendo === clave && <span className="spinner" aria-hidden="true" />}
                Aprobar
              </button>
              <button
                type="button"
                className="btn-peligro-borde"
                onClick={() => resolver(p, 'rechazar')}
                disabled={resolviendo === clave}
              >
                Rechazar
              </button>
            </div>
          </article>
        )
      })}
    </div>
  )
}
