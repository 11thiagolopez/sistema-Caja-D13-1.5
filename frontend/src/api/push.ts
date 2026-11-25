import { request } from './client'

export function getClavePublicaPush(): Promise<{ clavePublica: string }> {
  return request<{ clavePublica: string }>('/api/push/clave-publica')
}

export function guardarSuscripcionPush(endpoint: string, p256dh: string, auth: string): Promise<void> {
  return request<void>('/api/push/suscripcion', {
    method: 'POST',
    body: JSON.stringify({ endpoint, p256dh, auth }),
  })
}
