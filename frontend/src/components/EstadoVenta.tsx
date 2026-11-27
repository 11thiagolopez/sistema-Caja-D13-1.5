import type { VentaResponse } from '../types/api'

/** Estado de una venta con color y texto (nunca solo color). */
export function EstadoVenta({ venta }: { venta: Pick<VentaResponse, 'estado' | 'estadoFactura'> }) {
  if (venta.estado === 'ANULADA') return <span className="badge badge-error">Anulada</span>
  if (venta.estado === 'PENDIENTE_AUTORIZACION') return <span className="badge badge-aviso">Pendiente</span>
  if (venta.estado === 'EN_PROGRESO') return <span className="badge badge-aviso">En curso</span>
  if (venta.estadoFactura === 'FACTURADA') return <span className="badge badge-info">Facturada</span>
  return <span className="badge badge-ok">Cobrada</span>
}
