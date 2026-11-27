import { request, requestFormConProgreso } from './client'
import type { ArchivoTrabajoResponse, MomentoFoto } from '../types/api'
import type { LadoFotos } from './fotosTrabajo'

// Videos y audios adjuntos (las fotos van por api/fotosTrabajo). Mismos dos caminos que las fotos:
// el jefe por la agenda (ADMIN), el técnico por "Mis trabajos".
function base(lado: LadoFotos, idVenta: number): string {
  return lado === 'jefe' ? `/api/ventas/trabajo-domicilio/${idVenta}/archivos` : `/api/mis-trabajos/${idVenta}/archivos`
}

export function getArchivosTrabajo(idVenta: number): Promise<ArchivoTrabajoResponse[]> {
  return request<ArchivoTrabajoResponse[]>(base('jefe', idVenta))
}

export function subirArchivoTrabajo(
  lado: LadoFotos,
  idVenta: number,
  archivo: File,
  momento: MomentoFoto | null,
  onProgreso: (fraccion: number) => void,
): Promise<ArchivoTrabajoResponse[]> {
  const form = new FormData()
  if (momento) form.append('momento', momento)
  form.append('archivo', archivo, archivo.name)
  return requestFormConProgreso<ArchivoTrabajoResponse[]>(base(lado, idVenta), form, onProgreso)
}

// Link temporal (unas 2 horas) para reproducirlo directo desde el bucket.
export async function urlArchivoTrabajo(lado: LadoFotos, idVenta: number, idArchivo: number): Promise<string> {
  return (await request<{ url: string }>(`${base(lado, idVenta)}/${idArchivo}/url`)).url
}

export function borrarArchivoTrabajo(lado: LadoFotos, idVenta: number, idArchivo: number): Promise<void> {
  return request<void>(`${base(lado, idVenta)}/${idArchivo}`, { method: 'DELETE' })
}
