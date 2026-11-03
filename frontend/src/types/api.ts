// TECNICO: hace trabajos a domicilio, cobra comisión sobre la mano de obra. No usa la app (sin
// pantallas propias) — es una categoría de empleado, no un rol con acceso al sistema.
export type Rol = 'ADMIN' | 'VENDEDOR' | 'TECNICO'

// SALDO_CUENTA_CORRIENTE: nunca se elige a mano en el <select> de medio de pago — el backend lo
// fuerza solo cuando montoSaldoAplicado cubre el total completo de la venta (ver VentaRequest).
export type MedioPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA' | 'SALDO_CUENTA_CORRIENTE'

export type EstadoVenta = 'CONFIRMADA' | 'PENDIENTE_AUTORIZACION' | 'EN_PROGRESO' | 'ANULADA'

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
  // codigoFabrica si el producto lo tiene, o codigoInterno si no (productos sueltos/cortados a
  // medida) — es el valor que se escanea/imprime en la etiqueta física. Lo asigna el backend, no
  // se envía en el alta/edición.
  codigoBarras: string | null
  descripcion: string
  precioVenta: number | null
  precioCompra: number | null
  // Ancla en USD (dolarización), uso interno/ADMIN — nunca se muestra a un cliente. Null hasta
  // que exista alguna cotización cargada (ver CotizacionService).
  precioVentaUsd: number | null
  precioCompraUsd: number | null
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
  precioCompra?: number
  stockActual?: number
  // A diferencia de los demás campos, "" (string vacío) es un valor válido: limpia el código de
  // fábrica y el producto vuelve a usar codigoInterno como codigoBarras.
  codigoFabrica?: string
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

// 80 CUIT | 96 DNI | 99 sin documento — misma convención que ClienteDocTipo de Factura.
export interface ClienteRequest {
  nombre: string
  docTipo: ClienteDocTipo
  docNro?: string
  condicionIva?: number | null
  telefono?: string
  email?: string
  direccion?: string
}

export interface ClienteResponse {
  idCliente: number
  nombre: string
  docTipo: ClienteDocTipo
  docNro: string | null
  condicionIva: number | null
  telefono: string | null
  email: string | null
  direccion: string | null
  activo: boolean
}

export type TipoMovimientoCuentaCorriente = 'CREDITO' | 'DEBITO'

export interface MovimientoCuentaCorrienteClienteResponse {
  idMovimiento: number
  fecha: string
  tipo: TipoMovimientoCuentaCorriente
  monto: number
  descripcion: string | null
  idNotaCredito: number | null
  idVentaAplicacion: number | null
}

export interface CuentaCorrienteClienteResponse {
  idCliente: number
  nombreCliente: string
  saldoActual: number
  movimientos: MovimientoCuentaCorrienteClienteResponse[]
}

// DEVOLUCION_MERCADERIA y ANULACION_TOTAL devuelven stock; ERROR_TIPEO y DESCUENTO_POSTERIOR no.
export type MotivoNotaCredito =
  | 'DEVOLUCION_MERCADERIA'
  | 'ERROR_TIPEO'
  | 'DESCUENTO_POSTERIOR'
  | 'ANULACION_TOTAL'

export type AlcanceNotaCredito = 'TOTAL' | 'PARCIAL'

export interface DetalleNotaCreditoRequest {
  idDetalleVentaOriginal: number
  cantidad: number
}

// "detalles" solo es obligatorio/se envía cuando alcance=PARCIAL — con TOTAL el backend expande
// solas todas las líneas de la venta por su cantidad remanente.
export interface NotaCreditoRequest {
  idVentaOriginal: number
  idEmpleado: number
  idCliente?: number
  motivo: MotivoNotaCredito
  alcance: AlcanceNotaCredito
  detalles?: DetalleNotaCreditoRequest[]
}

export interface DetalleNotaCreditoResponse {
  idDetalle: number
  idProducto: number | null
  descripcionProducto: string | null
  cantidad: number
  precioUnitario: number
  subtotal: number
}

// estado: EMITIDA (interna, o ya con CAE) | PENDIENTE_FISCAL (esFiscal=true, falta emitir ante
// ARCA) | ERROR (ARCA rechazó o falló la conexión — reintentable con POST .../emitir-fiscal).
export interface NotaCreditoResponse {
  idNotaCredito: number
  idVentaOriginal: number
  idCliente: number
  nombreCliente: string
  fecha: string
  motivo: MotivoNotaCredito
  alcance: AlcanceNotaCredito
  montoTotal: number
  esFiscal: boolean
  estado: 'EMITIDA' | 'PENDIENTE_FISCAL' | 'ERROR'
  puntoVenta: number | null
  tipoComprobante: number | null
  numero: number | null
  cae: string | null
  caeVencimiento: string | null
  errorDetalle: string | null
  detalles: DetalleNotaCreditoResponse[]
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
  // Cuenta corriente: idCliente obligatorio si montoSaldoAplicado > 0. Si el saldo cubre el
  // total completo, el backend ignora "medioPago" y fuerza "SALDO_CUENTA_CORRIENTE".
  idCliente?: number
  montoSaldoAplicado?: number
}

