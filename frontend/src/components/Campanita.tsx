import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import { getNotificaciones, getNotificacionesNoLeidas, marcarNotificacionLeida } from '../api/notificaciones'
import { formatoFechaHora } from '../utils/formato'
import type { NotificacionResponse, Rol } from '../types/api'

const INTERVALO_MS = 15000

// A dónde lleva cada aviso: el jefe mira el trabajo desde la agenda, Fabián desde su propia lista.
function urlDelAviso(idVenta: number, rol: Rol | undefined): string {
  return rol === 'TECNICO' ? `/mis-trabajos/${idVenta}` : `/ventas/domicilio?id=${idVenta}`
}

// Cada uno ve solo sus propios avisos, y son de sentido opuesto: el jefe recibe lo que hace el
// técnico (en camino, llegó, terminado, nota nueva); el técnico recibe lo que le asigna el jefe
// (trabajo nuevo, reprogramado, cancelado). El nombre del botón lo deja claro para cada uno.
function textos(rol: Rol | undefined): { boton: string; vacio: string } {
  return rol === 'TECNICO'
    ? { boton: 'Mis avisos', vacio: 'Acá te avisamos cuando te asignen un trabajo nuevo, lo reprogramen o lo cancelen.' }
    : {
        boton: 'Avisos de técnicos',
        vacio: 'Acá te avisamos cuando un técnico sale para un trabajo, llega, lo termina o deja una nota.',
      }
}

/**
 * Avisos de trabajos a domicilio (ver plan-migracion.md sección 34, Parte 3): cada cambio de
 * estado, nota o reprogramación deja una fila acá, además del push. Sondea el contador cada 15s,
 * como AutorizacionesPendientes; la lista completa se pide solo al abrir el panel.
 */
export function Campanita({ rol }: { rol: Rol | undefined }) {
  const navigate = useNavigate()
  const [cantidad, setCantidad] = useState(0)
  const [abierta, setAbierta] = useState(false)
  const [notificaciones, setNotificaciones] = useState<NotificacionResponse[]>([])
  const [cargando, setCargando] = useState(false)
  const [top, setTop] = useState(64)
  const botonRef = useRef<HTMLButtonElement>(null)

  const refrescarContador = useCallback(async () => {
    setCantidad((await getNotificacionesNoLeidas()).cantidad)
  }, [])

  useEffect(() => {
    const refrescar = () => void refrescarContador().catch(() => {})
    refrescar()
    const intervalo = setInterval(refrescar, INTERVALO_MS)
    return () => clearInterval(intervalo)
  }, [refrescarContador])

  async function abrir() {
    // El panel es fijo a la pantalla: se abre justo debajo del botón, esté donde esté la barra.
    setTop((botonRef.current?.getBoundingClientRect().bottom ?? 56) + 8)
    setAbierta(true)
    setCargando(true)
    try {
      setNotificaciones(await getNotificaciones())
    } finally {
      setCargando(false)
    }
  }

  async function onTocarAviso(aviso: NotificacionResponse) {
    setAbierta(false)
    if (!aviso.leida) {
      marcarNotificacionLeida(aviso.idNotificacion).catch(() => {})
      setCantidad((actual) => Math.max(0, actual - 1))
    }
    if (aviso.idVenta != null) {
      navigate(urlDelAviso(aviso.idVenta, rol))
    }
  }

  return (
    <div className="campanita">
      <button
        ref={botonRef}
        type="button"
        className="campanita-boton"
        onClick={abrir}
        title={textos(rol).boton}
        aria-label={`${textos(rol).boton}: ${cantidad} sin leer`}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 8a6 6 0 0112 0c0 7 3 8 3 8H3s3-1 3-8" />
          <path d="M10 20a2 2 0 004 0" />
        </svg>
        {cantidad > 0 && <span className="campanita-contador">{cantidad}</span>}
      </button>
      {abierta && (
        <>
          <button type="button" className="campanita-fondo" aria-label="Cerrar avisos" onClick={() => setAbierta(false)} />
          <div className="campanita-panel" style={{ '--campanita-top': `${top}px` } as CSSProperties}>
            <header className="campanita-panel-titulo">
              <strong>{textos(rol).boton}</strong>
              <button type="button" className="modal-cerrar" aria-label="Cerrar" onClick={() => setAbierta(false)}>
                ×
              </button>
            </header>
            {cargando && <p className="peticion-detalle">Cargando...</p>}
            {!cargando && notificaciones.length === 0 && <p className="peticion-detalle">{textos(rol).vacio}</p>}
            <div className="campanita-lista">
              {notificaciones.map((n) => (
                <button
                  key={n.idNotificacion}
                  type="button"
                  className={`campanita-item${n.leida ? '' : ' campanita-item-sin-leer'}`}
                  onClick={() => onTocarAviso(n)}
                >
                  <span>{n.texto}</span>
                  <time>{formatoFechaHora(n.creadaEn)}</time>
                </button>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
