import { useCallback, useEffect, useMemo, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiRequestError } from '../api/client'
import { getSesionAbierta } from '../api/caja'
import { getCotizacionActual } from '../api/cotizacion'
import type { CotizacionResponse, Rol } from '../types/api'
import { AbrirCajaModal } from './AbrirCajaModal'
import { AccionesCajaContext, type ModalCaja } from './accionesCajaContexto'
import { AutorizacionesProvider, useAutorizaciones } from './AutorizacionesPendientes'
import { BuscadorGlobal } from './BuscadorGlobal'
import { Campanita } from './Campanita'
import { CerrarCajaModal } from './CerrarCajaModal'
import { Logo, LogoIcono } from './Logo'
import { RetiroModal } from './RetiroModal'
import { ProveedoresModal } from './ProveedoresModal'
import { CotizacionGateModal } from './CotizacionGateModal'

interface NavItem {
  label: string
  to?: string
  modal?: ModalCaja
  roles?: Rol[]
  /** Otras rutas en las que el ítem queda marcado (ej. "Compras" también en /compras/nueva). */
  tambienEn?: string[]
}

interface NavSection {
  label: string
  items: NavItem[]
}

// Las acciones que tienen botón dentro de su pantalla (abrir/cerrar caja, retiros, nueva compra)
// no se repiten acá: el menú es para ir a un lugar, no para hacer algo.
const SECCIONES: NavSection[] = [
  {
    label: 'Caja',
    items: [
      { label: 'Cobros', to: '/ventas/nueva' },
      { label: 'Resumen del día', to: '/caja/resumen', roles: ['ADMIN'] },
      { label: 'Peticiones', to: '/autorizaciones', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Ventas',
    items: [
      { label: 'Historial de ventas', to: '/ventas/historial', roles: ['ADMIN'] },
      { label: 'Presupuestos', to: '/presupuestos' },
      { label: 'Clientes', to: '/clientes', roles: ['ADMIN'] },
      { label: 'Facturación', to: '/comprobantes', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Trabajos',
    items: [
      { label: 'Trabajo a domicilio', to: '/ventas/domicilio', roles: ['ADMIN'] },
      { label: 'Técnicos', to: '/tecnicos', roles: ['ADMIN'] },
      { label: 'Reporte de trabajos', to: '/reportes/trabajos', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Gestión',
    items: [
      { label: 'Productos', to: '/productos' },
      { label: 'Compras', to: '/compras', roles: ['ADMIN'], tambienEn: ['/compras/nueva'] },
      { label: 'Proveedores', modal: 'proveedores', roles: ['ADMIN'] },
      { label: 'Cuenta corriente de proveedores', to: '/proveedores/cuenta-corriente', roles: ['ADMIN'] },
      { label: 'Gastos', to: '/gastos', roles: ['ADMIN'] },
      { label: 'Vendedores', to: '/vendedores', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Reportes',
    items: [
      { label: 'Gráficos y balance', to: '/reportes', roles: ['ADMIN'] },
      { label: 'Ventas por vendedor', to: '/vendedores/ventas', roles: ['ADMIN'] },
      { label: 'Comisiones', to: '/vendedores/comisiones', roles: ['ADMIN'] },
      { label: 'Compras por proveedor', to: '/compras/pagos-proveedores', roles: ['ADMIN'] },
      { label: 'Ventas por marca', to: '/reportes/marcas', roles: ['ADMIN'] },
      { label: 'Ventas por forma de pago', to: '/reportes/forma-pago', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Configuración',
    items: [{ label: 'Recargos de tarjeta', to: '/configuracion/recargos', roles: ['ADMIN'] }],
  },
]

const NOMBRE_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  VENDEDOR: 'Vendedor',
  TECNICO: 'Técnico',
}

// En celular el menú flota sobre el contenido: arranca cerrado y se cierra solo al elegir una opción.
function esCelular(): boolean {
  return window.matchMedia('(max-width: 768px)').matches
}

function puedeVer(roles: Rol[] | undefined, rol: Rol | undefined): boolean {
  return !roles || (rol != null && roles.includes(rol))
}

function iniciales(nombre: string | undefined): string {
  if (!nombre) return '?'
  const partes = nombre.trim().split(/\s+/)
  return ((partes[0]?.[0] ?? '') + (partes[1]?.[0] ?? '')).toUpperCase() || '?'
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
  const location = useLocation()
  const [modalAbierto, setModalAbierto] = useState<ModalCaja | null>(null)
  const [cajaObligatoria, setCajaObligatoria] = useState(false)
  const [cajaAbierta, setCajaAbierta] = useState<boolean | null>(null)
  const [versionCaja, setVersionCaja] = useState(0)
  const [sidebarAbierta, setSidebarAbierta] = useState(() => !esCelular())
  const { peticiones } = useAutorizaciones()

  // Dolarización: gate diario que va ANTES que el de caja — nadie (ningún rol) puede operar el
  // sistema sin la cotización del día cargada. Arranca en true (bloquea) hasta que el chequeo
  // inicial confirme que ya existe una cotización de hoy.
  const [cotizacionObligatoria, setCotizacionObligatoria] = useState(true)
  const [avisoCotizacion, setAvisoCotizacion] = useState<CotizacionResponse | null>(null)
  const esTecnico = sesion?.rol === 'TECNICO'

  // Ninguno de los dos gates aplica a TECNICO: no opera caja ni le importa la cotización del día
  // (y el backend le devuelve 403 en los dos GET, así que ni se pueden resolver solos).
  useEffect(() => {
    if (esTecnico) return
    getCotizacionActual()
      .then((cotizacion) => {
        setCotizacionObligatoria(!cotizacion)
      })
      .catch((err) => {
        // Sin permiso (403) = este usuario no opera con la cotización: bloquearlo detrás de un
        // modal que el backend nunca le va a dejar resolver lo deja trabado para siempre (bug
        // reportado con el técnico). Cualquier otro error sigue bloqueando, como siempre.
        if (err instanceof ApiRequestError && err.status === 403) {
          setCotizacionObligatoria(false)
        }
      })
  }, [esTecnico])

  const consultarCaja = useCallback(() => {
    return getSesionAbierta().then((sesionCaja) => {
      setCajaAbierta(sesionCaja != null)
      return sesionCaja
    })
  }, [])

  // Se dispara una sola vez al entrar a cualquier pantalla logueada: si no hay una sesión de
  // caja ABIERTA, fuerza el modal de "abrir caja" (ambos roles) antes de dejar hacer nada más.
  useEffect(() => {
    if (esTecnico) return
    consultarCaja()
      .then((sesionCaja) => {
        if (!sesionCaja) {
          setCajaObligatoria(true)
        }
      })
      .catch(() => {
        // Sin permiso o sin conexión: no se fuerza el modal de abrir caja (mismo motivo que arriba).
      })
  }, [esTecnico, consultarCaja])

  function cerrarModal() {
    setModalAbierto(null)
    setVersionCaja((v) => v + 1)
    consultarCaja().catch(() => {})
  }

  const accionesCaja = useMemo(
    () => ({
      cajaAbierta,
      version: versionCaja,
      abrirModal: (modal: ModalCaja) => setModalAbierto(modal),
    }),
    [cajaAbierta, versionCaja],
  )

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

  const secciones = SECCIONES.map((seccion) => ({
    ...seccion,
    items: seccion.items.filter((item) => puedeVer(item.roles, sesion?.rol)),
  })).filter((seccion) => seccion.items.length > 0)

  return (
    <AccionesCajaContext.Provider value={accionesCaja}>
      <div className="layout">
        {!esTecnico && (
          <aside className={`sidebar ${sidebarAbierta ? '' : 'sidebar-cerrada'}`} aria-label="Menú principal">
            <div className="sidebar-marca">
              <Logo tamano={40} claro />
            </div>
            <nav>
              {secciones.map((seccion) => (
                <div key={seccion.label} className="sidebar-seccion">
                  <div className="sidebar-seccion-titulo">{seccion.label}</div>
                  {seccion.items.map((item) =>
                    item.modal ? (
                      <button
                        key={item.label}
                        type="button"
                        className="sidebar-enlace"
                        onClick={() => {
                          setModalAbierto(item.modal!)
                          cerrarMenuEnCelular()
                        }}
                      >
                        {item.label}
                      </button>
                    ) : (
                      // end: /vendedores no tiene que quedar marcado en /vendedores/ventas, que es de Reportes.
                      <NavLink
                        key={item.to}
                        to={item.to!}
                        end
                        className={({ isActive }) =>
                          `sidebar-enlace ${isActive || item.tambienEn?.includes(location.pathname) ? 'active' : ''}`
                        }
                        onClick={cerrarMenuEnCelular}
                      >
                        {item.label}
                        {item.to === '/autorizaciones' && peticiones.length > 0 && (
                          <span className="sidebar-contador" aria-label={`${peticiones.length} pendientes`}>
                            {peticiones.length}
                          </span>
                        )}
                      </NavLink>
                    ),
                  )}
                </div>
              ))}
            </nav>
          </aside>
        )}
        {!esTecnico && sidebarAbierta && (
          <button type="button" className="sidebar-fondo" aria-label="Cerrar menú" onClick={() => setSidebarAbierta(false)} />
        )}

        <div className="layout-contenido">
          <header className="layout-topbar">
            {esTecnico ? (
              <LogoIcono tamano={36} />
            ) : (
              <button
                type="button"
                className="sidebar-toggle"
                onClick={() => setSidebarAbierta((actual) => !actual)}
                aria-label={sidebarAbierta ? 'Ocultar menú' : 'Mostrar menú'}
                aria-expanded={sidebarAbierta}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              </button>
            )}
            {!esTecnico && <BuscadorGlobal rol={sesion?.rol} />}
            <div className="topbar-usuario">
              {(sesion?.rol === 'ADMIN' || esTecnico) && <Campanita rol={sesion?.rol} />}
              <div className="topbar-perfil">
                <span className="topbar-avatar" aria-hidden="true">
                  {iniciales(sesion?.nombre)}
                </span>
                <span className="topbar-perfil-texto">
                  <strong>{sesion?.nombre}</strong>
                  <span>{sesion?.rol ? NOMBRE_ROL[sesion.rol] : ''}</span>
                </span>
              </div>
              <button type="button" className="btn-suave btn-chico" onClick={onLogout}>
                Salir
              </button>
            </div>
          </header>
          {avisoCotizacion && (
            <p className="aviso-cotizacion" role="status">
              Cotización del día: USD ${avisoCotizacion.valorVenta.toFixed(2)}
              <button type="button" onClick={() => setAvisoCotizacion(null)} aria-label="Cerrar aviso">
                ×
              </button>
            </p>
          )}
          <main id="contenido">
            <Outlet />
          </main>
        </div>

        {!esTecnico &&
          (cotizacionObligatoria ? (
            <CotizacionGateModal onExito={onCotizacionCargada} />
          ) : cajaObligatoria ? (
            <AbrirCajaModal
              onExito={() => {
                setCajaObligatoria(false)
                setCajaAbierta(true)
                navigate('/ventas/nueva', { replace: true })
              }}
            />
          ) : (
            <>
              {modalAbierto === 'abrir-caja' && <AbrirCajaModal onClose={cerrarModal} onExito={cerrarModal} />}
              {modalAbierto === 'cerrar-caja' && <CerrarCajaModal onClose={cerrarModal} />}
              {modalAbierto === 'retiro' && <RetiroModal onClose={cerrarModal} />}
              {modalAbierto === 'proveedores' && <ProveedoresModal onClose={() => setModalAbierto(null)} />}
            </>
          ))}
      </div>
    </AccionesCajaContext.Provider>
  )
}
