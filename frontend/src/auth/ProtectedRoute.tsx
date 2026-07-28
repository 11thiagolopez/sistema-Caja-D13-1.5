import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { Rol } from '../types/api'

export function RequireAuth() {
  const { sesion } = useAuth()
  if (!sesion) {
    return <Navigate to="/login" replace />
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
