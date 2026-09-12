import { useEffect, useRef } from 'react'
import JsBarcode from 'jsbarcode'
import { Modal } from './Modal'

interface EtiquetaImprimibleProps {
  codigoBarras: string
  descripcion: string
  precioVenta: number | null
  onCerrar: () => void
}

/**
 * Etiqueta para impresora térmica de recibos 58mm (no autoadhesiva — se pega con cinta). El botón
 * "Imprimir etiqueta" dispara window.print() acotado a `.etiqueta-imprimir` vía la regla de
 * @media print de App.css (mismo patrón que ComprobanteInterno/.comprobante, pero con su propio
 * @page de 58mm en vez de tamaño carta).
 */
export function EtiquetaImprimible({ codigoBarras, descripcion, precioVenta, onCerrar }: EtiquetaImprimibleProps) {
  const svgRef = useRef<SVGSVGElement>(null)

  useEffect(() => {
    if (!svgRef.current) return
    JsBarcode(svgRef.current, codigoBarras, {
      format: 'CODE128',
      width: 1.5,
      height: 40,
      displayValue: true,
      fontSize: 12,
    })
  }, [codigoBarras])

  return (
    <Modal onClose={onCerrar}>
      <div className="etiqueta-imprimir">
        <svg ref={svgRef} />
        <p className="etiqueta-descripcion">{descripcion}</p>
        {precioVenta != null && <p className="etiqueta-precio">${precioVenta.toFixed(2)}</p>}
      </div>
      <div className="etiqueta-acciones">
        <button type="button" onClick={() => window.print()}>
          Imprimir etiqueta
        </button>
        <button type="button" onClick={onCerrar}>
          Cerrar
        </button>
      </div>
    </Modal>
  )
}
