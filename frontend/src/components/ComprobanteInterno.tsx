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
        <table>
          <thead>
            <tr>
              <th>Producto</th>
              <th>Cantidad</th>
              <th>Precio unitario</th>
              <th>Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {venta.detalles.map((d, i) => (
              <tr key={i}>
                <td>{d.descripcionProducto}</td>
                <td>{d.cantidad}</td>
                <td>{d.precioUnitario.toFixed(2)}</td>
                <td>{d.subtotal.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {venta.descuento > 0 && <p>Descuento: {venta.descuento.toFixed(2)}</p>}
        <p>
          <strong>TOTAL: {venta.totalVenta.toFixed(2)}</strong>
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
