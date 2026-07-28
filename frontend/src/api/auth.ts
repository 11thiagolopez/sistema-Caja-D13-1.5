import { request } from './client'
import type { LoginRequest, LoginResponse } from '../types/api'

export function login(credenciales: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(credenciales),
  })
}
