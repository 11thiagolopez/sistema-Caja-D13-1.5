package com.thiago.escenasFX.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Orquesta la emisión de la factura fiscal (Factura C, D13 es Monotributo) de una Venta ya
 * CONFIRMADA — acción aparte del comprobante interno, dispara desde Consulta de ventas. Si ARCA
 * rechaza o falla la conexión, la FacturaFiscal queda en estado ERROR (con el detalle) para poder
 * reintentar; la Venta en sí no se toca, ya está confirmada independientemente de esto.
 */
@Service
public class FacturaFiscalService {

    private static final int TIPO_FACTURA_C = 11;

    private final FacturaFiscalRepository facturaRepo;
    private final VentaRepository ventaRepo;
    private final AfipFacturacionService afipFacturacionService;
    private final int puntoVenta;

    public FacturaFiscalService(FacturaFiscalRepository facturaRepo, VentaRepository ventaRepo,
            AfipFacturacionService afipFacturacionService, @Value("${afip.punto-venta}") int puntoVenta) {
        this.facturaRepo = facturaRepo;
        this.ventaRepo = ventaRepo;
        this.afipFacturacionService = afipFacturacionService;
        this.puntoVenta = puntoVenta;
    }

    @Transactional
    public FacturaFiscal facturar(Integer idVenta, Integer clienteDocTipo, String clienteDocNro) {
        Venta venta = ventaRepo.findById(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + idVenta));

        if (!"CONFIRMADA".equals(venta.getEstado())) {
            throw new IllegalStateException("Solo se puede facturar una venta CONFIRMADA");
        }
        facturaRepo.findByVentaIdVenta(idVenta).ifPresent(f -> {
            if ("EMITIDA".equals(f.getEstado())) {
                throw new IllegalStateException("La venta #" + idVenta + " ya tiene una factura fiscal emitida");
            }
        });
        if (clienteDocTipo != 99 && (clienteDocNro == null || clienteDocNro.isBlank())) {
            throw new IllegalArgumentException("Falta el número de documento del cliente");
        }

        FacturaFiscal factura = facturaRepo.findByVentaIdVenta(idVenta).orElseGet(FacturaFiscal::new);
        factura.setVenta(venta);
        factura.setPuntoVenta(puntoVenta);
        factura.setTipoComprobante(TIPO_FACTURA_C);
        factura.setClienteDocTipo(clienteDocTipo);
        factura.setClienteDocNro(clienteDocTipo == 99 ? null : clienteDocNro);
        factura.setImporte(venta.getTotalVenta());
        factura.setEstado("PENDIENTE");
        factura.setErrorDetalle(null);

        LocalDate fecha = venta.getFecha().toLocalDate();
        int concepto = concepto(venta.getDetalles());
        AfipFacturacionService.DatosFactura datos = new AfipFacturacionService.DatosFactura(
            puntoVenta, TIPO_FACTURA_C, concepto, clienteDocTipo, clienteDocTipo == 99 ? "0" : clienteDocNro,
            venta.getTotalVenta(), fecha,
            concepto != 1 ? fecha : null, concepto != 1 ? fecha : null, concepto != 1 ? fecha : null);

        try {
            AfipFacturacionService.ResultadoCae resultado = afipFacturacionService.emitirFacturaC(datos);
            if (resultado.aprobado()) {
                factura.setNumero(resultado.numero());
                factura.setCae(resultado.cae());
                factura.setCaeVencimiento(resultado.caeVencimiento());
                factura.setEstado("EMITIDA");
            } else {
                factura.setEstado("ERROR");
                factura.setErrorDetalle(resultado.detalle());
            }
        } catch (RuntimeException e) {
            factura.setEstado("ERROR");
            factura.setErrorDetalle(e.getMessage());
        }

        return facturaRepo.save(factura);
    }

    public java.util.Optional<FacturaFiscal> obtenerPorVenta(Integer idVenta) {
        return facturaRepo.findByVentaIdVenta(idVenta);
    }

    /** 1 = Productos, 2 = Servicios, 3 = Productos y Servicios (según AFIP). */
    private int concepto(List<DetalleVenta> detalles) {
        boolean hayServicio = detalles.stream().anyMatch(d -> "SERVICIO".equals(d.getTipo()));
        boolean hayProducto = detalles.stream().anyMatch(d -> !"SERVICIO".equals(d.getTipo()));
        if (hayServicio && hayProducto) {
            return 3;
        }
        return hayServicio ? 2 : 1;
    }
}
