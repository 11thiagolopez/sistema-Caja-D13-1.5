import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { ApiRequestError } from '../api/client'
import { borrarFotoTrabajo, descargarFotoTrabajo, subirFotosTrabajo, type LadoFotos } from '../api/fotosTrabajo'
import { borrarArchivoTrabajo, subirArchivoTrabajo, urlArchivoTrabajo } from '../api/archivosTrabajo'
import { comprimirFoto } from '../utils/comprimirFoto'
import { formatoFechaHora } from '../utils/formato'
import type { ArchivoTrabajoResponse, FotoTrabajoResponse, MomentoFoto } from '../types/api'

// Mismos topes que el backend (ArchivoTrabajoService): se chequean acá para no subir 100 MB en vano.
const MAX_BYTES = 100 * 1024 * 1024
const MAX_VIDEOS = 5

// Orden en pantalla: primero lo adjunto (contexto, ej. lo que mandó el cliente), después antes y después.
const GRUPOS: { momento: MomentoFoto | null; titulo: string }[] = [
  { momento: null, titulo: 'Adjuntos' },
  { momento: 'ANTES', titulo: 'Antes del trabajo' },
  { momento: 'DESPUES', titulo: 'Después del trabajo' },
]

// Las notas de voz de WhatsApp (.opus) a veces llegan sin tipo: se aceptan por la extensión.
const ACEPTA_ADJUNTAR = 'image/*,video/*,audio/*,.opus,.ogg,.m4a,.mp3,.aac,.wav'
const ACEPTA_ANTES_DESPUES = 'image/*,video/*'

function esImagen(archivo: File): boolean {
  return archivo.type.startsWith('image/') || /\.(jpe?g|png|webp|heic|heif)$/i.test(archivo.name)
}

function esVideo(archivo: File): boolean {
  return archivo.type.startsWith('video/') || /\.(mp4|mov|webm|3gp)$/i.test(archivo.name)
}

