const ZONA_NEGOCIO = 'America/Argentina/Buenos_Aires'

// en-CA da el formato yyyy-mm-dd. No usar toISOString: está en UTC, y desde las 21:00 de Argentina
// devolvería la fecha de mañana.
export function hoyIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA_NEGOCIO }).format(new Date())
}

export interface Rango {
  desde: string
  hasta: string
}

export function rangoDelMes(): Rango {
  const hoy = hoyIso()
  return { desde: hoy.slice(0, 8) + '01', hasta: hoy }
}

export function restarDiasIso(fechaIso: string, dias: number): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return new Date(Date.UTC(anio, mes - 1, dia - dias)).toISOString().slice(0, 10)
}
