import type { ReactNode } from 'react'
import { useAccionesCaja } from './accionesCajaContexto'

/**
 * Los botones de caja que antes estaban en el menú lateral: van arriba a la derecha de la pantalla
 * donde se usan (Cobros y Resumen del día). Con la caja cerrada solo se ofrece abrirla.
 */
export function BotonesCaja({ children }: { children?: ReactNode }) {
  const { cajaAbierta, abrirModal } = useAccionesCaja()
  if (cajaAbierta === false) {
    return (
      <>
        <button type="button" onClick={() => abrirModal('abrir-caja')}>
          Abrir caja
        </button>
        {children}
      </>
    )
  }
  return (
    <>
      <button type="button" className="btn-peligro-borde" onClick={() => abrirModal('cerrar-caja')}>
        Cerrar caja
      </button>
      <button type="button" className="btn-secundario" onClick={() => abrirModal('retiro')}>
        Retiro de dinero
      </button>
      {children}
    </>
  )
}

export function EstadoCaja() {
  const { cajaAbierta } = useAccionesCaja()
  if (cajaAbierta == null) return null
  return cajaAbierta ? (
    <span className="badge badge-ok">Caja abierta</span>
  ) : (
    <span className="badge">Caja cerrada</span>
  )
}
