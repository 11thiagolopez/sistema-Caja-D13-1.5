package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.exception.AuthenticationFailedException;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.Producto;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ProductoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class VentaService {

    private final VentaRepository ventaRepo;
    private final ProductoRepository productoRepo;
    private final OtpService otpService;
    private final EmailService emailService;

    public VentaService(VentaRepository ventaRepo, ProductoRepository productoRepo, OtpService otpService,
            EmailService emailService) {
        this.ventaRepo = ventaRepo;
        this.productoRepo = productoRepo;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    /**
     * El stock se valida y descuenta siempre en este método, incluso si la venta queda
     * PENDIENTE_AUTORIZACION por un descuento manual (se "reserva" de inmediato; no hay
     * liberación automática de stock si el OTP nunca se confirma).
     */
    @Transactional
    public Venta registrarVenta(Venta venta) {
        BigDecimal totalBruto = BigDecimal.ZERO;

        for (DetalleVenta d : venta.getDetalles()) {
            Producto p = productoRepo.findById(d.getProducto().getIdProducto())
                .orElseThrow(() -> new IllegalArgumentException("Producto no existe"));

            if (p.getStockActual() < d.getCantidad()) {
                throw new IllegalStateException("Stock insuficiente para " + p.getDescripcion());
            }

            p.setStockActual(p.getStockActual() - d.getCantidad());
            productoRepo.save(p);

            BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
            d.setSubtotal(subtotal);
            d.setVenta(venta);

            totalBruto = totalBruto.add(subtotal);
        }

        BigDecimal descuento = venta.getDescuento() != null ? venta.getDescuento() : BigDecimal.ZERO;
        if (descuento.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo");
        }
        if (descuento.compareTo(totalBruto) > 0) {
            throw new IllegalArgumentException("El descuento no puede ser mayor al total de la venta");
        }
        if (descuento.compareTo(BigDecimal.ZERO) > 0
                && (venta.getMotivoDescuento() == null || venta.getMotivoDescuento().isBlank())) {
            throw new IllegalArgumentException("Un descuento manual requiere indicar un motivo");
        }

        venta.setDescuento(descuento);
        venta.setTotalVenta(totalBruto.subtract(descuento));

        if (descuento.compareTo(BigDecimal.ZERO) > 0) {
            String codigo = otpService.generarCodigo();
            venta.setEstado("PENDIENTE_AUTORIZACION");
            venta.setOtpHash(otpService.hash(codigo));
            venta.setOtpExpiraEn(otpService.nuevaExpiracion());

            Venta guardada = ventaRepo.save(venta);

            emailService.enviarOtpAAdmins(
                "Autorización de descuento - venta #" + guardada.getIdVenta(),
                "Se registró la venta #" + guardada.getIdVenta() + " con un descuento de $" + descuento
                    + " (" + guardada.getMotivoDescuento() + ").\n"
                    + "Código de confirmación: " + codigo + "\n"
                    + "Vence en " + OtpService.VIGENCIA_MINUTOS + " minutos.");

            return guardada;
        }

        venta.setEstado("CONFIRMADA");
        return ventaRepo.save(venta);
    }

    @Transactional
    public Venta confirmarDescuento(Integer idVenta, String codigoIngresado) {
        Venta venta = ventaRepo.findById(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + idVenta));

        if (!"PENDIENTE_AUTORIZACION".equals(venta.getEstado())) {
            throw new IllegalStateException("La venta no tiene un descuento pendiente de autorización");
        }
        if (venta.getOtpExpiraEn().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("El código OTP expiró; la venta sigue pendiente de autorización");
        }
        if (!otpService.coincide(codigoIngresado, venta.getOtpHash())) {
            throw new AuthenticationFailedException("Código OTP inválido");
        }

        venta.setEstado("CONFIRMADA");
        venta.setOtpHash(null);
        venta.setOtpExpiraEn(null);
        return ventaRepo.save(venta);
    }
}
