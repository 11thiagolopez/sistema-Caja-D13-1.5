import type { ApiError } from '../types/api'

const BASE_URL = import.meta.env.VITE_API_BASE_URL

let authToken: string | null = null

export function setAuthToken(token: string | null): void {
  authToken = token
}

export class ApiRequestError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`)
  }

  const response = await fetch(`${BASE_URL}${path}`, { ...init, headers })

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiError | null
    throw new ApiRequestError(response.status, body?.message ?? response.statusText)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

// Para descargas binarias (PDF): mismo manejo de auth/errores que request<T>, sin parsear JSON.
export async function requestBlob(path: string): Promise<Blob> {
  const headers = new Headers()
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`)
  }

  const response = await fetch(`${BASE_URL}${path}`, { headers })

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiError | null
    throw new ApiRequestError(response.status, body?.message ?? response.statusText)
  }

  return response.blob()
}
