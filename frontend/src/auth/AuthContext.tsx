import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { login as loginRequest } from '../api/auth'
import { setAuthToken } from '../api/client'
import type { LoginResponse, Rol } from '../types/api'

const STORAGE_KEY = 'd13.sesion'

interface Sesion {
  idEmpleado: number
  nombre: string
  usuario: string
  rol: Rol
  token: string
}

interface AuthContextValue {
  sesion: Sesion | null
  login: (usuario: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function leerSesionGuardada(): Sesion | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Sesion
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(() => leerSesionGuardada())

  useEffect(() => {
    setAuthToken(sesion?.token ?? null)
  }, [sesion])

  async function login(usuario: string, password: string): Promise<void> {
    const respuesta: LoginResponse = await loginRequest({ usuario, password })
    const nuevaSesion: Sesion = {
      idEmpleado: respuesta.idEmpleado,
      nombre: respuesta.nombre,
      usuario: respuesta.usuario,
      rol: respuesta.rol,
      token: respuesta.token,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(nuevaSesion))
    setSesion(nuevaSesion)
  }

  function logout(): void {
    localStorage.removeItem(STORAGE_KEY)
    setSesion(null)
  }

  return <AuthContext.Provider value={{ sesion, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth debe usarse dentro de un AuthProvider')
  }
  return context
}
