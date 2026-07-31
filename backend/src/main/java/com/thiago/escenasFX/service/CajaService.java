package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.ResumenDiaDTO;
import com.thiago.escenasFX.dto.ResumenRangoDTO;
import com.thiago.escenasFX.dto.ResumenSesionDTO;
import com.thiago.escenasFX.exception.AuthenticationFailedException;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.MovimientoCaja;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.SolicitudRetiro;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.MovimientoCajaRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.SolicitudRetiroRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class CajaService {

    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";

    private final VentaRepository ventaRepo;
    private final MovimientoCajaRepository movRepo;
    private final SesionCajaRepository sesionRepo;
    private final SolicitudRetiroRepository solicitudRetiroRepo;
    private final OtpService otpService;
    private final EmailService emailService;

    public CajaService(VentaRepository ventaRepo, MovimientoCajaRepository movRepo, SesionCajaRepository sesionRepo,
            SolicitudRetiroRepository solicitudRetiroRepo, OtpService otpService, EmailService emailService) {
        this.ventaRepo = ventaRepo;
        this.movRepo = movRepo;
        this.sesionRepo = sesionRepo;
        this.solicitudRetiroRepo = solicitudRetiroRepo;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    /**
     * Se opera con una sola caja/cajero activo a la vez: no dejamos abrir una sesión nueva si ya
     * hay una ABIERTA, sea de hoy o de un día anterior sin cerrar (ver nota en
     * {@link com.thiago.escenasFX.repository.SesionCajaRepository#findByEstado}). Si quedó una
     * sesión vieja sin cerrar, hay que cerrarla primero — no se auto-cierra sola para no perder de
     * vista la diferencia de caja de ese turno.
     */
    public SesionCaja abrirSesion(BigDecimal montoInicial, Empleado empleado) {
        sesionRepo.findByEstado("ABIERTA").ifPresent(s -> {
            throw new IllegalStateException(
                "Ya existe una sesión de caja abierta (del " + s.getFecha() + "); hay que cerrarla antes de abrir una nueva");
        });

        SesionCaja sesion = new SesionCaja();
        sesion.setFecha(LocalDate.now());
        sesion.setMontoInicial(montoInicial);
        sesion.setEstado("ABIERTA");
        sesion.setEmpleadoApertura(empleado);
        return sesionRepo.save(sesion);
    }

    public SesionCaja obtenerSesionAbierta() {
        return sesionRepo.findByEstado("ABIERTA")
            .orElseThrow(() -> new IllegalStateException("No hay una sesión de caja abierta"));
    }

    public SesionCaja cerrarSesionDelDia() {
        SesionCaja sesion = obtenerSesionAbierta();
        sesion.setEstado("CERRADA");
        return sesionRepo.save(sesion);
    }

    /**
     * Genera el OTP y lo envía por email a los ADMIN. El MovimientoCaja real recién se crea
     * en confirmarRetiro(), una vez validado el código: mientras tanto no hay ningún gasto
     * registrado en caja.
     */
    @Transactional
    public SolicitudRetiro solicitarRetiro(BigDecimal monto, String motivo, String medioPago, Empleado solicitante) {
        String codigo = otpService.generarCodigo();

        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setMonto(monto);
        solicitud.setMotivo(motivo);
        solicitud.setMedioPago(medioPago);
        solicitud.setEmpleadoSolicitante(solicitante);
        solicitud.setOtpHash(otpService.hash(codigo));
        solicitud.setOtpExpiraEn(otpService.nuevaExpiracion());
        solicitud.setEstado("PENDIENTE");
        solicitud = solicitudRetiroRepo.save(solicitud);

        emailService.enviarOtpAAdmins(
            "Autorización de retiro de caja - solicitud #" + solicitud.getIdSolicitud(),
            "Empleado solicitante: " + solicitante.getNombre() + "\n"
                + "Se solicitó un retiro de $" + monto + " (" + motivo + ").\n"
                + "Código de confirmación: " + codigo + "\n"
                + "Vence en " + OtpService.VIGENCIA_MINUTOS + " minutos.");

        return solicitud;
    }

    @Transactional
    public MovimientoCaja confirmarRetiro(Integer idSolicitud, String codigoIngresado) {
        SolicitudRetiro solicitud = solicitudRetiroRepo.findById(idSolicitud)
            .orElseThrow(() -> new IllegalArgumentException("Solicitud de retiro no existe: " + idSolicitud));

        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new IllegalStateException("La solicitud de retiro ya fue procesada");
        }
        if (solicitud.getOtpExpiraEn().isBefore(LocalDateTime.now())) {
            solicitud.setEstado("EXPIRADA");
            solicitudRetiroRepo.save(solicitud);
            throw new IllegalStateException("El código OTP expiró, hay que generar una nueva solicitud de retiro");
        }
        if (!otpService.coincide(codigoIngresado, solicitud.getOtpHash())) {
            throw new AuthenticationFailedException("Código OTP inválido");
        }

        solicitud.setEstado("CONFIRMADA");
        solicitudRetiroRepo.save(solicitud);

        return registrarRetiro(solicitud.getMonto(), solicitud.getMotivo(), solicitud.getMedioPago(),
            solicitud.getEmpleadoSolicitante());
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

        return armarResumen(ventas, retiros, sesion.getMontoInicial());
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

        ResumenDiaDTO total = armarResumen(ventas, retiros, montoInicialTotal);

        return new ResumenRangoDTO(desde, hasta, total, resumenesPorSesion);
    }

    private ResumenDiaDTO armarResumen(List<Venta> ventas, List<MovimientoCaja> retiros, BigDecimal montoInicial) {
        BigDecimal ventasEfectivo = sumarVentasPorMedio(ventas, "EFECTIVO");
        BigDecimal ventasTransferencia = sumarVentasPorMedio(ventas, "TRANSFERENCIA");
        BigDecimal ventasTarjeta = sumarVentasPorMedio(ventas, "TARJETA");

        BigDecimal retirosEfectivo = sumarRetirosPorMedio(retiros, "EFECTIVO");
        BigDecimal retirosTransferencia = sumarRetirosPorMedio(retiros, "TRANSFERENCIA");

        BigDecimal efectivoFinal = montoInicial.add(ventasEfectivo).subtract(retirosEfectivo);
        BigDecimal totalDigital = ventasTransferencia.subtract(retirosTransferencia).add(ventasTarjeta);
        BigDecimal cajaTotal = efectivoFinal.add(totalDigital);

        return new ResumenDiaDTO(ventas, retiros, montoInicial, ventasEfectivo, ventasTransferencia, ventasTarjeta,
            retirosEfectivo, retirosTransferencia, efectivoFinal, totalDigital, cajaTotal);
    }

    private BigDecimal sumarVentasPorMedio(List<Venta> ventas, String medioPago) {
        return ventas.stream()
            .filter(v -> medioPago.equalsIgnoreCase(v.getMedioPago()))
            .map(Venta::getTotalVenta)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumarRetirosPorMedio(List<MovimientoCaja> retiros, String medioPago) {
        return retiros.stream()
            .filter(m -> medioPago.equalsIgnoreCase(m.getMedioPago()))
            .map(MovimientoCaja::getMonto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
