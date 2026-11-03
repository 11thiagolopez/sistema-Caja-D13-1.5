import { useEffect, useState } from 'react'
import { ApiRequestError } from '../api/client'
import { getAgendaTrabajos } from '../api/ventas'
import { hoyIso } from '../utils/date'
import { estadoVisibleTrabajo } from '../utils/estadoTrabajo'
import type { VentaResponse } from '../types/api'

const DIAS_SEMANA = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do']
const MAX_PUNTOS = 3

function fechaIso(anio: number, mesBase0: number, dia: number): string {
  return new Date(Date.UTC(anio, mesBase0, dia)).toISOString().slice(0, 10)
}

/** Mes con los trabajos de cada día y, debajo, la lista del día elegido. Se vuelve a pedir al volver de un trabajo. */
export function CalendarioTrabajos({
  fecha,
  onCambiarFecha,
  onAbrir,
}: {
  fecha: string
  onCambiarFecha: (fecha: string) => void
  onAbrir: (trabajo: VentaResponse) => void
}) {
  const [trabajos, setTrabajos] = useState<VentaResponse[]>([])
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [anio, mes] = fecha.split('-').map(Number)
  const mesBase0 = mes - 1
  const primerDia = fechaIso(anio, mesBase0, 1)
  const ultimoDia = fechaIso(anio, mesBase0 + 1, 0)
  const hoy = hoyIso()

  useEffect(() => {
    // Si cambia el mes mientras viaja el pedido, la respuesta vieja no tiene que pisar a la nueva.
    let vigente = true
    setCargando(true)
    setError(null)
    getAgendaTrabajos(primerDia, ultimoDia)
      .then((agenda) => {
        if (vigente) setTrabajos(agenda)
      })
      .catch((err) => {
        if (!vigente) return
        setTrabajos([])
        setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los trabajos')
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [primerDia, ultimoDia])

  const tituloMes = new Date(Date.UTC(anio, mesBase0, 1)).toLocaleDateString('es-AR', {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })
  const huecosIniciales = (new Date(Date.UTC(anio, mesBase0, 1)).getUTCDay() + 6) % 7
  const cantidadDias = Number(ultimoDia.slice(8))
  const dias = Array.from({ length: cantidadDias }, (_, i) => fechaIso(anio, mesBase0, i + 1))
  const delDia = trabajos.filter((t) => t.fechaProgramada === fecha)

  return (
    <section className="calendario-trabajos">
      <div className="calendario-mes">
        <button type="button" aria-label="Mes anterior" onClick={() => onCambiarFecha(fechaIso(anio, mesBase0 - 1, 1))}>
          ‹
        </button>
        <strong>{tituloMes}</strong>
        <button type="button" aria-label="Mes siguiente" onClick={() => onCambiarFecha(fechaIso(anio, mesBase0 + 1, 1))}>
          ›
        </button>
        <button type="button" onClick={() => onCambiarFecha(hoy)}>
          Hoy
        </button>
        {cargando && <span className="spinner" />}
      </div>

      <div className="calendario-grilla">
        {DIAS_SEMANA.map((d) => (
          <span key={d} className="calendario-dia-semana">
            {d}
          </span>
        ))}
        {Array.from({ length: huecosIniciales }, (_, i) => (
          <span key={i} />
        ))}
        {dias.map((dia) => {
          const deEseDia = trabajos.filter((t) => t.fechaProgramada === dia)
          const clases = ['calendario-dia']
          if (dia === fecha) clases.push('calendario-dia-elegido')
          if (dia === hoy) clases.push('calendario-dia-hoy')
          return (
            <button
              key={dia}
              type="button"
              className={clases.join(' ')}
              aria-label={`${Number(dia.slice(8))} de ${tituloMes}: ${deEseDia.length} trabajos`}
              aria-pressed={dia === fecha}
              onClick={() => onCambiarFecha(dia)}
            >
              {Number(dia.slice(8))}
              <span className="calendario-puntos">
                {deEseDia.slice(0, MAX_PUNTOS).map((t) => (
                  <span key={t.idVenta} className={`trabajo-punto ${estadoVisibleTrabajo(t).clase}`} />
                ))}
                {deEseDia.length > MAX_PUNTOS && <span className="calendario-mas">+{deEseDia.length - MAX_PUNTOS}</span>}
              </span>
            </button>
          )
        })}
      </div>

      {error && <p className="error">{error}</p>}
      {!cargando && !error && delDia.length === 0 && <p>No hay trabajos ese día.</p>}
      <div className="trabajos-lista">
        {delDia.map((trabajo) => {
          const { clase, etiqueta } = estadoVisibleTrabajo(trabajo)
          return (
            <button key={trabajo.idVenta} type="button" className={`trabajo-item ${clase}`} onClick={() => onAbrir(trabajo)}>
              <span className="trabajo-datos">
                <span className="trabajo-direccion">{trabajo.direccionTrabajo || 'Sin dirección'}</span>
                <span>{trabajo.clienteNombre || 'Sin nombre'}</span>
                <span className="trabajo-detalle">
                  #{trabajo.idVenta} · ${trabajo.totalVenta.toFixed(2)}
                  {trabajo.nombreTecnico && ` · ${trabajo.nombreTecnico}`}
                </span>
              </span>
              <span className="trabajo-estado">{etiqueta}</span>
            </button>
          )
        })}
      </div>
    </section>
  )
}