function formatoTamanio(bytes: number): string {
  return bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

type Abierto =
  | { tipo: 'foto'; foto: FotoTrabajoResponse }
  | { tipo: 'archivo'; archivo: ArchivoTrabajoResponse; url: string }

interface Props {
  lado: LadoFotos
  idVenta: number
  fotos: FotoTrabajoResponse[]
  onCambioFotos: (fotos: FotoTrabajoResponse[]) => void
  archivos: ArchivoTrabajoResponse[]
  onCambioArchivos: (archivos: ArchivoTrabajoResponse[]) => void
  // Quien mira: el jefe borra cualquiera; el técnico, solo lo suyo (el backend lo valida igual).
  idEmpleadoActual: number | undefined
  puedeSubir: boolean
  ayuda?: string
}

/**
 * Fotos, videos y audios de un trabajo a domicilio, compartidos entre el jefe y el técnico. Las
 * fotos se achican en el navegador y quedan en la base; los videos y audios van al bucket y se
 * reproducen con un link temporal. "Adjuntar" sube cualquiera de los tres sin marcarlo (contexto:
 * lo que manda el cliente); el técnico además tiene "Antes" y "Después" como comprobante.
 */
export function AdjuntosTrabajo({
  lado,
  idVenta,
  fotos,
  onCambioFotos,
  archivos,
  onCambioArchivos,
  idEmpleadoActual,
  puedeSubir,
  ayuda,
}: Props) {
  const [urlsFotos, setUrlsFotos] = useState<Record<number, string>>({})
  const [abierto, setAbierto] = useState<Abierto | null>(null)
  const [abriendo, setAbriendo] = useState<number | null>(null)
  const [estadoSubida, setEstadoSubida] = useState<string | null>(null)
  const [progreso, setProgreso] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Dos selectores fijos (y no uno que cambia de "accept"): en iPhone el selector solo se abre si el
  // click sale directo del toque del usuario.
  const inputAdjuntarRef = useRef<HTMLInputElement>(null)
  const inputAntesDespuesRef = useRef<HTMLInputElement>(null)
  // Antes/después/adjunto de la tanda que se está eligiendo (el selector de archivos no lo puede llevar).
  const momentoElegido = useRef<MomentoFoto | null>(null)
  const urlsRef = useRef(urlsFotos)
  urlsRef.current = urlsFotos
  const subiendo = estadoSubida !== null

  // Las fotos se piden con el token (no son públicas): se bajan como blob y se muestran con una URL local.
  useEffect(() => {
    let cancelado = false
    for (const foto of fotos) {
      if (urlsRef.current[foto.idFoto]) continue
      descargarFotoTrabajo(lado, idVenta, foto.idFoto)
        .then((blob) => {
          if (cancelado) return
          const url = URL.createObjectURL(blob)
          setUrlsFotos((actuales) => ({ ...actuales, [foto.idFoto]: url }))
        })
        .catch(() => {})
    }
    return () => {
      cancelado = true
    }
  }, [fotos, lado, idVenta])

  useEffect(() => () => Object.values(urlsRef.current).forEach((url) => URL.revokeObjectURL(url)), [])

  function elegir(momento: MomentoFoto | null) {
    momentoElegido.current = momento
    ;(momento === null ? inputAdjuntarRef : inputAntesDespuesRef).current?.click()
  }

  async function onElegir(e: ChangeEvent<HTMLInputElement>) {
    const elegidos = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (elegidos.length === 0) return
    setError(null)
    const momento = lado === 'tecnico' ? momentoElegido.current : null
    const imagenes = elegidos.filter(esImagen)
    const otros = elegidos.filter((a) => !esImagen(a))

    const muyGrandes = otros.filter((a) => a.size > MAX_BYTES)
    if (muyGrandes.length > 0) {
      setError(`${muyGrandes.map((a) => a.name).join(', ')}: pesa más de 100 MB. Grabá uno más corto.`)
      return
    }
    const videosNuevos = otros.filter(esVideo).length
    const videosQueHay = archivos.filter((a) => a.tipoContenido.startsWith('video/')).length
    if (videosQueHay + videosNuevos > MAX_VIDEOS) {
      setError(`Un trabajo puede tener hasta ${MAX_VIDEOS} videos (ya tiene ${videosQueHay}).`)
      return
    }

    const errores: string[] = []
    try {
      if (imagenes.length > 0) {
        setEstadoSubida(imagenes.length === 1 ? 'Subiendo la foto...' : `Subiendo ${imagenes.length} fotos...`)
        setProgreso(null)
        try {
          const comprimidas = await Promise.all(imagenes.map(comprimirFoto))
          onCambioFotos(await subirFotosTrabajo(lado, idVenta, comprimidas, momento))
        } catch (err) {
          errores.push(err instanceof ApiRequestError ? err.message : 'No se pudieron subir las fotos')
        }
      }
      // Videos y audios de a uno (cada uno puede pesar 100 MB), con el % subido.
      for (const [i, archivo] of otros.entries()) {
        const queEs = esVideo(archivo) ? 'video' : 'audio'
        setEstadoSubida(otros.length === 1 ? `Subiendo el ${queEs}` : `Subiendo ${i + 1} de ${otros.length} (${queEs})`)
        setProgreso(0)
        try {
          onCambioArchivos(await subirArchivoTrabajo(lado, idVenta, archivo, momento, setProgreso))
        } catch (err) {
          errores.push(`${archivo.name}: ${err instanceof ApiRequestError ? err.message : 'no se pudo subir'}`)
        }
      }
    } finally {
      setEstadoSubida(null)
      setProgreso(null)
    }
    if (errores.length > 0) setError(errores.join(' — '))
  }

  async function verArchivo(archivo: ArchivoTrabajoResponse) {
    setError(null)
    setAbriendo(archivo.idArchivo)
    try {
      setAbierto({ tipo: 'archivo', archivo, url: await urlArchivoTrabajo(lado, idVenta, archivo.idArchivo) })
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir el archivo')
    } finally {
      setAbriendo(null)
    }
  }

  async function borrar(item: Abierto) {
    const nombre = item.tipo === 'foto' ? 'esta foto' : item.archivo.tipoContenido.startsWith('audio/') ? 'este audio' : 'este video'
    if (!window.confirm(`¿Borrar ${nombre}?`)) return
    setError(null)
    try {
      if (item.tipo === 'foto') {
        await borrarFotoTrabajo(lado, idVenta, item.foto.idFoto)
        onCambioFotos(fotos.filter((f) => f.idFoto !== item.foto.idFoto))
      } else {
        await borrarArchivoTrabajo(lado, idVenta, item.archivo.idArchivo)
        onCambioArchivos(archivos.filter((a) => a.idArchivo !== item.archivo.idArchivo))
      }
      setAbierto(null)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo borrar')
    }
  }

  const puedeBorrar = (idEmpleado: number) => puedeSubir && (lado === 'jefe' || idEmpleado === idEmpleadoActual)
  const autorDe = (item: Abierto) => (item.tipo === 'foto' ? item.foto : item.archivo)
  const nada = fotos.length === 0 && archivos.length === 0

  return (
    <div className="fotos-trabajo">
      {ayuda && <p className="peticion-detalle">{ayuda}</p>}
      {nada && <p className="peticion-detalle">Todavía no hay fotos, videos ni audios.</p>}
      {GRUPOS.map(({ momento, titulo }) => {
        const fotosDelGrupo = fotos.filter((f) => f.momento === momento)
        const archivosDelGrupo = archivos.filter((a) => a.momento === momento)
        if (fotosDelGrupo.length === 0 && archivosDelGrupo.length === 0) return null
        return (
          <div key={titulo} className="fotos-grupo">
            <span className="fotos-grupo-titulo">
              {titulo} ({fotosDelGrupo.length + archivosDelGrupo.length})
            </span>
            {fotosDelGrupo.length > 0 && (
              <div className="fotos-grilla">
                {fotosDelGrupo.map((foto) => (
                  <button
                    key={foto.idFoto}
                    type="button"
                    className="foto-miniatura"
                    onClick={() => setAbierto({ tipo: 'foto', foto })}
                    aria-label={`Ver foto de ${foto.nombreEmpleado}`}
                  >
                    {urlsFotos[foto.idFoto] ? <img src={urlsFotos[foto.idFoto]} alt="" /> : <span className="spinner" />}
                  </button>
                ))}
              </div>
            )}
            {archivosDelGrupo.length > 0 && (
              <div className="videos-lista">
                {archivosDelGrupo.map((archivo) => {
                  const esAudio = archivo.tipoContenido.startsWith('audio/')
                  return (
                    <button key={archivo.idArchivo} type="button" className="video-item" onClick={() => verArchivo(archivo)}>
                      {abriendo === archivo.idArchivo ? (
                        <span className="spinner" />
                      ) : (
                        <span aria-hidden="true">{esAudio ? '♪' : '▶'}</span>
                      )}
                      <span>
                        {esAudio ? 'Audio' : 'Video'} de {archivo.nombreEmpleado} — {formatoFechaHora(archivo.creadaEn)} ·{' '}
                        {formatoTamanio(archivo.tamanioBytes)}
                      </span>
                    </button>
                  )
                })}
              </div>
            )}
          </div>
        )
      })}

      {puedeSubir && (
        <>
          <input ref={inputAdjuntarRef} type="file" accept={ACEPTA_ADJUNTAR} multiple hidden onChange={onElegir} />
          <input ref={inputAntesDespuesRef} type="file" accept={ACEPTA_ANTES_DESPUES} multiple hidden onChange={onElegir} />
          <div className="fotos-botones">
            {lado === 'tecnico' &&
              (['ANTES', 'DESPUES'] as const).map((momento) => (
                <button key={momento} type="button" onClick={() => elegir(momento)} disabled={subiendo}>
                  {momento === 'ANTES' ? 'Antes' : 'Después'}
                </button>
              ))}
            <button type="button" onClick={() => elegir(null)} disabled={subiendo}>
              Adjuntar
            </button>
          </div>
          {subiendo && (
            <div className="video-progreso">
              {progreso !== null ? <progress max={1} value={progreso} /> : <span className="spinner" />}
              <span>
                {estadoSubida}
                {progreso !== null && (progreso < 1 ? `... ${Math.round(progreso * 100)}%` : ' — guardando...')}
              </span>
            </div>
          )}
          <p className="peticion-detalle">
            Fotos, videos (hasta {MAX_VIDEOS}, de 100 MB cada uno) y audios, como las notas de voz de WhatsApp.
          </p>
        </>
      )}
      {error && <p className="error">{error}</p>}

      {abierto && (
        <div className="foto-visor" role="dialog" aria-modal="true">
          <button type="button" className="foto-visor-fondo" aria-label="Cerrar" onClick={() => setAbierto(null)} />
          <div className="foto-visor-contenido">
            {abierto.tipo === 'foto' && urlsFotos[abierto.foto.idFoto] && <img src={urlsFotos[abierto.foto.idFoto]} alt="" />}
            {abierto.tipo === 'archivo' &&
              (abierto.archivo.tipoContenido.startsWith('audio/') ? (
                <audio src={abierto.url} controls autoPlay />
              ) : (
                <video src={abierto.url} controls autoPlay playsInline />
              ))}
            <div className="foto-visor-pie">
              <span>
                {autorDe(abierto).nombreEmpleado} — {formatoFechaHora(autorDe(abierto).creadaEn)}
              </span>
              <div className="foto-visor-acciones">
                {puedeBorrar(autorDe(abierto).idEmpleado) && (
                  <button type="button" className="boton-rechazar" onClick={() => borrar(abierto)}>
                    Borrar
                  </button>
                )}
                <button type="button" onClick={() => setAbierto(null)}>
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
