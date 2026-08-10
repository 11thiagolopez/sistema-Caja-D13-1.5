// TECNICO: hace trabajos a domicilio, cobra comisión sobre la mano de obra. No usa la app (sin
// pantallas propias) — es una categoría de empleado, no un rol con acceso al sistema.
export type Rol = 'ADMIN' | 'VENDEDOR' | 'TECNICO'

export type MedioPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA'

export type EstadoVenta = 'CONFIRMADA' | 'PENDIENTE_AUTORIZACION' | 'EN_PROGRESO'

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
  // Nombre de la marca (ej. "KALOP"), listo para mostrar y para buscar. Puede ser null en
  // productos históricos que todavía no tienen la marca cargada.
  marca: string | null
  // Código de 2 dígitos de la marca (Marca.codigo), el que se usa para armar codigoInterno.
  numeroMarca: string | null
  correlativo: string
  codigoInterno: string
  proveedor: string
  codigoFabrica: string | null
  descripcion: string
  precioVenta: number | null
  precioCompra: number | null
  stockActual: number
  activo: boolean
}

// Edición en línea desde la tabla de Productos: cada campo es opcional, el backend solo
// actualiza el que venga presente (semántica PATCH). No incluye rubro/código porque forman
// parte de codigoInterno.
export interface ProductoUpdateRequest {
  descripcion?: string
  marca?: string
  precioVenta?: number
  stockActual?: number
}

export interface ProductoRequest {
  rubro: string
  familia: string
  // Nombre libre (ej. "KALOP"), no un código: el backend resuelve/crea la Marca correspondiente.
  marca: string
  proveedor: string
  codigoFabrica?: string
  descripcion: string
  precioVenta: number
  precioCompra?: number
  stockActual: number
}

export interface MarcaResponse {
  idMarca: number
  nombre: string
  codigo: string
}

export interface ProveedorRequest {
  nombre: string
  contacto?: string
  telefono?: string
  email?: string
}

export interface ProveedorResponse {
  idProveedor: number
  nombre: string
  contacto: string | null
  telefono: string | null
  email: string | null
  activo: boolean
}

export interface CargarStockRequest {
  codigo: string
  cantidad: number
}

// Categoría de una línea de detalle: artículo de catálogo, copia de llave (legado, ya no
// seleccionable desde Cobros) o mano de obra de un trabajo a domicilio.
export type TipoLinea = 'ARTICULO' | 'COPIA' | 'SERVICIO'

// MOSTRADOR = venta de catálogo (Cobros) | DOMICILIO = trabajo a domicilio.
export type TipoVenta = 'MOSTRADOR' | 'DOMICILIO'

export type EstadoTrabajo = 'AGENDADO' | 'EN_CURSO' | 'COMPLETADO' | 'COBRADO'

// O trae idProducto (línea de catálogo, descuenta stock) o trae descripcion (mano de obra u otro
// ítem manual, no toca stock) — viene uno de los dos.
export interface DetalleVentaRequest {
  idProducto?: number
  descripcion?: string
  tipo: TipoLinea
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
  idProducto: number | null
  descripcionProducto: string
  tipo: TipoLinea
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
  clienteEmail: string | null
  comprobanteEnviadoPorEmail: boolean
  tipoVenta: TipoVenta
  clienteNombre: string | null
  clienteTelefono: string | null
  direccionTrabajo: string | null
  descripcionTrabajo: string | null
  estadoTrabajo: EstadoTrabajo | null
  idEmpleadoTecnico: number | null
  nombreTecnico: string | null
  detalles: DetalleVentaResponse[]
}

// O trae idVenta (actualizar un trabajo existente) o no (crear uno nuevo).
export interface TrabajoDomicilioRequest {
  idVenta?: number
  idEmpleado: number
  idEmpleadoTecnico?: number
  clienteNombre: string
  clienteTelefono?: string
  direccionTrabajo?: string
  descripcionTrabajo?: string
  estadoTrabajo?: EstadoTrabajo
  detalles: DetalleVentaRequest[]
  cerrar: boolean
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
  comisionesPagadas: number
  gananciaNeta: number
}

export interface GastoRequest {
  idEmpleado: number
  nombre: string
  importe: number
  fecha: string
  categoria?: string
}

export interface GastoResponse {
  idGasto: number
  nombre: string
  importe: number
  fecha: string
  categoria: string | null
  empleadoRegistroNombre: string | null
  creadoEn: string
}

export interface NuevoProductoEnCompraRequest {
  rubro: string
  familia: string
  marca: string
  descripcion: string
  codigoFabrica?: string
}

export interface CompraItemRequest {
  idProducto?: number
  nuevoProducto?: NuevoProductoEnCompraRequest
  cantidad: number
  precioCompraUnitario: number
  precioVentaUnitario?: number
}

export interface CompraRequest {
  idEmpleado: number
  fecha: string
  proveedorNombre: string
  medioPago: MedioPago
  items: CompraItemRequest[]
}

export interface CompraItemResponse {
  idItem: number
  idProducto: number
  descripcionProducto: string
  marcaProducto: string | null
  cantidad: number
  precioCompraUnitario: number
  precioVentaUnitario: number | null
  subtotal: number
}

export interface CompraResponse {
  idCompra: number
  fecha: string
  idProveedor: number | null
  nombreProveedor: string | null
  medioPago: MedioPago
  totalCompra: number
  items: CompraItemResponse[]
}

export interface PagoProveedorDTO {
  idProveedor: number
  nombreProveedor: string
  totalPagado: number
  cantidadCompras: number
}

export interface ProductoComprasRankingDTO {
  idProducto: number
  descripcion: string
  cantidadComprada: number
  totalPagado: number
}

export interface EmpleadoRequest {
  nombre: string
  usuario: string
  password: string
  email?: string
  rol: Rol
  comision?: number
}

export interface EmpleadoUpdateRequest {
  nombre: string
  usuario: string
  password?: string
  email?: string
  rol: Rol
  comision?: number
}

export interface EmpleadoResponse {
  idEmpleado: number
  nombre: string
  usuario: string
  rol: Rol
  email: string | null
  comision: number | null
  activo: boolean
}

export interface ComisionEmpleadoDTO {
  idEmpleado: number
  nombreEmpleado: string
  comisionPorcentaje: number | null
  gananciaGenerada: number
  comisionCalculada: number
  cantidadVentas: number
}

export interface MarcaRankingDTO {
  marca: string
  cantidadVendida: number
  totalFacturado: number
}

export interface FormaPagoResumenDTO {
  medioPago: MedioPago
  cantidadVentas: number
  totalFacturado: number
}

// O trae idProducto (línea de catálogo) o trae descripcion (ítem manual, ej. "Apertura de
// cerradura") — viene uno de los dos.
export interface DetallePresupuestoRequest {
  idProducto?: number
  descripcion?: string
  cantidad: number
  precioUnitario: number
}

export interface PresupuestoRequest {
  idEmpleado: number
  clienteNombre: string
  clienteEmail?: string
  clienteTelefono?: string
  detalles: DetallePresupuestoRequest[]
}

export interface DetallePresupuestoResponse {
  idProducto: number | null
  descripcionProducto: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface PresupuestoResponse {
  idPresupuesto: number
  fecha: string
  idEmpleado: number
  nombreEmpleado: string
  clienteNombre: string
  clienteEmail: string | null
  clienteTelefono: string | null
  totalPresupuesto: number
  enviadoPorEmail: boolean
  detalles: DetallePresupuestoResponse[]
}
