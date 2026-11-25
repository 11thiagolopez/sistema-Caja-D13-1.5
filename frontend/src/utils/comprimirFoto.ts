// Una foto de celular pesa 3-8 MB; achicada a 1600px de lado mayor en JPEG queda en unos
// 200-400 KB, que alcanza de sobra para ver un trabajo y no llena la base (las fotos se guardan
// ahí). Si el navegador no puede decodificarla (ej. HEIC en algunos Android), se manda tal cual y
// el backend decide: acepta JPG, PNG o WEBP de hasta 3 MB.
const LADO_MAXIMO = 1600
const CALIDAD = 0.8

export async function comprimirFoto(archivo: File): Promise<Blob> {
  let imagen: ImageBitmap
  try {
    imagen = await createImageBitmap(archivo)
  } catch {
    return archivo
  }
  const escala = Math.min(1, LADO_MAXIMO / Math.max(imagen.width, imagen.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(imagen.width * escala)
  canvas.height = Math.round(imagen.height * escala)
  const contexto = canvas.getContext('2d')
  if (!contexto) return archivo
  contexto.drawImage(imagen, 0, 0, canvas.width, canvas.height)
  imagen.close()
  const comprimida = await new Promise<Blob | null>((resolver) => canvas.toBlob(resolver, 'image/jpeg', CALIDAD))
  return comprimida && comprimida.size < archivo.size ? comprimida : archivo
}
