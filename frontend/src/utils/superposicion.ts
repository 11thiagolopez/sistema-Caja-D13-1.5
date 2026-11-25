import type { VentaResponse } from '../types/api'

// Un trabajo con hora de inicio pero sin hora de fin se toma como de 1 hora; uno sin hora de inicio
// no se puede superponer con nada (no se sabe cuándo es).
const DURACION_POR_DEFECTO_MIN = 60

function minutos(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number)
  return h * 60 + m
}

export interface Franja {
  horaDesde: string | null
  horaHasta: string | null
}

function rango({ horaDesde, horaHasta }: Franja): [number, number] | null {
  if (!horaDesde) return null
  const desde = minutos(horaDesde)
  const hasta = horaHasta ? minutos(horaHasta) : desde + DURACION_POR_DEFECTO_MIN
  return [desde, Math.max(hasta, desde + 1)]
}

/** Se pisan si un rango empieza antes de que termine el otro (10–12 y 12–14 NO se pisan). */
export function sePisan(a: Franja, b: Franja): boolean {
  const ra = rango(a)
  const rb = rango(b)
  return ra != null && rb != null && ra[0] < rb[1] && rb[0] < ra[1]
}

/**
 * Trabajos abiertos del mismo técnico, el mismo día, cuyo horario se pisa con el dado (Etapa 2 del
 * documento: "aviso si dos se superponen"). Es un aviso, no un bloqueo: el jefe decide.
 */
export function trabajosSuperpuestos(
  trabajos: VentaResponse[],
  nuevo: Franja & { idVenta: number | null; idEmpleadoTecnico: number | null; fechaProgramada: string },
): VentaResponse[] {
  if (nuevo.idEmpleadoTecnico == null) return []
  return trabajos.filter(
    (t) =>
      t.idVenta !== nuevo.idVenta &&
      t.estado === 'EN_PROGRESO' &&
      t.idEmpleadoTecnico === nuevo.idEmpleadoTecnico &&
      t.fechaProgramada === nuevo.fechaProgramada &&
      sePisan(t, nuevo),
  )
}
