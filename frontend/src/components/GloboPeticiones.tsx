import { useRef, useState, type CSSProperties, type PointerEvent, type ReactNode } from 'react'
import type { EstadoAutorizacion } from '../types/api'

const STORAGE_KEY = 'd13.globoPeticiones'
const TAMANO = 56 // Los mismos valores que .globo-peticiones en App.css
const MARGEN = 8
const UMBRAL_ARRASTRE = 6 // Un dedo siempre se mueve un poco al tocar: menos que esto sigue siendo un toque.

interface Posicion {
  lado: 'izquierda' | 'derecha'
  altura: number // De 0 (arriba) a 1 (abajo): así sigue en pantalla si cambia el tamaño o se gira el celular.
}

function limitar(valor: number, minimo: number, maximo: number): number {
  return Math.min(Math.max(valor, minimo), maximo)
}

function leerPosicion(): Posicion {
  try {
    const guardada = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Posicion | null
    if (guardada && (guardada.lado === 'izquierda' || guardada.lado === 'derecha') && typeof guardada.altura === 'number') {
      return { lado: guardada.lado, altura: limitar(guardada.altura, 0, 1) }
    }
  } catch {
    // Sin localStorage el globo arranca siempre en el lugar de siempre.
  }
  return { lado: 'derecha', altura: 0.7 }
}

function textoEstado(estado: EstadoAutorizacion): string {
  if (estado === 'RECHAZADA') return 'hay una rechazada'
  if (estado === 'APROBADA') return 'hay una aprobada'
  return 'esperando respuesta'
}

/**
 * Globo que se arrastra y queda pegado al costado más cercano. No se abre solo, para que pedir algo
 * nunca tape lo que el usuario está por tocar (en celular, el botón de registrar la venta). Abierto,
 * tocar afuera solo lo cierra: no aprieta sin querer lo que haya debajo.
 */
export function GloboPeticiones({
  cantidad,
  estado,
  children,
}: {
  cantidad: number
  estado: EstadoAutorizacion
  children: ReactNode
}) {
  const [posicion, setPosicion] = useState(leerPosicion)
  const [arrastre, setArrastre] = useState<{ x: number; y: number } | null>(null)
  const [abierto, setAbierto] = useState(false)
  const agarre = useRef<{ inicioX: number; inicioY: number; offsetX: number; offsetY: number } | null>(null)
  const seMovio = useRef(false)

  function alApretar(e: PointerEvent<HTMLButtonElement>) {
    const caja = e.currentTarget.getBoundingClientRect()
    agarre.current = {
      inicioX: e.clientX,
      inicioY: e.clientY,
      offsetX: e.clientX - caja.left,
      offsetY: e.clientY - caja.top,
    }
    seMovio.current = false
    e.currentTarget.setPointerCapture(e.pointerId)
  }

  function alMover(e: PointerEvent<HTMLButtonElement>) {
    const actual = agarre.current
    if (!actual) return
    if (!seMovio.current && Math.hypot(e.clientX - actual.inicioX, e.clientY - actual.inicioY) < UMBRAL_ARRASTRE) return
    seMovio.current = true
    setAbierto(false)
    setArrastre({
      x: limitar(e.clientX - actual.offsetX, MARGEN, window.innerWidth - TAMANO - MARGEN),
      y: limitar(e.clientY - actual.offsetY, MARGEN, window.innerHeight - TAMANO - MARGEN),
    })
  }

  function alSoltar() {
    agarre.current = null
    if (!arrastre) return
    const nueva: Posicion = {
      lado: arrastre.x + TAMANO / 2 < window.innerWidth / 2 ? 'izquierda' : 'derecha',
      altura: limitar((arrastre.y - MARGEN) / (window.innerHeight - TAMANO - 2 * MARGEN), 0, 1),
    }
    setPosicion(nueva)
    setArrastre(null)
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nueva))
    } catch {
      // Sin localStorage el globo se mueve igual, solo no recuerda el lugar al recargar.
    }
  }

  // El click también llega al soltar un arrastre; por teclado llega sin pasar por los eventos de arriba.
  function alTocar() {
    if (seMovio.current) {
      seMovio.current = false
      return
    }
    setAbierto((actual) => !actual)
  }

  const clase = `globo-peticiones globo-${posicion.lado}${posicion.altura < 0.5 ? ' globo-arriba' : ' globo-abajo'}`
  const variables = { '--globo-altura': posicion.altura } as CSSProperties

  return (
    <div className={clase} style={variables}>
      {abierto && (
        <>
          <button type="button" className="globo-fondo" aria-label="Cerrar mis peticiones" onClick={() => setAbierto(false)} />
          <aside className="globo-panel">
            <header className="globo-panel-titulo">
              <strong>Mis peticiones ({cantidad})</strong>
              <button type="button" className="modal-cerrar" aria-label="Cerrar" onClick={() => setAbierto(false)}>
                ×
              </button>
            </header>
            {children}
          </aside>
        </>
      )}
      <button
        type="button"
        className={`globo globo-${estado.toLowerCase()}${arrastre ? ' globo-arrastrando' : ''}`}
        style={arrastre ? { left: arrastre.x, right: 'auto', top: arrastre.y } : undefined}
        aria-label={`Mis peticiones: ${cantidad}, ${textoEstado(estado)}`}
        aria-expanded={abierto}
        onPointerDown={alApretar}
        onPointerMove={alMover}
        onPointerUp={alSoltar}
        onPointerCancel={alSoltar}
        onClick={alTocar}
      >
        $<span className="globo-contador">{cantidad}</span>
      </button>
    </div>
  )
}
