// Dos tipos de aviso:
// - Sin contenido (peticiones de Autorizaciones): montos y motivos no pasan por los servidores de
//   push, el detalle se ve recién al entrar al sistema con sesión iniciada. Texto y URL fijos.
// - Con contenido cifrado (trabajos a domicilio): event.data trae {tipo, cuerpo, url}, cada aviso
//   con su propio texto y su propio trabajo.

// Una versión nueva de este archivo toma el control en cuanto se descarga, sin esperar a que se
// cierren todas las pestañas del sistema (si no, los cambios de los avisos tardaban días en llegar).
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  const datos = event.data ? event.data.json() : null
  event.waitUntil(
    self.registration.showNotification('oNce', {
      body: datos ? datos.cuerpo : 'Tenés una petición nueva para aprobar.',
      icon: '/icon-192.png',
      // Cada aviso de trabajo con su propia etiqueta: con una etiqueta compartida (ej. por trabajo),
      // Windows y Android reemplazaban el aviso anterior en silencio y solo se veía el primero.
      tag: datos ? `${datos.tipo}-${Date.now()}` : 'peticion-autorizacion',
      renotify: !datos,
      data: { url: datos ? datos.url : '/autorizaciones' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = event.notification.data?.url ?? '/autorizaciones'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((ventanas) => {
      const abierta = ventanas.find((v) => new URL(v.url).origin === self.location.origin)
      if (abierta) {
        abierta.navigate(url)
        return abierta.focus()
      }
      return self.clients.openWindow(url)
    }),
  )
})
