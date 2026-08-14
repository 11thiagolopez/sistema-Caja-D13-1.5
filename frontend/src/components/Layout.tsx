import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getSesionAbierta } from '../api/caja'
import { getCotizacionActual } from '../api/cotizacion'
import type { CotizacionResponse, Rol } from '../types/api'
import { AbrirCajaModal } from './AbrirCajaModal'
import { CerrarCajaModal } from './CerrarCajaModal'
import { RetiroModal } from './RetiroModal'
import { ProveedoresModal } from './ProveedoresModal'
import { CotizacionGateModal } from './CotizacionGateModal'

type ModalKey = 'abrir-caja' | 'cerrar-caja' | 'retiro' | 'proveedores'

interface NavItem {
  label: string
  to?: string
  modal?: ModalKey
  roles?: Rol[]
}

interface NavSection {
  label: string
  roles?: Rol[]
  items: NavItem[]
}

const SECCIONES: NavSection[] = [
  {
    label: 'Caja',
    items: [
      { label: 'Abrir caja', modal: 'abrir-caja' },
      { label: 'Cerrar caja', modal: 'cerrar-caja' },
      { label: 'Retiros de dinero', modal: 'retiro' },
      { label: 'Ver resumen del día', to: '/caja/resumen', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Gastos',
    roles: ['ADMIN'],
    items: [{ label: 'Gastos operativos', to: '/gastos' }],
  },
  {
    label: 'Compras',
    roles: ['ADMIN'],
    items: [
      { label: 'Agregar compra', to: '/compras/nueva' },
      { label: 'Consultar compras', to: '/compras' },
    ],
  },
  {
    label: 'Vendedores',
    roles: ['ADMIN'],
    items: [{ label: 'Vendedores', to: '/vendedores' }],
  },
  {
    label: 'Reportes',
    roles: ['ADMIN'],
    items: [
      { label: 'Balance y productos', to: '/reportes' },
      { label: 'Ventas por vendedor', to: '/vendedores/ventas' },
      { label: 'Comisiones', to: '/vendedores/comisiones' },
      { label: 'Compras por proveedor', to: '/compras/pagos-proveedores' },
      { label: 'Ventas por marca', to: '/reportes/marcas' },
      { label: 'Ventas por forma de pago', to: '/reportes/forma-pago' },
    ],
  },
]

const ENLACES_SUELTOS: NavItem[] = [
  { label: 'Cobros', to: '/ventas/nueva' },
  { label: 'Productos', to: '/productos' },
  { label: 'Presupuestos', to: '/presupuestos' },
  { label: 'Historial de ventas', to: '/ventas/historial', roles: ['ADMIN'] },
  { label: 'Trabajo a domicilio', to: '/ventas/domicilio', roles: ['ADMIN'] },
]

function puedeVer(roles: Rol[] | undefined, rol: Rol | undefined): boolean {
  return !roles || (rol != null && roles.includes(rol))
}

export function Layout() {
  const { sesion, logout } = useAuth()
  const navigate = useNavigate()
  const [seccionAbierta, setSeccionAbierta] = useState<string | null>('Caja')
  const [modalAbierto, setModalAbierto] = useState<ModalKey | null>(null)
  const [cajaObligatoria, setCajaObligatoria] = useState(false)
  const [sidebarAbierta, setSidebarAbierta] = useState(true)

  // Dolarización: gate diario que va ANTES que el de caja — nadie (ningún rol) puede operar el
  // sistema sin la cotización del día cargada. Arranca en true (bloquea) hasta que el chequeo
  // inicial confirme que ya existe una cotización de hoy.
  const [cotizacionObligatoria, setCotizacionObligatoria] = useState(true)
  const [avisoCotizacion, setAvisoCotizacion] = useState<CotizacionResponse | null>(null)

  useEffect(() => {
    getCotizacionActual().then((cotizacion) => {
      setCotizacionObligatoria(!cotizacion)
    })
  }, [])

  // Se dispara una sola vez al entrar a cualquier pantalla logueada: si no hay una sesión de
  // caja ABIERTA, fuerza el modal de "abrir caja" (ambos roles) antes de dejar hacer nada más.
  useEffect(() => {
    getSesionAbierta().then((sesionCaja) => {
      if (!sesionCaja) {
        setCajaObligatoria(true)
      }
    })
  }, [])

  function onCotizacionCargada(cotizacion: CotizacionResponse) {
    setCotizacionObligatoria(false)
    // El cartelito solo aparece cuando ESTA sesión disparó la carga (no si ya existía de antes
    // al entrar), y nunca para VENDEDOR — no tiene que saber el valor del dólar.
    if (sesion?.rol === 'ADMIN') {
      setAvisoCotizacion(cotizacion)
    }
  }

  function onLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  function toggleSeccion(label: string) {
    setSeccionAbierta((actual) => (actual === label ? null : label))
  }

  return (
    <div className="layout">
      <aside className={`sidebar ${sidebarAbierta ? '' : 'sidebar-cerrada'}`}>
        <div className="sidebar-marca">Sistema D13</div>
        <nav>
          {ENLACES_SUELTOS.filter((item) => puedeVer(item.roles, sesion?.rol)).map((item) => (
            <NavLink key={item.to} to={item.to!} className="sidebar-enlace">
              {item.label}
            </NavLink>
          ))}

          {SECCIONES.filter((seccion) => puedeVer(seccion.roles, sesion?.rol)).map((seccion) => (
            <div key={seccion.label} className="sidebar-seccion">
              <button
                type="button"
                className="sidebar-seccion-titulo"
                onClick={() => toggleSeccion(seccion.label)}
                aria-expanded={seccionAbierta === seccion.label}
              >
                {seccion.label}
                <span className="sidebar-seccion-flecha">{seccionAbierta === seccion.label ? '▾' : '▸'}</span>
              </button>
              {seccionAbierta === seccion.label && (
                <div className="sidebar-seccion-items">
                  {seccion.items
                    .filter((item) => puedeVer(item.roles, sesion?.rol))
                    .map((item) =>
                      item.modal ? (
                        <button
                          key={item.label}
                          type="button"
                          className="sidebar-enlace sidebar-enlace-boton"
                          onClick={() => setModalAbierto(item.modal!)}
                        >
                          {item.label}
                        </button>
                      ) : (
                        <NavLink key={item.to} to={item.to!} className="sidebar-enlace">
                          {item.label}
                        </NavLink>
                      ),
                    )}
                </div>
              )}
            </div>
          ))}

          {sesion?.rol === 'ADMIN' && (
            <button
              type="button"
              className="sidebar-enlace sidebar-enlace-boton"
              onClick={() => setModalAbierto('proveedores')}
            >
              Proveedores
            </button>
          )}
        </nav>
      </aside>

      <div className="layout-contenido">
        <header className="layout-topbar">
          <div className="topbar-espaciador">
            <button
              type="button"
              className="sidebar-toggle"
              onClick={() => setSidebarAbierta((actual) => !actual)}
              aria-label={sidebarAbierta ? 'Ocultar menú' : 'Mostrar menú'}
              aria-expanded={sidebarAbierta}
            >
              ☰
            </button>
          </div>
          <h1 className="topbar-titulo">Sistema D13</h1>
          <div className="topbar-usuario">
            <span className="nav-usuario">
              {sesion?.nombre} ({sesion?.rol})
            </span>
            <button type="button" onClick={onLogout}>
              Salir
            </button>
          </div>
        </header>
        {avisoCotizacion && (
          <p className="aviso-cotizacion">
            Cotización del día: USD ${avisoCotizacion.valorVenta.toFixed(2)}
            <button type="button" onClick={() => setAvisoCotizacion(null)} aria-label="Cerrar aviso">
              ×
            </button>
          </p>
        )}
        <main>
          <Outlet />
        </main>
      </div>

      {cotizacionObligatoria ? (
        <CotizacionGateModal onExito={onCotizacionCargada} />
      ) : cajaObligatoria ? (
        <AbrirCajaModal
          onExito={() => {
            setCajaObligatoria(false)
            navigate('/ventas/nueva', { replace: true })
          }}
        />
      ) : (
        <>
          {modalAbierto === 'abrir-caja' && (
            <AbrirCajaModal onClose={() => setModalAbierto(null)} onExito={() => setModalAbierto(null)} />
          )}
          {modalAbierto === 'cerrar-caja' && <CerrarCajaModal onClose={() => setModalAbierto(null)} />}
          {modalAbierto === 'retiro' && <RetiroModal onClose={() => setModalAbierto(null)} />}
          {modalAbierto === 'proveedores' && <ProveedoresModal onClose={() => setModalAbierto(null)} />}
        </>
      )}
    </div>
  )
}
