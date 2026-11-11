import type { MedioPago } from '../types/api'

// Solo una vista previa para Cobros: el monto real lo calcula el backend (RecargoService).
// Se cobra neto / (1 - comisión) para que, después de que el posnet se quede con su %, quede el
// precio original: con 20%, $100 se cobran $125.
export function calcularRecargo(neto: number, porcentaje: number): number {
  if (neto <= 0 || porcentaje <= 0) return 0
  const cobrado = Math.ceil((neto / (1 - porcentaje / 100)) * 100 - 1e-6) / 100
  return Math.round((cobrado - neto) * 100) / 100
}

export const ETIQUETA_MEDIO_PAGO: Record<MedioPago, string> = {
  EFECTIVO: 'Efectivo',
  TRANSFERENCIA: 'Transferencia',
  TARJETA: 'Tarjeta',
  TARJETA_CREDITO: 'Tarjeta de crédito',
  TARJETA_DEBITO: 'Tarjeta de débito',
  CUENTA_CORRIENTE: 'Cuenta corriente',
  SALDO_CUENTA_CORRIENTE: 'Saldo cuenta corriente',
}
