import type { ReactNode } from 'react'

interface ModalProps {
  title?: string
  onClose?: () => void
  wide?: boolean
  children: ReactNode
}

/**
 * Overlay genérico (sin portal ni focus-trap, igual que el patrón original de
 * ComprobanteInterno). Sin onClose, el modal no se puede descartar clickeando afuera ni con una
 * cruz — usado para flujos obligatorios como "abrir caja" post-login.
 */
export function Modal({ title, onClose, wide, children }: ModalProps) {
  return (
    <div className="modal-overlay">
      <div className={wide ? 'modal modal-wide' : 'modal'}>
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
