import { useState, type FormEvent } from 'react'
import { Modal } from './Modal'
import { formatoPesos } from '../utils/formato'

// Billetes argentinos para sugerir con cuánto paga: redondea el total hacia arriba a cada uno.
const BILLETES = [500, 1000, 2000, 5000, 10000, 20000]

function sugerencias(total: number): number[] {
  const montos = BILLETES.map((b) => Math.ceil(total / b) * b).filter((m) => m > total)
  return [...new Set(montos)].sort((a, b) => a - b).slice(0, 4)
}

interface VueltoModalProps {
  total: number
  enviando: boolean
  /** Con cuánto pagó el cliente (o null si pagó justo). */
  onConfirmar: (pagaCon: number) => void
  onCancelar: () => void
}

/**
 * Antes de registrar una venta en efectivo: pregunta con cuánto paga el cliente y muestra el
 * vuelto. Es solo una ayuda para el cajero; a la caja entra el total de la venta, no lo que se
 * recibió.
 */
export function VueltoModal({ total, enviando, onConfirmar, onCancelar }: VueltoModalProps) {
  const [texto, setTexto] = useState('')
  const pagaCon = texto.trim() === '' ? null : Number(texto.replace(',', '.'))
  const valido = pagaCon != null && Number.isFinite(pagaCon)
  const vuelto = valido ? pagaCon - total : null
  const falta = vuelto != null && vuelto < -0.004

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (enviando) return
    // Vacío = pagó justo.
    if (pagaCon == null) {
      onConfirmar(total)
      return
    }
    if (!valido || falta) return
    onConfirmar(pagaCon)
  }

  return (
    <Modal title="Cobro en efectivo" onClose={enviando ? undefined : onCancelar}>
      <form onSubmit={onSubmit} className="vuelto">
        <div className="vuelto-total">
          <span>Total a cobrar</span>
          <strong>{formatoPesos(total)}</strong>
        </div>

        <label>
          ¿Con cuánto paga el cliente?
          <input
            type="number"
            inputMode="decimal"
            min={0}
            step="0.01"
            autoFocus
            placeholder="Dejalo vacío si paga justo"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            aria-invalid={falta || undefined}
            aria-describedby="vuelto-resultado"
          />
        </label>

        <div className="vuelto-sugerencias" role="group" aria-label="Montos rápidos">
          <button type="button" className="btn-suave" onClick={() => setTexto(String(total))}>
            Justo
          </button>
          {sugerencias(total).map((m) => (
            <button key={m} type="button" className="btn-suave" onClick={() => setTexto(String(m))}>
              {formatoPesos(m)}
            </button>
          ))}
        </div>

        <div
          id="vuelto-resultado"
          className={`vuelto-resultado ${falta ? 'vuelto-resultado-falta' : ''}`}
          aria-live="polite"
        >
          {falta ? (
            <>
              <span>Falta</span>
              <strong>{formatoPesos(-(vuelto ?? 0))}</strong>
            </>
          ) : (
            <>
              <span>Vuelto</span>
              <strong>{formatoPesos(vuelto != null && vuelto > 0 ? vuelto : 0)}</strong>
            </>
          )}
        </div>

        <div className="formulario-acciones">
          <button type="button" className="btn-secundario" onClick={onCancelar} disabled={enviando}>
            Volver
          </button>
          <button type="submit" disabled={enviando || falta || (pagaCon != null && !valido)}>
            {enviando && <span className="spinner" aria-hidden="true" />}
            {enviando ? 'Registrando…' : 'Confirmar venta'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
