import { Modal } from './Modal'
import type { VentaResponse } from '../types/api'

interface ComprobanteInternoProps {
  venta: VentaResponse
  onCerrar: () => void
}

/**
 * Comprobante interno no fiscal: numeración propia (basada en idVenta, que ya es un correlativo
 * real de la tabla ventas) y un CAE placeholder. El día que haya integración con ARCA, este es el
 * único componente a tocar para mostrar el CAE real — el resto del flujo de ventas no cambia.
 */
export function ComprobanteInterno({ venta, onCerrar }: ComprobanteInternoProps) {
  const numero = String(venta.idVenta).padStart(4, '0')
  const fecha = new Date(venta.fecha).toLocaleString('es-AR')

  return (
    <Modal onClose={onCerrar}>
      <div className="comprobante">
        <h3>COMPROBANTE INTERNO N° {numero}</h3>
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
        <p>CAE: PENDIENTE (ARCA no configurado)</p>
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
