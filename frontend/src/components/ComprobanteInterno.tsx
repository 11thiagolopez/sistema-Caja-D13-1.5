import { Modal } from './Modal'
import type { VentaResponse } from '../types/api'

interface ComprobanteInternoProps {
  venta: VentaResponse
  onCerrar: () => void
}

/**
 * Comprobante interno no fiscal: numeración propia (basada en idVenta, que ya es un correlativo
 * real de la tabla ventas), marcado con una "X" junto al título — igual que una Factura A/B/C
 * muestra su letra — para que no se confunda con la factura fiscal real (ver Consulta de ventas,
 * columna "Factura fiscal", que sí emite un CAE de ARCA).
 *
 * Cada ítem se muestra en dos líneas (descripción arriba, cantidad×precio = subtotal abajo) en
 * vez de una tabla de columnas lado a lado: es el mismo formato angosto que imprime la Xprinter
 * térmica del mostrador (ver @media print en App.css, `.comprobante`) — una tabla con 4 columnas
 * no entra legible en los ~48mm de ancho imprimible real del papel (58mm de rollo, 210mm de largo
 * fijo — medida confirmada por el dueño en la impresora física). El `@page comprobante-58mm`
 * declara ese tamaño fijo (no `auto`): pedirle al navegador un alto automático cuando la
 * impresora ya tiene un tamaño de página fijo configurado en Windows es lo que hacía que todo
 * saliera escalado a un cuadrado diminuto e ilegible.
 */
export function ComprobanteInterno({ venta, onCerrar }: ComprobanteInternoProps) {
  const numero = String(venta.idVenta).padStart(4, '0')
  const fecha = new Date(venta.fecha).toLocaleString('es-AR')

  return (
    <Modal onClose={onCerrar}>
      <div className="comprobante">
        <div className="comprobante-titulo">
          <span className="comprobante-x">X</span>
          <h3>COMPROBANTE INTERNO N° {numero}</h3>
        </div>
        <p className="comprobante-aviso">(no válido como factura fiscal)</p>
        <p>Fecha: {fecha}</p>
        <div className="comprobante-items">
          {venta.detalles.map((d, i) => (
            <div className="comprobante-item" key={i}>
              <div className="comprobante-item-desc">
                {d.cantidad}x {d.descripcionProducto}
              </div>
              <div className="comprobante-item-fila">
                <span>${d.precioUnitario.toFixed(2)} c/u</span>
                <span>${d.subtotal.toFixed(2)}</span>
              </div>
            </div>
          ))}
        </div>
        {venta.descuento > 0 && <p>Descuento: ${venta.descuento.toFixed(2)}</p>}
        <p className="comprobante-total">
          <strong>TOTAL: ${venta.totalVenta.toFixed(2)}</strong>
        </p>
        <p>Medio de pago: {venta.medioPago}</p>
        <div className="comprobante-acciones">
          <button type="button" onClick={() => window.print()}>
            Imprimir
          </button>
          <button type="button" onClick={onCerrar}>
            Cerrar
          </button>
        </div>
      </div>
    </Modal>
  )
}
