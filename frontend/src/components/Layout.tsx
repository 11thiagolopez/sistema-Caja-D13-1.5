import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getSesionAbierta } from '../api/caja'
import { getCotizacionActual } from '../api/cotizacion'
import type { CotizacionResponse, Rol } from '../types/api'
import { AbrirCajaModal } from './AbrirCajaModal'
import { AutorizacionesProvider, useAutorizaciones } from './AutorizacionesPendientes'
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
    label: 'Compras',
    roles: ['ADMIN'],
    items: [
      { label: 'Agregar compra', to: '/compras/nueva' },
      { label: 'Consultar compras', to: '/compras' },
      { label: 'Cuenta corriente de proveedores', to: '/proveedores/cuenta-corriente' },
    ],
  },
  {
    label: 'Reportes',
    roles: ['ADMIN'],
    items: [
      { label: 'Gráficos y balance', to: '/reportes' },
      { label: 'Ventas por vendedor', to: '/vendedores/ventas' },
      { label: 'Comisiones', to: '/vendedores/comisiones' },
      { label: 'Compras por proveedor', to: '/compras/pagos-proveedores' },
      { label: 'Ventas por marca', to: '/reportes/marcas' },
      { label: 'Ventas por forma de pago', to: '/reportes/forma-pago' },
    ],
  },
  {
    label: 'Reportes de trabajos a domicilio',
    roles: ['ADMIN'],
    items: [
      { label: 'Reporte de trabajos', to: '/reportes/trabajos' },
      { label: 'Técnicos', to: '/tecnicos' },
    ],
  },
]

const ENLACES_SUELTOS: NavItem[] = [
  { label: 'Peticiones', to: '/autorizaciones', roles: ['ADMIN'] },
  { label: 'Cobros', to: '/ventas/nueva' },
  { label: 'Productos', to: '/productos' },
  { label: 'Presupuestos', to: '/presupuestos' },
  { label: 'Historial de ventas', to: '/ventas/historial', roles: ['ADMIN'] },
  { label: 'Trabajo a domicilio', to: '/ventas/domicilio', roles: ['ADMIN'] },
  { label: 'Clientes', to: '/clientes', roles: ['ADMIN'] },
  { label: 'Vendedores', to: '/vendedores', roles: ['ADMIN'] },
  { label: 'Gastos', to: '/gastos', roles: ['ADMIN'] },
  { label: 'Facturación', to: '/comprobantes', roles: ['ADMIN'] },
  { label: 'Recargos de tarjeta', to: '/configuracion/recargos', roles: ['ADMIN'] },
]

// En celular el menú flota sobre el contenido: arranca cerrado y se cierra solo al elegir una opción.
function esCelular(): boolean {
  return window.matchMedia('(max-width: 768px)').matches
}

function puedeVer(roles: Rol[] | undefined, rol: Rol | undefined): boolean {
  return !roles || (rol != null && roles.includes(rol))
}

export function Layout() {
  const { sesion } = useAuth()
  return (
    <AutorizacionesProvider key={sesion?.idEmpleado} idEmpleado={sesion?.idEmpleado ?? 0} rol={sesion?.rol}>
      <LayoutContenido />
    </AutorizacionesProvider>
  )
}

function LayoutContenido() {
  const { sesion, logout } = useAuth()
  const navigate = useNavigate()
  const [seccionAbierta, setSeccionAbierta] = useState<string | null>('Caja')
  const [modalAbierto, setModalAbierto] = useState<ModalKey | null>(null)
  const [cajaObligatoria, setCajaObligatoria] = useState(false)
  const [sidebarAbierta, setSidebarAbierta] = useState(() => !esCelular())
  const { peticiones } = useAutorizaciones()

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

  function cerrarMenuEnCelular() {
    if (esCelular()) {
      setSidebarAbierta(false)
    }
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
            // end: /vendedores no tiene que quedar marcado en /vendedores/ventas, que es de Reportes.
            <NavLink key={item.to} to={item.to!} end className="sidebar-enlace" onClick={cerrarMenuEnCelular}>
              {item.label}
              {item.to === '/autorizaciones' && peticiones.length > 0 && (
                <span className="sidebar-contador">{peticiones.length}</span>
              )}
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
                          onClick={() => {
                            setModalAbierto(item.modal!)
                            cerrarMenuEnCelular()
                          }}
                        >
                          {item.label}
                        </button>
                      ) : (
                        <NavLink key={item.to} to={item.to!} className="sidebar-enlace" onClick={cerrarMenuEnCelular}>
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
              onClick={() => {
                setModalAbierto('proveedores')
                cerrarMenuEnCelular()
              }}
            >
              Proveedores
            </button>
          )}
        </nav>
      </aside>
      {sidebarAbierta && (
        <button type="button" className="sidebar-fondo" aria-label="Cerrar menú" onClick={() => setSidebarAbierta(false)} />
      )}

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
