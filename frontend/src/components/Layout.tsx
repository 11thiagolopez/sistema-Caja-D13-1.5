import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { sesion, logout } = useAuth()
  const navigate = useNavigate()

  function onLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="layout">
      <nav>
        <NavLink to="/productos">Productos</NavLink>
        <NavLink to="/ventas/nueva">Registrar venta</NavLink>
        {sesion?.rol === 'ADMIN' && (
          <>
            <NavLink to="/ventas/historial">Historial de ventas</NavLink>
            <NavLink to="/caja">Caja</NavLink>
            <NavLink to="/reportes">Reportes</NavLink>
          </>
        )}
        <span className="nav-usuario">
          {sesion?.nombre} ({sesion?.rol})
        </span>
        <button type="button" onClick={onLogout}>
          Salir
        </button>
      </nav>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
