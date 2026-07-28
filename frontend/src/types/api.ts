export type Rol = 'ADMIN' | 'VENDEDOR'

export type MedioPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA'

export type EstadoVenta = 'CONFIRMADA' | 'PENDIENTE_AUTORIZACION'

export type EstadoSesionCaja = 'ABIERTA' | 'CERRADA'

export interface ApiError {
  message: string
}

export interface LoginRequest {
  usuario: string
  password: string
}

export interface LoginResponse {
  idEmpleado: number
  nombre: string
  usuario: string
  rol: Rol
  token: string
}

export interface Producto {
  idProducto: number
  rubro: string
  familia: string
  marca: string
  correlativo: string
  codigoInterno: string
  proveedor: string
  codigoFabrica: string
  descripcion: string
  precioVenta: number
  precioCompra: number | null
  stockActual: number
}

export interface DetalleVentaRequest {
  idProducto: number
  cantidad: number
  precioUnitario: number
}

export interface VentaRequest {
  idEmpleado: number
  medioPago: MedioPago
  tipoComprobante?: string
  detalles: DetalleVentaRequest[]
  descuento?: number
  motivoDescuento?: string
}

export interface DetalleVentaResponse {
  idProducto: number
  descripcionProducto: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface VentaResponse {
  idVenta: number
  fecha: string
  idEmpleado: number
  medioPago: MedioPago
  tipoComprobante: string | null
  totalVenta: number
  descuento: number
  estado: EstadoVenta
  detalles: DetalleVentaResponse[]
}

export interface ConfirmarDescuentoRequest {
  idVenta: number
  codigo: string
}

export interface AbrirCajaRequest {
  idEmpleado: number
  montoInicial: number
}

export interface RetiroSolicitarRequest {
  idEmpleado: number
  monto: number
  motivo: string
  medioPago: MedioPago
}

export interface ConfirmarRetiroRequest {
  idSolicitud: number
  codigo: string
}

export interface SesionCajaResponse {
  idSesion: number
  fecha: string
  montoInicial: number
  estado: EstadoSesionCaja
}

export interface SolicitudRetiroResponse {
  idSolicitud: number
  monto: number
  motivo: string
  medioPago: MedioPago
  estado: string
  otpExpiraEn: string
}

export interface MovimientoCajaResponse {
  idMovimiento: number
  fecha: string
  tipo: string
  medioPago: MedioPago
  monto: number
  motivo: string
}

export interface ResumenDiaResponse {
  ventas: VentaResponse[]
  retiros: MovimientoCajaResponse[]
  montoInicial: number
  ventasEfectivo: number
  ventasTransferencia: number
  ventasTarjeta: number
  retirosEfectivo: number
  retirosTransferencia: number
  efectivoFinal: number
  totalDigital: number
  cajaTotalDelDia: number
}

export interface ResumenSesionResponse {
  idSesion: number
  fecha: string
  estado: EstadoSesionCaja
  empleadoApertura: string
  resumen: ResumenDiaResponse
}

export interface ResumenRangoResponse {
  desde: string
  hasta: string
  total: ResumenDiaResponse
  sesiones: ResumenSesionResponse[]
}

export interface ProductoRankingDTO {
  idProducto: number
  descripcion: string
  cantidadVendida: number
  totalFacturado: number
}

export interface BalanceFinancieroResponse {
  desde: string
  hasta: string
  ingresosPorVentas: number
  costoMercaderia: number
  gastosOperativos: number
  gananciaNeta: number
}
