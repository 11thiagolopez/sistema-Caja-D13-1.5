import { createContext, useContext } from 'react'

export type ModalCaja = 'abrir-caja' | 'cerrar-caja' | 'retiro' | 'proveedores'

interface AccionesCajaValor {
  /** null mientras se consulta; true/false según haya una sesión de caja abierta. */
  cajaAbierta: boolean | null
  abrirModal: (modal: ModalCaja) => void
  /** Sube cada vez que se cierra un modal de caja: las pantallas que muestran montos lo usan para refrescar. */
  version: number
}

export const AccionesCajaContext = createContext<AccionesCajaValor>({
  cajaAbierta: null,
  abrirModal: () => {},
  version: 0,
})

export function useAccionesCaja(): AccionesCajaValor {
  return useContext(AccionesCajaContext)
}
