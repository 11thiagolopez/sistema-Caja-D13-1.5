import type { VentaResponse } from '../types/api'

/**
 * Cómo se muestra un trabajo a domicilio. "Cobrado" sale de estado === 'CONFIRMADA' y no de
 * estadoTrabajo: un borrador guardado con estadoTrabajo CERRADO_Y_COBRADO nunca pasó por "Cerrar y
 * cobrar", así que no entró a la caja ni tiene comprobante.
 *
 * TERMINADO_TECNICO es el aviso del técnico ("terminé"); TERMINADO lo confirma el jefe y bloquea el
 * trabajo hasta cobrarlo o reabrirlo (plan-migracion.md sección 37).
 */
export function estadoVisibleTrabajo({ estado, estadoTrabajo }: Pick<VentaResponse, 'estado' | 'estadoTrabajo'>): {
  clase: string
  etiqueta: string
} {
  if (estado === 'ANULADA') return { clase: 'trabajo-anulado', etiqueta: 'Cancelado' }
  if (estado === 'CONFIRMADA') return { clase: 'trabajo-cobrado', etiqueta: 'Cobrado' }
  if (estadoTrabajo === 'EN_CAMINO') return { clase: 'trabajo-en-curso', etiqueta: 'En camino' }
  if (estadoTrabajo === 'EN_CURSO') return { clase: 'trabajo-en-curso', etiqueta: 'En curso' }
  if (estadoTrabajo === 'TERMINADO_TECNICO') return { clase: 'trabajo-en-curso', etiqueta: 'El técnico avisó que terminó' }
  if (estadoTrabajo === 'TERMINADO' || estadoTrabajo === 'CERRADO_Y_COBRADO') {
    return { clase: 'trabajo-sin-cobrar', etiqueta: 'Terminado, falta cobrar' }
  }
  return { clase: 'trabajo-agendado', etiqueta: 'Agendado' }
}

/** Terminado por el jefe (sin cobrar), cobrado o eliminado: nadie lo modifica. Espejo de VentaService.estaBloqueado. */
export function trabajoBloqueado({ estado, estadoTrabajo }: Pick<VentaResponse, 'estado' | 'estadoTrabajo'>): boolean {
  return estado !== 'EN_PROGRESO' || estadoTrabajo === 'TERMINADO'
}
