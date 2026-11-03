import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { Rol } from '../types/api'

export function RequireAuth() {
  const { sesion } = useAuth()
  const location = useLocation()
  if (!sesion) {
    // Para volver adonde iba al loguearse: tocar una notificación lleva directo a Peticiones.
    return <Navigate to="/login" replace state={{ desde: location.pathname }} />
  }
  return <Outlet />
}

export function RequireRole({ allow }: { allow: Rol[] }) {
  const { sesion } = useAuth()
  if (!sesion) {
    return <Navigate to="/login" replace />
  }
  if (!allow.includes(sesion.rol)) {
    return <Navigate to="/productos" replace />
  }
  return <Outlet />
}
