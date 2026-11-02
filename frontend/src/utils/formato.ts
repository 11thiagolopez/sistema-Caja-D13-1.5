const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })
const ENTERO = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 })
const COMPACTO = new Intl.NumberFormat('es-AR', { notation: 'compact', maximumFractionDigits: 1 })
const PORCENTAJE = new Intl.NumberFormat('es-AR', { style: 'percent', maximumFractionDigits: 1 })

export function formatoPesos(valor: number): string {
  return PESOS.format(valor)
}

export function formatoEntero(valor: number): string {
  return ENTERO.format(valor)
}

// Para ejes: completo hasta 10.000 y compacto desde ahí ($ 25 mil, $ 1,2 M).
export function formatoPesosEje(valor: number): string {
  return `$ ${Math.abs(valor) < 10000 ? ENTERO.format(valor) : COMPACTO.format(valor)}`
}

export function formatoPorcentaje(fraccion: number): string {
  return PORCENTAJE.format(fraccion)
}

// La API ya manda la hora de Argentina: se arma con el texto para que no dependa de la zona del
// navegador, siempre en formato de 24 horas.
export function formatoFechaHora(fechaHoraIso: string): string {
  const [fecha, hora = ''] = fechaHoraIso.split('T')
  const [anio, mes, dia] = fecha.split('-')
  return `${dia}/${mes}/${anio} ${hora.slice(0, 5)}`
}

function aFechaLocal(fechaIso: string): Date {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return new Date(anio, mes - 1, dia)
}

export function formatoDiaCorto(fechaIso: string): string {
  return aFechaLocal(fechaIso).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' })
}

export function formatoDiaLargo(fechaIso: string): string {
  return aFechaLocal(fechaIso).toLocaleDateString('es-AR', {
    weekday: 'long',
    day: 'numeric',
    month: 'numeric',
    year: 'numeric',
  })
}
