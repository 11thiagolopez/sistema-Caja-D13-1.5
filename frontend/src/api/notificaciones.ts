import { request } from './client'
import type { NotificacionResponse } from '../types/api'

export function getNotificaciones(): Promise<NotificacionResponse[]> {
  return request<NotificacionResponse[]>('/api/notificaciones')
}

export function getNotificacionesNoLeidas(): Promise<{ cantidad: number }> {
  return request<{ cantidad: number }>('/api/notificaciones/no-leidas')
}

export function marcarNotificacionLeida(id: number): Promise<void> {
  return request<void>(`/api/notificaciones/${id}/leida`, { method: 'POST' })
}
