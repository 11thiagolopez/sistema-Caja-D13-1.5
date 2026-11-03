package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.thiago.escenasFX.dto.ResumenDiaDTO;
import com.thiago.escenasFX.dto.ResumenRangoDTO;
import com.thiago.escenasFX.dto.ResumenSesionDTO;
import com.thiago.escenasFX.model.CotizacionDolar;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.Gasto;
import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteProveedor;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.SolicitudRetiro;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.GastoRepository;
import com.thiago.escenasFX.repository.MovimientoCajaRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteProveedorRepository;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.SolicitudRetiroRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class CajaService {

    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";
    private static final String ORIGEN_CAJA = "CAJA";

    // JVM lock que serializa abrirSesion() de punta a punta, incluido el commit — ver el
    // comentario en ese método. Esto asume un solo proceso de backend corriendo a la vez (el
    // despliegue real de este sistema), no múltiples instancias detrás de un balanceador.
    private static final Object LOCK_ABRIR_SESION = new Object();

    private final VentaRepository ventaRepo;
    private final MovimientoCajaRepository movRepo;
    private final SesionCajaRepository sesionRepo;
    private final SolicitudRetiroRepository solicitudRetiroRepo;
    private final CotizacionService cotizacionService;
    private final ProductoRepository productoRepo;
    private final MovimientoCuentaCorrienteProveedorRepository movimientoProveedorRepo;
    private final GastoRepository gastoRepo;
    private final TransactionTemplate transactionTemplate;

    public CajaService(VentaRepository ventaRepo, MovimientoCajaRepository movRepo, SesionCajaRepository sesionRepo,
            SolicitudRetiroRepository solicitudRetiroRepo, CotizacionService cotizacionService, ProductoRepository productoRepo,
            MovimientoCuentaCorrienteProveedorRepository movimientoProveedorRepo, GastoRepository gastoRepo,
            PlatformTransactionManager transactionManager) {
        this.ventaRepo = ventaRepo;
        this.movRepo = movRepo;
        this.sesionRepo = sesionRepo;
        this.solicitudRetiroRepo = solicitudRetiroRepo;
        this.cotizacionService = cotizacionService;
        this.productoRepo = productoRepo;
        this.movimientoProveedorRepo = movimientoProveedorRepo;
        this.gastoRepo = gastoRepo;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Se opera con una sola caja/cajero activo a la vez: no dejamos abrir una sesión nueva si ya
     * hay una ABIERTA, sea de hoy o de un día anterior sin cerrar (ver nota en
     * {@link com.thiago.escenasFX.repository.SesionCajaRepository#findByEstado}). Si quedó una
     * sesión vieja sin cerrar, hay que cerrarla primero — no se auto-cierra sola para no perder de
     * vista la diferencia de caja de ese turno.
     *
     * Sin @Transactional a propósito: el chequeo "no hay sesión ABIERTA" + el insert de la sesión
     * nueva corren dentro de {@code synchronized}, manejando la transacción a mano con
     * TransactionTemplate para que el commit termine ANTES de soltar el lock. Con @Transactional
     * declarativo el commit lo hace el proxy de Spring recién después de que este método retorna
     * — es decir, después de salir del synchronized — así que un segundo hilo podía entrar a
     * synchronized, repetir el chequeo, y todavía no ver la sesión que el primer hilo ya había
     * "guardado" pero no confirmado en la base. Es exactamente el bug real que ya pasó una vez en
     * producción: dos aperturas casi simultáneas pasaban juntas la validación y dejaban dos
     * sesiones ABIERTA.
     */
    public SesionCaja abrirSesion(BigDecimal montoInicial, Empleado empleado, BigDecimal cotizacionManual) {
        synchronized (LOCK_ABRIR_SESION) {
            return transactionTemplate.execute(status ->
                abrirSesionEnTransaccion(montoInicial, empleado, cotizacionManual));
        }
    }

    private SesionCaja abrirSesionEnTransaccion(BigDecimal montoInicial, Empleado empleado,
            BigDecimal cotizacionManual) {
        sesionRepo.findByEstado("ABIERTA").ifPresent(s -> {
            throw new IllegalStateException(
                "Ya existe una sesión de caja abierta (del " + s.getFecha() + "); hay que cerrarla antes de abrir una nueva");
        });

        CotizacionDolar cotizacionEntidad = cotizacionManual != null
            ? cotizacionService.registrarManual(cotizacionManual)
            : cotizacionService.obtenerCotizacionDelDia();
        BigDecimal cotizacion = cotizacionEntidad.getValorVenta();
        int productosActualizados = productoRepo.reajustarPreciosPorCotizacion(cotizacion);

        SesionCaja sesion = new SesionCaja();
        sesion.setFecha(LocalDate.now());
        sesion.setMontoInicial(montoInicial);
        sesion.setEstado("ABIERTA");
        sesion.setEmpleadoApertura(empleado);
        sesion.setCotizacionUsdVenta(cotizacion);
        sesion = sesionRepo.save(sesion);
        sesion.setProductosActualizados(productosActualizados);
        return sesion;
    }

    public SesionCaja obtenerSesionAbierta() {
        return sesionRepo.findByEstado("ABIERTA")
            .orElseThrow(() -> new IllegalStateException("No hay una sesión de caja abierta"));
    }

    /**
     * Variante no-throwing de obtenerSesionAbierta(), usada por el modal de "abrir caja" post
     * login: no es un error de negocio que no haya sesión abierta, es justamente lo que dispara
     * el modal.
     */
    public Optional<SesionCaja> obtenerSesionAbiertaOpcional() {
        return sesionRepo.findByEstado("ABIERTA");
    }

    public SesionCaja cerrarSesionDelDia() {
        SesionCaja sesion = obtenerSesionAbierta();
        sesion.setEstado("CERRADA");
        return sesionRepo.save(sesion);
    }

    /**
     * El MovimientoCaja real recién se crea cuando un ADMIN aprueba la petición: mientras tanto no
     * hay ningún gasto registrado en caja. Si lo pide un ADMIN no hay nada que autorizar, así que
     * el retiro se registra de una; la solicitud se guarda igual, ya CONFIRMADA, para que quede el
     * mismo rastro que en un retiro pedido por un VENDEDOR.
     */
    @Transactional
    public SolicitudRetiro solicitarRetiro(BigDecimal monto, String motivo, String medioPago, Empleado solicitante,
            boolean autorizadoPorAdmin) {
        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setMonto(monto);
        solicitud.setMotivo(motivo);
        solicitud.setMedioPago(medioPago);
        solicitud.setEmpleadoSolicitante(solicitante);

        if (autorizadoPorAdmin) {
            return confirmarRetiro(solicitud, solicitante);
        }

        solicitud.setEstado("PENDIENTE");
        return solicitudRetiroRepo.save(solicitud);
    }

    public List<SolicitudRetiro> listarRetirosPendientes() {
        return solicitudRetiroRepo.findByEstadoOrderByCreadoEnAsc("PENDIENTE");
    }

    @Transactional
    public SolicitudRetiro aprobarRetiro(Integer idSolicitud, Empleado admin) {
        return confirmarRetiro(obtenerPendienteConLock(idSolicitud), admin);
    }

    @Transactional
    public SolicitudRetiro rechazarRetiro(Integer idSolicitud, Empleado admin) {
        SolicitudRetiro solicitud = obtenerPendienteConLock(idSolicitud);
        solicitud.setEstado("RECHAZADA");
        solicitud.setResueltoPor(admin);
        solicitud.setResueltoEn(LocalDateTime.now());
        return solicitudRetiroRepo.save(solicitud);
    }

    // Con lock: sin esto, dos ADMIN aprobando a la vez podían pasar los dos el chequeo de PENDIENTE
    // y registrar dos veces la misma salida de plata.
    private SolicitudRetiro obtenerPendienteConLock(Integer idSolicitud) {
        SolicitudRetiro solicitud = solicitudRetiroRepo.buscarPorIdConLock(idSolicitud)
            .orElseThrow(() -> new IllegalArgumentException("Solicitud de retiro no existe: " + idSolicitud));
        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new IllegalStateException("La solicitud de retiro ya fue procesada");
        }
        return solicitud;
    }

    private SolicitudRetiro confirmarRetiro(SolicitudRetiro solicitud, Empleado resueltoPor) {
        solicitud.setEstado("CONFIRMADA");
        solicitud.setResueltoPor(resueltoPor);
        solicitud.setResueltoEn(LocalDateTime.now());
        SolicitudRetiro guardada = solicitudRetiroRepo.save(solicitud);
        registrarRetiro(guardada.getMonto(), guardada.getMotivo(), guardada.getMedioPago(),
            guardada.getEmpleadoSolicitante());
        return guardada;
    }

    private MovimientoCaja registrarRetiro(BigDecimal monto, String motivo, String medioPago, Empleado empleado) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setTipo("RETIRO");
        movimiento.setMedioPago(medioPago);
        movimiento.setMonto(monto);
        movimiento.setMotivo(motivo);
        movimiento.setEmpleado(empleado);
        sesionRepo.findByEstado("ABIERTA").ifPresent(movimiento::setSesion);
        return movRepo.save(movimiento);
    }

    /**
     * Arqueo de hoy. Delegado en calcularResumenPorRango(hoy, hoy) para que el monto inicial se
     * calcule igual en los dos casos (sumando cada sesión del período, no solo la que esté
     * ABIERTA en este instante — antes se perdía el monto inicial apenas se cerraba la última
     * sesión del día).
     */
    public ResumenDiaDTO calcularResumenDelDia() {
        LocalDate hoy = LocalDate.now();
        return calcularResumenPorRango(hoy, hoy).getTotal();
    }

    /**
     * Arqueo de un turno puntual (una SesionCaja), para saber exactamente cuánto vendió y qué
     * diferencia de caja tuvo un vendedor específico.
     */
    public ResumenDiaDTO calcularResumenDeSesion(Integer idSesion) {
        SesionCaja sesion = sesionRepo.findById(idSesion)
            .orElseThrow(() -> new IllegalArgumentException("Sesión de caja no existe: " + idSesion));

        List<Venta> ventas = ventaRepo.findBySesion_IdSesionAndEstado(idSesion, ESTADO_CONFIRMADA);
        List<MovimientoCaja> retiros = movRepo.findBySesion_IdSesion(idSesion);
        List<MovimientoCuentaCorrienteProveedor> movimientosProveedor =
            movimientoProveedorRepo.findBySesion_IdSesion(idSesion);
        List<Gasto> gastos = gastoRepo.findBySesion_IdSesion(idSesion);

        return armarResumen(ventas, retiros, movimientosProveedor, gastos, sesion.getMontoInicial());
    }

    /**
     * Arqueo de un rango de fechas (ej. un mes entero): el total combinado del período, más el
     * desglose de cada sesión/turno individual dentro de ese rango.
     */
    public ResumenRangoDTO calcularResumenPorRango(LocalDate desde, LocalDate hasta) {
        List<SesionCaja> sesiones = sesionRepo.findByFechaBetweenOrderByFechaAscIdSesionAsc(desde, hasta);

        List<ResumenSesionDTO> resumenesPorSesion = sesiones.stream()
            .map(s -> new ResumenSesionDTO(s, calcularResumenDeSesion(s.getIdSesion())))
            .toList();

        // El monto inicial del total del período es la suma de lo declarado en cada sesión
        // abierta durante ese rango (cada turno arranca con su propio efectivo físico).
        BigDecimal montoInicialTotal = sesiones.stream()
            .map(SesionCaja::getMontoInicial)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime desdeDT = desde.atStartOfDay();
        LocalDateTime hastaDT = hasta.atTime(23, 59, 59);

        // Ventas CONFIRMADA por fecha (no por sesión): más inclusivo, no depende de que la venta
        // haya tenido una caja abierta al momento de registrarse.
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desdeDT, hastaDT, ESTADO_CONFIRMADA);
        List<MovimientoCaja> retiros = movRepo.findByFechaBetween(desdeDT, hastaDT);
        List<MovimientoCuentaCorrienteProveedor> movimientosProveedor =
            movimientoProveedorRepo.findByFechaBetween(desdeDT, hastaDT);
        // Por cuándo se registró, no por Gasto.fecha: cargar un gasto atrasado no altera turnos cerrados.
        List<Gasto> gastos = gastoRepo.findByCreadoEnBetween(desdeDT, hastaDT);

        ResumenDiaDTO total = armarResumen(ventas, retiros, movimientosProveedor, gastos, montoInicialTotal);

        return new ResumenRangoDTO(desde, hasta, total, resumenesPorSesion);
    }

    private ResumenDiaDTO armarResumen(List<Venta> ventas, List<MovimientoCaja> retiros,
            List<MovimientoCuentaCorrienteProveedor> movimientosLedger, List<Gasto> gastos, BigDecimal montoInicial) {
        BigDecimal ventasEfectivo = sumarVentasPorMedio(ventas, "EFECTIVO");
        BigDecimal ventasTransferencia = sumarVentasPorMedio(ventas, "TRANSFERENCIA");
        BigDecimal ventasTarjeta = sumarVentasPorMedio(ventas, "TARJETA");
        // Informativo: una venta pagada 100% con saldo ya queda afuera de los tres buckets de
        // arriba (su medioPago es "SALDO_CUENTA_CORRIENTE", no matchea ninguno) — esto es la
        // suma total de saldo aplicado (ventas pagadas del todo o en parte con saldo), para que
        // se vea en el arqueo sin confundirse con dinero real cobrado.
        BigDecimal ventasPagadasConSaldo = ventas.stream()
            .map(CajaService::saldoAplicadoDe)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal retirosEfectivo = sumarRetirosPorMedio(retiros, "EFECTIVO");
        BigDecimal retirosTransferencia = sumarRetirosPorMedio(retiros, "TRANSFERENCIA");

        // Se muestran todos; solo restan los que salieron de la caja. Los que traen medioPago son
        // los pagos y las compras a crédito: el DEBE de una compra pagada duplicaría su pago.
        List<MovimientoCuentaCorrienteProveedor> movimientosProveedor = movimientosLedger.stream()
            .filter(m -> m.getMedioPago() != null)
            .toList();
        BigDecimal pagosProveedorEfectivoCaja = sumarPagosProveedorDeCaja(movimientosProveedor, "EFECTIVO");
        BigDecimal pagosProveedorTransferenciaCaja = sumarPagosProveedorDeCaja(movimientosProveedor, "TRANSFERENCIA");

        BigDecimal gastosEfectivoCaja = sumarGastosDeCaja(gastos, "EFECTIVO");
        BigDecimal gastosTransferenciaCaja = sumarGastosDeCaja(gastos, "TRANSFERENCIA");

        BigDecimal efectivoFinal = montoInicial.add(ventasEfectivo).subtract(retirosEfectivo)
            .subtract(pagosProveedorEfectivoCaja).subtract(gastosEfectivoCaja);
        BigDecimal totalDigital = ventasTransferencia.subtract(retirosTransferencia)
            .subtract(pagosProveedorTransferenciaCaja).subtract(gastosTransferenciaCaja).add(ventasTarjeta);
        BigDecimal cajaTotal = efectivoFinal.add(totalDigital);

        return new ResumenDiaDTO(ventas, retiros, montoInicial, ventasEfectivo, ventasTransferencia, ventasTarjeta,
            retirosEfectivo, retirosTransferencia, efectivoFinal, totalDigital, cajaTotal, ventasPagadasConSaldo,
            movimientosProveedor, pagosProveedorEfectivoCaja, pagosProveedorTransferenciaCaja,
            gastos, gastosEfectivoCaja, gastosTransferenciaCaja);
    }

    public static boolean restaDelSaldoEsperado(MovimientoCuentaCorrienteProveedor m) {
        return "EFECTIVO".equals(m.getMedioPago()) && ORIGEN_CAJA.equals(m.getOrigen());
    }

    public static boolean restaDelSaldoEsperado(Gasto g) {
        return "EFECTIVO".equals(g.getMedioPago()) && ORIGEN_CAJA.equals(g.getOrigen());
    }

    private BigDecimal sumarGastosDeCaja(List<Gasto> gastos, String medioPago) {
        return gastos.stream()
            .filter(g -> medioPago.equals(g.getMedioPago()) && ORIGEN_CAJA.equals(g.getOrigen()))
            .map(Gasto::getImporte)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumarPagosProveedorDeCaja(List<MovimientoCuentaCorrienteProveedor> movimientos, String medioPago) {
        return movimientos.stream()
            .filter(m -> medioPago.equals(m.getMedioPago()) && ORIGEN_CAJA.equals(m.getOrigen()))
            .map(MovimientoCuentaCorrienteProveedor::getMonto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Una venta con pago parcial en saldo sigue teniendo medioPago="EFECTIVO"/etc. por el resto,
    // pero totalVenta incluye la parte pagada con saldo — hay que restarla para no contar como
    // dinero real cobrado algo que en realidad fue un crédito de cuenta corriente.
    private BigDecimal sumarVentasPorMedio(List<Venta> ventas, String medioPago) {
        return ventas.stream()
            .filter(v -> medioPago.equalsIgnoreCase(v.getMedioPago()))
            .map(v -> v.getTotalVenta().subtract(saldoAplicadoDe(v)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal saldoAplicadoDe(Venta v) {
        return v.getMontoSaldoAplicado() != null ? v.getMontoSaldoAplicado() : BigDecimal.ZERO;
    }

    private BigDecimal sumarRetirosPorMedio(List<MovimientoCaja> retiros, String medioPago) {
        return retiros.stream()
            .filter(m -> medioPago.equalsIgnoreCase(m.getMedioPago()))
            .map(MovimientoCaja::getMonto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
