import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { ApiRequestError } from '../api/client'
import { getEstadoPeticiones, getPeticiones } from '../api/autorizaciones'
import { descargarVentaPdf } from '../api/ventas'
import { verBlob } from '../utils/descargarBlob'
import type { AutorizacionPeticion, EstadoAutorizacion, Rol, TipoAutorizacion } from '../types/api'

const INTERVALO_MS = 15000

export interface AutorizacionPendiente {
  tipo: TipoAutorizacion
  id: number // idSolicitud o idVenta, según tipo
  monto: number
  motivo: string
  estado?: EstadoAutorizacion
}

interface AutorizacionesContextValue {
  agregar: (autorizacion: AutorizacionPendiente) => void
  // Solo ADMIN: las peticiones de los vendedores que esperan respuesta.
  peticiones: AutorizacionPeticion[]
  refrescarPeticiones: () => Promise<void>
}

const AutorizacionesContext = createContext<AutorizacionesContextValue | null>(null)

function titulo({ tipo, id }: AutorizacionPendiente): string {
  return tipo === 'RETIRO' ? `Retiro #${id}` : `Descuento de venta #${id}`
}

function textoEstado({ tipo, estado }: AutorizacionPendiente): string {
  if (estado === 'APROBADA') return 'Aprobado por el administrador.'
  if (estado === 'RECHAZADA') {
    return tipo === 'RETIRO'
      ? 'Rechazado por el administrador: no se registró el retiro.'
      : 'Rechazado por el administrador: la venta se anuló y el stock volvió. Cobrala de nuevo sin descuento.'
  }
  return 'Esperando respuesta del administrador...'
}

/**
 * Lo que el vendedor pidió y todavía espera la respuesta del ADMIN, a la vista desde cualquier
 * pantalla: pedir un retiro o un descuento no bloquea seguir cobrando. Se guarda en localStorage
 * (por empleado) para que sobreviva a cambiar de pantalla, recargar o volver a entrar, y el backend
 * solo informa cómo quedó cada id: el VENDEDOR no debe tener ningún endpoint que liste "sus ventas"
 * (ver SecurityConfig).
 */
export function AutorizacionesProvider({
  idEmpleado,
  rol,
  children,
}: {
  idEmpleado: number
  rol?: Rol
  children: ReactNode
}) {
  const storageKey = `d13.autorizaciones.${idEmpleado}`
  const [pendientes, setPendientes] = useState<AutorizacionPendiente[]>(() => {
    try {
      return JSON.parse(localStorage.getItem(storageKey) ?? '[]') as AutorizacionPendiente[]
    } catch {
      return []
    }
  })
  const [plegado, setPlegado] = useState(false)
  const [errorComprobante, setErrorComprobante] = useState<string | null>(null)
  const [peticiones, setPeticiones] = useState<AutorizacionPeticion[]>([])

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(pendientes))
    } catch {
      // Sin localStorage el panel sigue andando, solo no sobrevive a una recarga.
    }
  }, [storageKey, pendientes])

  const refrescarPeticiones = useCallback(async () => {
    setPeticiones(await getPeticiones())
  }, [])

  // Un corte de red momentáneo no tiene que molestar a nadie: el próximo intento lo resuelve.
  useEffect(() => {
    if (rol !== 'ADMIN') return
    const refrescar = () => void refrescarPeticiones().catch(() => {})
    refrescar()
    const intervalo = setInterval(refrescar, INTERVALO_MS)
    return () => clearInterval(intervalo)
  }, [rol, refrescarPeticiones])

  const hayEsperando = pendientes.some((p) => !p.estado || p.estado === 'PENDIENTE')

  useEffect(() => {
    if (!hayEsperando) return
    async function actualizarEstados() {
      const idsDe = (tipo: TipoAutorizacion, lista: AutorizacionPendiente[]) =>
        lista.filter((p) => p.tipo === tipo).map((p) => p.id)
      const estados = await getEstadoPeticiones(idsDe('RETIRO', pendientes), idsDe('DESCUENTO', pendientes))
      setPendientes((actual) =>
        actual.map((p) => {
          const nuevo = estados.find((e) => e.tipo === p.tipo && e.id === p.id)
          return nuevo ? { ...p, estado: nuevo.estado } : p
        }),
      )
    }
    const intervalo = setInterval(() => void actualizarEstados().catch(() => {}), INTERVALO_MS)
    return () => clearInterval(intervalo)
  }, [hayEsperando, pendientes])

  function agregar(autorizacion: AutorizacionPendiente) {
    setPendientes((actual) => [...actual, autorizacion])
    setPlegado(false)
  }

  // Mismo PDF que "Ver comprobante" en Cobros: cuando aprueban el descuento, esa pantalla ya puede
  // estar en otra venta.
  async function verComprobante(idVenta: number) {
    setErrorComprobante(null)
    try {
      verBlob(await descargarVentaPdf(idVenta))
    } catch (err) {
      setErrorComprobante(err instanceof ApiRequestError ? err.message : 'No se pudo abrir el comprobante')
    }
  }

  function quitar({ tipo, id }: AutorizacionPendiente) {
    setPendientes((actual) => actual.filter((p) => p.tipo !== tipo || p.id !== id))
  }

  return (
    <AutorizacionesContext.Provider value={{ agregar, peticiones, refrescarPeticiones }}>
      {children}
      {pendientes.length > 0 && (
        <aside className="autorizaciones">
          <button
            type="button"
            className="autorizaciones-titulo"
            onClick={() => setPlegado((actual) => !actual)}
            aria-expanded={!plegado}
          >
            Mis peticiones ({pendientes.length})
            <span>{plegado ? '▴' : '▾'}</span>
          </button>
          {!plegado && (
            <div className="autorizaciones-lista">
              {errorComprobante && <p className="error">{errorComprobante}</p>}
              {pendientes.map((p) => (
                <div key={`${p.tipo}-${p.id}`} className={`autorizacion autorizacion-${(p.estado ?? 'PENDIENTE').toLowerCase()}`}>
                  <strong>{titulo(p)}</strong>
                  <span>
                    ${p.monto.toFixed(2)} — {p.motivo}
                  </span>
                  <p>{textoEstado(p)}</p>
                  {p.tipo === 'DESCUENTO' && p.estado === 'APROBADA' && (
                    <button type="button" onClick={() => verComprobante(p.id)}>
                      Ver comprobante
                    </button>
                  )}
                  {p.estado && p.estado !== 'PENDIENTE' && (
                    <button type="button" className="boton-link" onClick={() => quitar(p)}>
                      Listo
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </aside>
      )}
    </AutorizacionesContext.Provider>
  )
}

export function useAutorizaciones(): AutorizacionesContextValue {
  const context = useContext(AutorizacionesContext)
  if (!context) {
    throw new Error('useAutorizaciones debe usarse dentro de un AutorizacionesProvider')
  }
  return context
}
