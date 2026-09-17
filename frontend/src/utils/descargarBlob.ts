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

/**
 * Abre un blob (PDF) en una pestaña nueva con el visor nativo del navegador, en vez de forzar una
 * descarga — el "Ver" de un comprobante usa esto, "Descargar" sigue usando descargarBlob de
 * arriba. El Content-Disposition "attachment" de la respuesta HTTP no aplica acá: el navegador ya
 * reconstruyó el blob en memoria, así que decide mostrarlo o descargarlo solo por el tipo de
 * contenido (application/pdf → lo abre en su visor de PDF, con su propio botón de imprimir que
 * respeta el tamaño de página real embebido en el PDF — más confiable que el @media print/@page
 * de CSS, que quedó sujeto a cómo cada driver de impresora interpreta el pedido).
 *
 * No se revoca la URL enseguida (a diferencia de descargarBlob): la pestaña nueva la sigue
 * necesitando para mostrar el PDF. El navegador la libera sola al cerrar esa pestaña o recargar
 * esta página.
 */
export function verBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
}
