// Dispara la descarga de un blob (PDF, etc.) como si fuera un link <a download>. Antes esta misma
// función estaba duplicada en HistorialVentas.tsx y Presupuestos.tsx.
export function descargarBlob(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  enlace.click()
  URL.revokeObjectURL(url)
}
