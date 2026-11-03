import { useState } from 'react'
import { hoyIso, restarDiasIso, type Rango } from '../../utils/date'

type Periodo = 'hoy' | 'semana' | 'mes' | 'personalizado'

const PERIODOS: { id: Periodo; nombre: string }[] = [
  { id: 'hoy', nombre: 'Hoy' },
  { id: 'semana', nombre: 'Últimos 7 días' },
  { id: 'mes', nombre: 'Este mes' },
  { id: 'personalizado', nombre: 'Personalizado' },
]

function rangoDe(periodo: Exclude<Periodo, 'personalizado'>): Rango {
  const hoy = hoyIso()
  if (periodo === 'hoy') return { desde: hoy, hasta: hoy }
  if (periodo === 'semana') return { desde: restarDiasIso(hoy, 6), hasta: hoy }
  return { desde: hoy.slice(0, 8) + '01', hasta: hoy }
}

/** Filtro global de las pantallas de reportes. Arranca en "Este mes": ver rangoDelMes. */
export function FiltroPeriodo({ rango, onCambiar }: { rango: Rango; onCambiar: (rango: Rango) => void }) {
  const [periodo, setPeriodo] = useState<Periodo>('mes')
  const [borrador, setBorrador] = useState<Rango>(rango)

  function elegirPeriodo(nuevo: Periodo) {
    setPeriodo(nuevo)
    if (nuevo === 'personalizado') {
      setBorrador(rango)
    } else {
      onCambiar(rangoDe(nuevo))
    }
  }

  // Un rango a medio cargar o invertido no se aplica: los gráficos quedan en el último válido.
  function cambiarBorrador(cambio: Partial<Rango>) {
    const nuevo = { ...borrador, ...cambio }
    setBorrador(nuevo)
    if (nuevo.desde && nuevo.hasta && nuevo.desde <= nuevo.hasta) {
      onCambiar(nuevo)
    }
  }

  const rangoInvalido = periodo === 'personalizado' && !!borrador.desde && !!borrador.hasta && borrador.desde > borrador.hasta

  return (
    <>
      <div className="reportes-filtro">
        <div className="reportes-opciones" role="group" aria-label="Período">
          {PERIODOS.map(({ id, nombre }) => (
            <button key={id} type="button" aria-pressed={periodo === id} onClick={() => elegirPeriodo(id)}>
              {nombre}
            </button>
          ))}
        </div>
        {periodo === 'personalizado' && (
          <div className="reportes-rango">
            <label>
              Desde
              <input type="date" max={hoyIso()} value={borrador.desde} onChange={(e) => cambiarBorrador({ desde: e.target.value })} />
            </label>
            <label>
              Hasta
              <input type="date" max={hoyIso()} value={borrador.hasta} onChange={(e) => cambiarBorrador({ hasta: e.target.value })} />
            </label>
          </div>
        )}
      </div>
      {rangoInvalido && <p className="error">La fecha desde no puede ser posterior a la fecha hasta.</p>}
    </>
  )
}
