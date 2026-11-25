import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { ApiRequestError } from '../api/client'
import { borrarFotoTrabajo, descargarFotoTrabajo, subirFotosTrabajo, type LadoFotos } from '../api/fotosTrabajo'
import { comprimirFoto } from '../utils/comprimirFoto'
import { formatoFechaHora } from '../utils/formato'
import type { FotoTrabajoResponse } from '../types/api'

interface Props {
  lado: LadoFotos
  idVenta: number
  fotos: FotoTrabajoResponse[]
  onCambio: (fotos: FotoTrabajoResponse[]) => void
  // Quien mira: el jefe borra cualquiera; el técnico, solo las suyas (el backend lo valida igual).
  idEmpleadoActual: number | undefined
  puedeSubir: boolean
  ayuda?: string
}

/**
 * Fotos de un trabajo a domicilio, compartidas entre el jefe y el técnico. Las imágenes se piden
 * con el token (no son públicas), así que se bajan como blob y se muestran con una URL local.
 */
export function FotosTrabajo({ lado, idVenta, fotos, onCambio, idEmpleadoActual, puedeSubir, ayuda }: Props) {
  const [urls, setUrls] = useState<Record<number, string>>({})
  const [ampliada, setAmpliada] = useState<FotoTrabajoResponse | null>(null)
  const [subiendo, setSubiendo] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const urlsRef = useRef(urls)
  urlsRef.current = urls

  useEffect(() => {
    let cancelado = false
    for (const foto of fotos) {
      if (urlsRef.current[foto.idFoto]) continue
      descargarFotoTrabajo(lado, idVenta, foto.idFoto)
        .then((blob) => {
          if (cancelado) return
          const url = URL.createObjectURL(blob)
          setUrls((actuales) => ({ ...actuales, [foto.idFoto]: url }))
        })
        .catch(() => {})
    }
    return () => {
      cancelado = true
    }
  }, [fotos, lado, idVenta])

  // Libera las URLs locales al salir de la pantalla.
  useEffect(() => () => Object.values(urlsRef.current).forEach((url) => URL.revokeObjectURL(url)), [])

  async function onElegir(e: ChangeEvent<HTMLInputElement>) {
    const archivos = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (archivos.length === 0) return
    setError(null)
    setSubiendo(true)
    try {
      const comprimidas = await Promise.all(archivos.map(comprimirFoto))
      onCambio(await subirFotosTrabajo(lado, idVenta, comprimidas))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron subir las fotos')
    } finally {
      setSubiendo(false)
    }
  }

  async function borrar(foto: FotoTrabajoResponse) {
    if (!window.confirm('¿Borrar esta foto?')) return
    setError(null)
    try {
      await borrarFotoTrabajo(lado, idVenta, foto.idFoto)
      setAmpliada(null)
      onCambio(fotos.filter((f) => f.idFoto !== foto.idFoto))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo borrar la foto')
    }
  }

  const puedeBorrar = (foto: FotoTrabajoResponse) =>
    puedeSubir && (lado === 'jefe' || foto.idEmpleado === idEmpleadoActual)

  return (
    <div className="fotos-trabajo">
      {ayuda && <p className="peticion-detalle">{ayuda}</p>}
      {fotos.length === 0 && <p className="peticion-detalle">Todavía no hay fotos.</p>}
      {fotos.length > 0 && (
        <div className="fotos-grilla">
          {fotos.map((foto) => (
            <button
              key={foto.idFoto}
              type="button"
              className="foto-miniatura"
              onClick={() => setAmpliada(foto)}
              aria-label={`Ver foto de ${foto.nombreEmpleado}`}
            >
              {urls[foto.idFoto] ? <img src={urls[foto.idFoto]} alt="" /> : <span className="spinner" />}
            </button>
          ))}
        </div>
      )}

      {puedeSubir && (
        <>
          <input ref={inputRef} type="file" accept="image/*" multiple hidden onChange={onElegir} />
          <button type="button" onClick={() => inputRef.current?.click()} disabled={subiendo}>
            {subiendo && <span className="spinner" />}
            {subiendo ? 'Subiendo...' : 'Agregar fotos'}
          </button>
        </>
      )}
      {error && <p className="error">{error}</p>}

      {ampliada && (
        <div className="foto-visor" role="dialog" aria-modal="true">
          <button type="button" className="foto-visor-fondo" aria-label="Cerrar" onClick={() => setAmpliada(null)} />
          <div className="foto-visor-contenido">
            {urls[ampliada.idFoto] && <img src={urls[ampliada.idFoto]} alt="" />}
            <div className="foto-visor-pie">
              <span>
                {ampliada.nombreEmpleado} — {formatoFechaHora(ampliada.creadaEn)}
              </span>
              <div className="foto-visor-acciones">
                {puedeBorrar(ampliada) && (
                  <button type="button" className="boton-rechazar" onClick={() => borrar(ampliada)}>
                    Borrar
                  </button>
                )}
                <button type="button" onClick={() => setAmpliada(null)}>
                  Cerrar
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
