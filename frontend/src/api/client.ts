import type { ApiError } from '../types/api'

const BASE_URL = import.meta.env.VITE_API_BASE_URL

let authToken: string | null = null

export function setAuthToken(token: string | null): void {
  authToken = token
}

// La suscripción push de ESTE dispositivo (su endpoint). Va en cada pedido como X-Push-Endpoint para
// que el backend no le mande a este mismo dispositivo el aviso de algo que se hizo desde acá: el jefe
// y el local comparten el usuario ADMIN, y el aviso es para el otro (NotificacionTrabajoService).
let pushEndpoint: string | null = null

export function setPushEndpoint(endpoint: string | null): void {
  pushEndpoint = endpoint
}

/** Al arrancar: si este dispositivo ya tiene las notificaciones activas, toma su endpoint. */
export async function cargarPushEndpoint(): Promise<void> {
  try {
    if (!('serviceWorker' in navigator)) return
    const registro = await navigator.serviceWorker.getRegistration('/sw.js')
    // Que el navegador baje ya la versión nueva de sw.js si la hay (si no, la revisa cuando quiere).
    registro?.update().catch(() => {})
    const suscripcion = await registro?.pushManager.getSubscription()
    setPushEndpoint(suscripcion?.endpoint ?? null)
  } catch {
    // Sin push en este navegador: no hay nada que excluir.
  }
}

function ponerCabeceras(headers: Headers): void {
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`)
  }
  if (pushEndpoint) {
    headers.set('X-Push-Endpoint', pushEndpoint)
  }
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
  ponerCabeceras(headers)

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
  ponerCabeceras(headers)

  const response = await fetch(`${BASE_URL}${path}`, { headers })

  if (!response.ok) {
    manejarRespuestaNoOk(response)
    const body = (await response.json().catch(() => null)) as ApiError | null
    throw new ApiRequestError(response.status, body?.message ?? response.statusText)
  }

  return response.blob()
}

// Para subir archivos (fotos): el navegador arma el Content-Type multipart con su boundary, así que
// acá no se fija ninguno (a diferencia de request<T>, que siempre manda JSON).
export async function requestForm<T>(path: string, form: FormData): Promise<T> {
  const headers = new Headers()
  ponerCabeceras(headers)

  const response = await fetch(`${BASE_URL}${path}`, { method: 'POST', body: form, headers })

  if (!response.ok) {
    manejarRespuestaNoOk(response)
    const body = (await response.json().catch(() => null)) as ApiError | null
    throw new ApiRequestError(
      response.status,
      body?.message ?? (response.status === 413 ? 'Las fotos son demasiado grandes' : response.statusText),
    )
  }

  return (await response.json()) as T
}

// Como requestForm, pero informando cuánto se subió (0 a 1): para videos de hasta 100 MB desde el
// celular, que pueden tardar minutos. fetch no da el progreso de subida, XMLHttpRequest sí.
export function requestFormConProgreso<T>(path: string, form: FormData, onProgreso: (fraccion: number) => void): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${BASE_URL}${path}`)
    const headers = new Headers()
    ponerCabeceras(headers)
    headers.forEach((valor, nombre) => xhr.setRequestHeader(nombre, valor))
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgreso(e.loaded / e.total)
    }
    xhr.onload = () => {
      let body: unknown = null
      try {
        body = JSON.parse(xhr.responseText)
      } catch {
        body = null
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(body as T)
        return
      }
      if (xhr.status === 401 && authToken) {
        onUnauthorized?.()
      }
      const mensaje = (body as ApiError | null)?.message
      reject(
        new ApiRequestError(
          xhr.status,
          mensaje ?? (xhr.status === 413 ? 'El archivo es demasiado grande' : xhr.statusText || 'Error al subir'),
        ),
      )
    }
    xhr.onerror = () => reject(new ApiRequestError(0, 'Se cortó la conexión mientras se subía el archivo'))
    xhr.send(form)
  })
}
