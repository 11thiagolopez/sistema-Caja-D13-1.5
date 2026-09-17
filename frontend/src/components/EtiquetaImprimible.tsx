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
 * @media print de App.css, con su propio @page etiqueta-58mm.
 *
 * Es la única pantalla que sigue imprimiendo así (HTML/CSS renderizado en vivo, no un PDF del
 * backend) — el comprobante de venta/remito y la factura fiscal dejaron de tener su propia vista
 * @media print (ver HistorialVentas.tsx/RegistrarVenta.tsx, sección "Ver"/"Ver comprobante") y
 * ahora abren directamente el PDF real que genera el backend, para que no puedan divergir en
 * formato ni en cómo imprimen.
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