export interface DetalleVentaResponse {
  idDetalle: number
  idProducto: number | null
  descripcionProducto: string
  tipo: TipoLinea
  cantidad: number
  precioUnitario: number
  subtotal: number
  // Texto libre de Producto.proveedor al momento de la venta — null en ítems manuales sin
  // producto de catálogo (mano de obra, descripción libre). Sirve para identificar rápido a qué
  // proveedor corresponde un producto vendido si la venta falla o hay un reclamo.
  proveedorProducto: string | null
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
  // Cliente del catálogo nuevo (cuenta corriente) — distinto de clienteNombre/clienteTelefono
  // arriba, que son texto libre de Trabajo a domicilio.
  idCliente: number | null
  nombreCliente: string | null
  montoSaldoAplicado: number | null
  detalles: DetalleVentaResponse[]
}

// 80 = CUIT | 96 = DNI | 99 = Consumidor Final (clienteDocNro va vacío en ese caso).
export type ClienteDocTipo = 80 | 96 | 99

export interface FacturarVentaRequest {
  clienteDocTipo: ClienteDocTipo
  clienteDocNro?: string
  // Nombre/razón social del cliente: WSFEv1 no lo pide, pero toda factura real de ARCA lo
  // imprime — el backend lo exige junto con clienteDocNro cuando clienteDocTipo no es 99.
  clienteNombre?: string
  // CondicionIVAReceptorId de ARCA: obligatorio con CUIT, con DNI o Consumidor Final se asume 5.
  condicionIvaReceptor?: number
}

// Datos del padrón de ARCA. condicionIva es una sugerencia deducida de los impuestos inscriptos.
export interface ContribuyenteResponse {
  cuit: string
  nombre: string
  tipoPersona: string | null
  domicilio: string | null
  estadoClave: string | null
  condicionIva: number | null
  advertencia: string | null
}

