import type { Rol } from '../types/api'

// "Mis trabajos" del técnico: hoy exclusivo TECNICO. Espejo de SecurityConfig.ROLES_MIS_TRABAJOS
// — para sumar VENDEDOR el día de mañana, alcanza con agregarlo acá y allá.
export const ROLES_MIS_TRABAJOS: Rol[] = ['TECNICO']

export function puedeUsarMisTrabajos(rol: Rol | undefined): boolean {
  return rol != null && ROLES_MIS_TRABAJOS.includes(rol)
}
