package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.ResumenDiaDTO;
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

    public SesionCaja abrirSesion(BigDecimal montoInicial, Empleado empleado) {
        LocalDate hoy = LocalDate.now();
        sesionRepo.findByFechaAndEstado(hoy, "ABIERTA").ifPresent(s -> {
            throw new IllegalStateException("Ya existe una sesión de caja abierta hoy");
        });

        SesionCaja sesion = new SesionCaja();
        sesion.setFecha(hoy);
        sesion.setMontoInicial(montoInicial);
        sesion.setEstado("ABIERTA");
        sesion.setEmpleadoApertura(empleado);
        return sesionRepo.save(sesion);
    }

    public SesionCaja obtenerSesionAbiertaDeHoy() {
        return sesionRepo.findByFechaAndEstado(LocalDate.now(), "ABIERTA")
            .orElseThrow(() -> new IllegalStateException("No hay una sesión de caja abierta hoy"));
    }

    public SesionCaja cerrarSesionDelDia() {
        SesionCaja sesion = obtenerSesionAbiertaDeHoy();
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
            "Se solicitó un retiro de $" + monto + " (" + motivo + ").\n"
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
        return movRepo.save(movimiento);
    }

    public ResumenDiaDTO calcularResumenDelDia() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime desde = hoy.atStartOfDay();
        LocalDateTime hasta = hoy.atTime(23, 59, 59);

        // Solo ventas CONFIRMADA: una venta con descuento aún PENDIENTE_AUTORIZACION todavía
        // no es un ingreso real, no debe inflar el arqueo de caja del día.
        List<Venta> ventas = ventaRepo.findByFechaBetweenAndEstado(desde, hasta, ESTADO_CONFIRMADA);
        List<MovimientoCaja> retiros = movRepo.findByFechaBetween(desde, hasta);

        BigDecimal montoInicial = sesionRepo.findByFechaAndEstado(hoy, "ABIERTA")
            .map(SesionCaja::getMontoInicial)
            .orElse(BigDecimal.ZERO);

        BigDecimal ventasEfectivo = sumarVentasPorMedio(ventas, "EFECTIVO");
        BigDecimal ventasTransferencia = sumarVentasPorMedio(ventas, "TRANSFERENCIA");
        BigDecimal ventasTarjeta = sumarVentasPorMedio(ventas, "TARJETA");

        BigDecimal retirosEfectivo = sumarRetirosPorMedio(retiros, "EFECTIVO");
        BigDecimal retirosTransferencia = sumarRetirosPorMedio(retiros, "TRANSFERENCIA");

        BigDecimal efectivoFinal = montoInicial.add(ventasEfectivo).subtract(retirosEfectivo);
        BigDecimal totalDigital = ventasTransferencia.subtract(retirosTransferencia).add(ventasTarjeta);
        BigDecimal cajaTotalDelDia = efectivoFinal.add(totalDigital);

        return new ResumenDiaDTO(ventas, retiros, montoInicial, ventasEfectivo, ventasTransferencia, ventasTarjeta,
            retirosEfectivo, retirosTransferencia, efectivoFinal, totalDigital, cajaTotalDelDia);
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