// estado: PENDIENTE (creada, todavía sin CAE) | EMITIDA | ERROR.
export interface FacturaFiscalResponse {
  idFactura: number
  idVenta: number
  puntoVenta: number
  tipoComprobante: number
  numero: number | null
  clienteDocTipo: number
  clienteDocNro: string | null
  clienteNombre: string | null
  condicionIvaReceptor: number | null
  cae: string | null
  caeVencimiento: string | null
  importe: number
  estado: 'PENDIENTE' | 'EMITIDA' | 'ERROR'
  errorDetalle: string | null
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

export interface AbrirCajaRequest {
  idEmpleado: number
  montoInicial: number
  // Solo se envía cuando las dos APIs de cotización fallaron (ver SesionCajaResponse) y el ADMIN
  // la carga a mano.
  cotizacionManual?: number
}

export interface RetiroSolicitarRequest {
  idEmpleado: number
  monto: number
  motivo: string
  medioPago: MedioPago
}

export interface SesionCajaResponse {
  idSesion: number
  fecha: string
  montoInicial: number
  estado: EstadoSesionCaja
  // Dolarización: cotización USD venta usada para repricear productos al abrir esta sesión.
  cotizacionUsdVenta: number | null
  productosActualizados: number
}

// Gate diario de dolarización: /api/cotizacion/actual devuelve esto si ya se cargó la cotización
// de HOY (204 si no, ver getCotizacionActual). fuente: 'dolarapi.com' | 'dolar-bna' | 'MANUAL'.
export interface CotizacionResponse {
  valorVenta: number
  fecha: string
  fuente: string
  manual: boolean
}

export interface CotizacionManualRequest {
  valorVenta: number
}

export interface SolicitudRetiroResponse {
  idSolicitud: number
  monto: number
  motivo: string
  medioPago: MedioPago
  estado: string
}

export type TipoAutorizacion = 'RETIRO' | 'DESCUENTO'
export type EstadoAutorizacion = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA'

export interface AutorizacionPeticion {
  tipo: TipoAutorizacion
  id: number
  monto: number
  motivo: string
  solicitante: string | null
  fecha: string
  medioPago: string
  // Solo DESCUENTO: total de la venta ya con el descuento aplicado.
  totalVenta: number | null
}

export interface AutorizacionEstado {
  tipo: TipoAutorizacion
  id: number
  estado: EstadoAutorizacion
}

export interface MovimientoCajaResponse {
  idMovimiento: number
  fecha: string
  tipo: string
  medioPago: MedioPago
  monto: number
  motivo: string
}

export interface GastoArqueoResponse {
  idGasto: number
  fecha: string
  nombre: string
  categoria: string | null
  medioPago: MedioPagoProveedor | null
  origen: OrigenPago | null
  importe: number
  restaDelSaldoEsperado: boolean
}

export interface MovimientoProveedorArqueoResponse {
  idMovimiento: number
  fecha: string
  nombreProveedor: string
  descripcion: string | null
  medioPago: string
  origen: OrigenPago | null
  monto: number
  restaDelSaldoEsperado: boolean
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
  // Informativo, no suma a cajaTotalDelDia: ventas pagadas (total o parcialmente) con saldo de
  // cuenta corriente en vez de dinero real.
  ventasPagadasConSaldo: number
  // Todo pago a proveedor y compra a crédito del período, salgan o no de la caja.
  movimientosProveedor: MovimientoProveedorArqueoResponse[]
  pagosProveedorEfectivoCaja: number
  pagosProveedorTransferenciaCaja: number
  gastos: GastoArqueoResponse[]
  gastosEfectivoCaja: number
  gastosTransferenciaCaja: number
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

export type OrdenRanking = 'monto' | 'unidades'

export interface ResumenVentasDTO {
  totalVendido: number
  cantidadVentas: number
  ticketPromedio: number
  unidadesVendidas: number
}

export interface PuntoGraficoDTO {
  etiqueta: string
  valor: number
}

export interface RankingItemDTO {
  etiqueta: string
  valor: number
  monto: number
  unidades: number
}

export interface RankingMarcasResponse {
  mejores: RankingItemDTO[]
  peores: RankingItemDTO[]
}

export interface VentasPorDiaResponse {
  puntos: PuntoGraficoDTO[]
  mejorDia: PuntoGraficoDTO | null
  peorDia: PuntoGraficoDTO | null
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
  medioPago: MedioPagoProveedor
  origen: OrigenPago
}

export interface GastoResponse {
  idGasto: number
  nombre: string
  importe: number
  fecha: string
  categoria: string | null
  medioPago: MedioPagoProveedor | null
  origen: OrigenPago | null
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

export type MedioPagoProveedor = 'EFECTIVO' | 'TRANSFERENCIA'

// CUENTA_CORRIENTE: solo existe para compras — deja el total como deuda con el proveedor, sin
// importar montoPagado (lo decide el backend). No sirve para pagar esa deuda.
export type MedioPagoCompra = MedioPagoProveedor | 'CUENTA_CORRIENTE'

// De dónde sale la plata de un pago a proveedor. Es independiente del medio de pago: solo CAJA
// descuenta de la caja del día, y el saldo esperado solo baja con EFECTIVO + CAJA.
export type OrigenPago = 'CAJA' | 'EXTERNO'

export interface CompraRequest {
  idEmpleado: number
  fecha: string
  proveedorNombre: string
  medioPago: MedioPagoCompra
  // Obligatorio salvo en CUENTA_CORRIENTE, donde no se paga nada al registrar la compra.
  origen?: OrigenPago
  items: CompraItemRequest[]
  // Cuenta corriente de proveedor, nuevo: ausente = se paga todo de una (comportamiento de
  // siempre); un valor menor al total deja el resto como deuda pendiente.
  montoPagado?: number
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
  // string y no MedioPagoCompra: las compras viejas pueden traer TARJETA.
  medioPago: string
  // Null si no se pagó nada al registrar la compra o si es anterior a este campo.
  origen: OrigenPago | null
  totalCompra: number
  montoPagado: number
  saldoPendiente: number
  items: CompraItemResponse[]
}

export interface RegistrarPagoProveedorRequest {
  idEmpleado: number
  monto: number
  medioPago: MedioPagoProveedor
  origen: OrigenPago
}

export interface DetalleDevolucionProveedorRequest {
  idProducto: number
  cantidad: number
  precioUnitario: number
}

export interface DevolucionProveedorRequest {
  idProveedor: number
  idEmpleado: number
  idCompraOriginal?: number
  motivo?: string
  detalles: DetalleDevolucionProveedorRequest[]
}

export interface DetalleDevolucionProveedorResponse {
  idDetalle: number
  idProducto: number
  descripcionProducto: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface DevolucionProveedorResponse {
  idDevolucion: number
  idProveedor: number
  nombreProveedor: string
  idCompraOriginal: number | null
  fecha: string
  motivo: string | null
  montoTotal: number
  detalles: DetalleDevolucionProveedorResponse[]
}

// DEBE_COMPRA (suma deuda) | HABER_PAGO | HABER_DEVOLUCION (ambos restan).
export type TipoMovimientoCuentaCorrienteProveedor = 'DEBE_COMPRA' | 'HABER_PAGO' | 'HABER_DEVOLUCION'

export interface MovimientoCuentaCorrienteProveedorResponse {
  idMovimiento: number
  fecha: string
  tipo: TipoMovimientoCuentaCorrienteProveedor
  monto: number
  medioPago: string | null
  origen: OrigenPago | null
  descripcion: string | null
  idCompra: number | null
  idDevolucion: number | null
}

export interface CompraPendienteResponse {
  idCompra: number
  fecha: string
  totalCompra: number
  // Lo que falta pagar de esta compra, ya descontado el crédito general de devoluciones.
  saldoPendiente: number
}

export interface CuentaCorrienteProveedorResponse {
  idProveedor: number
  nombreProveedor: string
  // Cuánto le debemos al proveedor hoy. Negativo = el proveedor nos debe a nosotros.
  saldoPendiente: number
  // De la más vieja a la más nueva: es el orden en que se les aplica un pago.
  comprasPendientes: CompraPendienteResponse[]
  movimientos: MovimientoCuentaCorrienteProveedorResponse[]
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
