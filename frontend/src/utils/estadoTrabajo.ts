import type { VentaResponse } from '../types/api'

/**
 * Cómo se muestra un trabajo a domicilio. "Cobrado" sale de estado === 'CONFIRMADA' y no de
 * estadoTrabajo: un borrador guardado con estadoTrabajo COBRADO nunca pasó por "Cerrar y cobrar", así
 * que no entró a la caja ni tiene comprobante.
 */
export function estadoVisibleTrabajo({ estado, estadoTrabajo }: Pick<VentaResponse, 'estado' | 'estadoTrabajo'>): {
  clase: string
  etiqueta: string
} {
  if (estado === 'ANULADA') return { clase: 'trabajo-anulado', etiqueta: 'Anulado' }
  if (estado === 'CONFIRMADA') return { clase: 'trabajo-cobrado', etiqueta: 'Cobrado' }
  if (estadoTrabajo === 'EN_CURSO') return { clase: 'trabajo-en-curso', etiqueta: 'En curso' }
  if (estadoTrabajo === 'COMPLETADO' || estadoTrabajo === 'COBRADO') {
    return { clase: 'trabajo-sin-cobrar', etiqueta: 'Completado, falta cobrar' }
  }
  return { clase: 'trabajo-agendado', etiqueta: 'Agendado' }
}
