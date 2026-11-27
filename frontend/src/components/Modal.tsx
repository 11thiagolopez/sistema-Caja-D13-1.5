import { useEffect, type ReactNode } from 'react'

interface ModalProps {
  title?: string
  onClose?: () => void
  wide?: boolean
  children: ReactNode
}

/**
 * Overlay genérico (sin portal ni focus-trap, igual que el patrón que sigue usando
 * EtiquetaImprimible). Sin onClose, el modal no se puede descartar clickeando afuera, con la cruz
 * ni con Escape — usado para flujos obligatorios como "abrir caja" post-login.
 */
export function Modal({ title, onClose, wide, children }: ModalProps) {
  useEffect(() => {
    if (!onClose) return
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose?.()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="modal-overlay">
      <div className={wide ? 'modal modal-wide' : 'modal'} role="dialog" aria-modal="true" aria-label={title}>
        {(title || onClose) && (
          <div className="modal-header">
            {title && <h3>{title}</h3>}
            {onClose && (
              <button type="button" className="modal-cerrar" onClick={onClose} aria-label="Cerrar">
                ×
              </button>
            )}
          </div>
        )}
        {children}
      </div>
    </div>
  )
}
