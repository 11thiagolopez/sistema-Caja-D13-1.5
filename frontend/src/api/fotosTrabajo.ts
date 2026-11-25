import { request, requestBlob, requestForm } from './client'
import type { FotoTrabajoResponse, MomentoFoto } from '../types/api'

// Las mismas fotos, por dos caminos distintos según quién mira: el jefe por la agenda (ADMIN) y el
// técnico por "Mis trabajos" (que en el backend valida que el trabajo sea suyo).
export type LadoFotos = 'jefe' | 'tecnico'

function base(lado: LadoFotos, idVenta: number): string {
  return lado === 'jefe' ? `/api/ventas/trabajo-domicilio/${idVenta}/fotos` : `/api/mis-trabajos/${idVenta}/fotos`
}

export function getFotosTrabajo(idVenta: number): Promise<FotoTrabajoResponse[]> {
  return request<FotoTrabajoResponse[]>(base('jefe', idVenta))
}

export function subirFotosTrabajo(
  lado: LadoFotos,
  idVenta: number,
  fotos: Blob[],
  momento: MomentoFoto | null,
): Promise<FotoTrabajoResponse[]> {
  const form = new FormData()
  // El técnico elige antes/después (obligatorio para él); las del jefe no llevan momento.
  if (momento) form.append('momento', momento)
  fotos.forEach((foto, i) => form.append('fotos', foto, `foto-${i + 1}.jpg`))
  return requestForm<FotoTrabajoResponse[]>(base(lado, idVenta), form)
}

export function descargarFotoTrabajo(lado: LadoFotos, idVenta: number, idFoto: number): Promise<Blob> {
  return requestBlob(`${base(lado, idVenta)}/${idFoto}`)
}

export function borrarFotoTrabajo(lado: LadoFotos, idVenta: number, idFoto: number): Promise<void> {
  return request<void>(`${base(lado, idVenta)}/${idFoto}`, { method: 'DELETE' })
}
