import type { ApiError } from '../types/api'

const BASE_URL = import.meta.env.VITE_API_BASE_URL

let authToken: string | null = null

export function setAuthToken(token: string | null): void {
  authToken = token
}

// Registrado por AuthContext para cerrar sesión sola cuando el backend rechaza el token (expirado,
// o el empleado fue dado de baja — ver JwtAuthenticationFilter). Antes cada página mostraba su
// propio "no se pudo cargar X" y el usuario quedaba tildado sin entender por qué, en vez de volver
// a Login. Solo se dispara si había un token puesto: un 401 de un login con credenciales
// incorrectas (sin sesión activa todavía) no debe disparar un logout.
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

function manejarRespuestaNoOk(response: Response): void {
  if (response.status === 401 && authToken) {
    onUnauthorized?.()
  }
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
    manejarRespuestaNoOk(response)
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
    manejarRespuestaNoOk(response)
    const body = (await response.json().catch(() => null)) as ApiError | null
    throw new ApiRequestError(response.status, body?.message ?? response.statusText)
  }

  return response.blob()
}
