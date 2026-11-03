// El aviso llega sin contenido a propósito: montos y motivos no pasan por los servidores de push, y
// el detalle se ve recién al entrar al sistema con sesión iniciada.
self.addEventListener('push', (event) => {
  event.waitUntil(
    self.registration.showNotification('Sistema D13', {
      body: 'Tenés una petición nueva para aprobar.',
      icon: '/icon-192.png',
      tag: 'peticion-autorizacion',
      renotify: true,
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((ventanas) => {
      const abierta = ventanas.find((v) => new URL(v.url).origin === self.location.origin)
      if (abierta) {
        abierta.navigate('/autorizaciones')
        return abierta.focus()
      }
      return self.clients.openWindow('/autorizaciones')
    }),
  )
})
