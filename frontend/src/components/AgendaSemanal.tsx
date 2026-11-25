import { useEffect, useState } from 'react'
import { ApiRequestError } from '../api/client'
import { getAgendaTrabajos } from '../api/ventas'
import { hoyIso } from '../utils/date'
import { estadoVisibleTrabajo } from '../utils/estadoTrabajo'
import type { VentaResponse } from '../types/api'

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
const TODOS = ''

// Fechas como "YYYY-MM-DD" en UTC puro: sumar días sin que un cambio de horario corra la fecha.
function sumarDias(fechaIso: string, dias: number): string {
  const [a, m, d] = fechaIso.split('-').map(Number)
  return new Date(Date.UTC(a, m - 1, d + dias)).toISOString().slice(0, 10)
}

function lunesDe(fechaIso: string): string {
  const [a, m, d] = fechaIso.split('-').map(Number)
  const diaSemana = (new Date(Date.UTC(a, m - 1, d)).getUTCDay() + 6) % 7 // 0 = lunes
  return sumarDias(fechaIso, -diaSemana)
}

function diaMes(fechaIso: string): string {
  return `${Number(fechaIso.slice(8))}/${Number(fechaIso.slice(5, 7))}`
}

function horario(t: VentaResponse): string {
  if (!t.horaDesde) return 'Sin horario'
  const desde = t.horaDesde.slice(0, 5)
  return t.horaHasta ? `${desde} – ${t.horaHasta.slice(0, 5)}` : `desde ${desde}`
}

// Los que tienen hora primero y en orden; los "sin horario" al final del día.
function porHorario(a: VentaResponse, b: VentaResponse): number {
  if (a.horaDesde && b.horaDesde) return a.horaDesde.localeCompare(b.horaDesde)
  if (a.horaDesde) return -1
  if (b.horaDesde) return 1
  return a.idVenta - b.idVenta
}

/**
 * Agenda semanal del jefe (Etapa 2 del documento del dueño): lunes a domingo de la semana de
 * `fecha`, un trabajo por tarjeta con horario, dirección, cliente y técnico. Filtro por técnico
 * para ver de un vistazo cómo tiene la semana cada uno. Tocar un día lo deja elegido (para "Nuevo
 * trabajo" con esa fecha); tocar un trabajo lo abre.
 */
export function AgendaSemanal({
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
  const [tecnico, setTecnico] = useState(TODOS)

  const lunes = lunesDe(fecha)
  const domingo = sumarDias(lunes, 6)
  const dias = Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i))
  const hoy = hoyIso()

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError(null)
    getAgendaTrabajos(lunes, domingo)
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
  }, [lunes, domingo])

  const tecnicos = [...new Map(
    trabajos.filter((t) => t.idEmpleadoTecnico != null).map((t) => [String(t.idEmpleadoTecnico), t.nombreTecnico ?? '']),
  )].sort((a, b) => a[1].localeCompare(b[1]))
  const visibles = trabajos.filter((t) => tecnico === TODOS || String(t.idEmpleadoTecnico) === tecnico)

  return (
    <section className="agenda-semanal">
      <div className="calendario-recuadro">
        <div className="calendario-mes">
          <button type="button" aria-label="Semana anterior" onClick={() => onCambiarFecha(sumarDias(lunes, -7))}>
            ‹
          </button>
          <strong>
            Semana del {diaMes(lunes)} al {diaMes(domingo)}
          </strong>
          <button type="button" aria-label="Semana siguiente" onClick={() => onCambiarFecha(sumarDias(lunes, 7))}>
            ›
          </button>
          <button type="button" onClick={() => onCambiarFecha(hoy)}>
            Hoy
          </button>
          {cargando && <span className="spinner" />}
        </div>
        <label className="agenda-filtro">
          Técnico
          <select value={tecnico} onChange={(e) => setTecnico(e.target.value)}>
            <option value={TODOS}>Todos</option>
            {tecnicos.map(([id, nombre]) => (
              <option key={id} value={id}>
                {nombre}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className="error">{error}</p>}

      <div className="agenda-dias">
        {dias.map((dia, i) => {
          const delDia = visibles.filter((t) => t.fechaProgramada === dia).sort(porHorario)
          const clases = ['agenda-dia']
          if (dia === hoy) clases.push('agenda-dia-hoy')
          if (dia === fecha) clases.push('agenda-dia-elegido')
          return (
            <div key={dia} className={clases.join(' ')}>
              <button
                type="button"
                className="agenda-dia-titulo"
                aria-pressed={dia === fecha}
                onClick={() => onCambiarFecha(dia)}
              >
                <span>{DIAS[i]}</span>
                <span className="agenda-dia-numero">{diaMes(dia)}</span>
              </button>
              {delDia.length === 0 && <p className="agenda-vacio">—</p>}
              {delDia.map((t) => {
                const { clase, etiqueta } = estadoVisibleTrabajo(t)
                return (
                  <button key={t.idVenta} type="button" className={`agenda-trabajo ${clase}`} onClick={() => onAbrir(t)}>
                    <span className="agenda-trabajo-hora">{horario(t)}</span>
                    <span className="agenda-trabajo-direccion">{t.direccionTrabajo || 'Sin dirección'}</span>
                    <span>{t.clienteNombre || 'Sin nombre'}</span>
                    <span className="agenda-trabajo-pie">
                      {t.nombreTecnico ?? 'Sin técnico'} · {etiqueta}
                    </span>
                  </button>
                )
              })}
            </div>
          )
        })}
      </div>
    </section>
  )
}
