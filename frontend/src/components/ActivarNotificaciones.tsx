import { useEffect, useState } from 'react'
import { ApiRequestError } from '../api/client'
import { getClavePublicaPush, guardarSuscripcionPush } from '../api/push'

type Estado = 'cargando' | 'no-disponible' | 'bloqueadas' | 'inactivas' | 'activas'

function claveABytes(claveBase64Url: string): Uint8Array<ArrayBuffer> {
  const base64 = claveBase64Url.replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4)), (c) => c.charCodeAt(0))
}

/**
 * Permiso de notificaciones de ESTE dispositivo: cada celular o PC del ADMIN se activa por separado.
 * El navegador solo deja pedir el permiso a partir de un toque del usuario, por eso es un botón. Si
 * ya estaba activo se vuelve a mandar la suscripción, porque el navegador puede renovarla.
 */
export function ActivarNotificaciones() {
  const [estado, setEstado] = useState<Estado>('cargando')
  const [error, setError] = useState<string | null>(null)
  const [activando, setActivando] = useState(false)

  useEffect(() => {
    async function revisar() {
      if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
        setEstado('no-disponible')
        return
      }
      if (Notification.permission === 'denied') {
        setEstado('bloqueadas')
        return
      }
      const registro = await navigator.serviceWorker.register('/sw.js')
      const suscripcion = await registro.pushManager.getSubscription()
      if (suscripcion) {
        await guardarSuscripcionPush(suscripcion.endpoint)
        setEstado('activas')
      } else {
        setEstado('inactivas')
      }
    }
    revisar().catch(() => setEstado('inactivas'))
  }, [])

  async function activar() {
    setError(null)
    setActivando(true)
    try {
      const permiso = await Notification.requestPermission()
      if (permiso !== 'granted') {
        setEstado(permiso === 'denied' ? 'bloqueadas' : 'inactivas')
        return
      }
      const { clavePublica } = await getClavePublicaPush()
      const registro = await navigator.serviceWorker.register('/sw.js')
      await navigator.serviceWorker.ready
      const suscripcion = await registro.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: claveABytes(clavePublica),
      })
      await guardarSuscripcionPush(suscripcion.endpoint)
      setEstado('activas')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron activar las notificaciones')
    } finally {
      setActivando(false)
    }
  }

  if (estado === 'cargando' || estado === 'activas') {
    return estado === 'activas' ? <p className="peticion-detalle">Notificaciones activas en este dispositivo.</p> : null
  }
  if (estado === 'no-disponible') {
    return <p className="peticion-detalle">Este navegador no admite notificaciones.</p>
  }
  if (estado === 'bloqueadas') {
    return (
      <p className="peticion-detalle">
        Las notificaciones están bloqueadas: activalas desde los permisos del sitio en el navegador.
      </p>
    )
  }
  return (
    <div className="activar-notificaciones">
      <button type="button" onClick={activar} disabled={activando}>
        {activando && <span className="spinner" />}
        Activar notificaciones en este dispositivo
      </button>
      {error && <p className="error">{error}</p>}
    </div>
  )
}
