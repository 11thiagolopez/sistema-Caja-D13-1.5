import { formatoDiaLargo } from './formato'

/**
 * Número argentino en el formato que pide wa.me (549 + código de área + número, sin el 0 ni el 15).
 * "11 15 5555-1234", "011 5555 1234", "+54 9 11 5555-1234" y "1155551234" → "5491155551234".
 * Null si no se puede armar un número de 10 dígitos (área + abonado): mejor no abrir un chat
 * equivocado.
 */
export function numeroWhatsapp(telefono: string): string | null {
  let d = telefono.replace(/[^0-9]/g, '')
  if (d.startsWith('00')) d = d.slice(2)
  if (d.startsWith('54')) d = d.slice(2)
  if (d.startsWith('9') && d.length === 11) d = d.slice(1)
  if (d.startsWith('0')) d = d.slice(1)
  // "11 15 5555 1234": el 15 del celular va después del código de área (de 2 a 4 dígitos).
  if (d.length === 12) {
    for (const largoArea of [2, 3, 4]) {
      if (d.slice(largoArea, largoArea + 2) === '15') {
        d = d.slice(0, largoArea) + d.slice(largoArea + 2)
        break
      }
    }
  }
  return d.length === 10 ? `549${d}` : null
}

function hora(hhmm: string): string {
  const [h, m] = hhmm.split(':')
  return m === '00' ? String(Number(h)) : `${Number(h)}:${m}`
}

/** "Hola Juan! Te confirmamos el turno para el martes 25/11/2026 de 10 a 12 en Av. ..." */
export function mensajeConfirmacion(datos: {
  clienteNombre: string
  fechaProgramada: string
  horaDesde: string
  horaHasta: string
  direccionTrabajo: string
}): string {
  const nombre = datos.clienteNombre.trim().split(/\s+/)[0]
  let cuando = `el ${formatoDiaLargo(datos.fechaProgramada).replace(',', '')}`
  if (datos.horaDesde && datos.horaHasta) cuando += ` de ${hora(datos.horaDesde)} a ${hora(datos.horaHasta)}`
  else if (datos.horaDesde) cuando += ` desde las ${hora(datos.horaDesde)}`
  const donde = datos.direccionTrabajo.trim() ? ` en ${datos.direccionTrabajo.trim()}` : ''
  return `Hola${nombre ? ` ${nombre}` : ''}! Te confirmamos el turno para ${cuando}${donde}. Cualquier cambio avisanos por acá. D13 Distribuidora.`
}

// WhatsApp Web no funciona en el celular (manda a bajar la app): ahí se abre la app.
export const ES_CELULAR = typeof navigator !== 'undefined' && /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)

/** WhatsApp del local, que es el que tiene que estar abierto en WhatsApp Web para mandar la confirmación. */
export const WHATSAPP_DEL_LOCAL = '11 2375-2626'

/**
 * En la PC abre WhatsApp Web (web.whatsapp.com), que manda desde la cuenta abierta en ESE navegador
 * — la del local. Antes (wa.me) en la PC abría la app de escritorio instalada, que podía ser la
 * personal. En el celular sigue siendo wa.me: abre la app de WhatsApp.
 */
export function linkWhatsapp(numero: string, mensaje: string): string {
  const texto = encodeURIComponent(mensaje)
  return ES_CELULAR
    ? `https://wa.me/${numero}?text=${texto}`
    : `https://web.whatsapp.com/send?phone=${numero}&text=${texto}`
}

// Siempre la misma pestaña: cada confirmación reusa la de WhatsApp Web en vez de abrir otra.
export const PESTANIA_WHATSAPP = 'whatsapp-web-d13'
