package com.thiago.escenasFX.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.DetalleVenta;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.FacturaFiscalRepository;
import com.thiago.escenasFX.repository.NotaCreditoRepository;
import com.thiago.escenasFX.repository.VentaRepository;

/**
 * Orquesta la emisión de la factura fiscal (Factura C, D13 es Monotributo) de una Venta ya
 * CONFIRMADA. Si ARCA rechaza o falla la conexión, la FacturaFiscal queda en estado ERROR (con el
 * detalle) para poder reintentar y la venta sigue SIN_FACTURAR. ARCA no participa de la transacción
 * y un CAE no se puede deshacer: por eso todo se valida antes de llamarlo.
 */
@Service
public class FacturaFiscalService {

    private static final int TIPO_FACTURA_C = 11;
    private static final int DOC_CONSUMIDOR_FINAL = 99;
    static final int CONDICION_CONSUMIDOR_FINAL = 5;
    private static final List<Integer> CONDICIONES_IVA = List.of(1, 4, 5, 6, 7, 8, 9, 10, 13, 15, 16);

    private final FacturaFiscalRepository facturaRepo;
    private final VentaRepository ventaRepo;
    private final NotaCreditoRepository notaCreditoRepo;
    private final AfipFacturacionService afipFacturacionService;
    private final int puntoVenta;

    public FacturaFiscalService(FacturaFiscalRepository facturaRepo, VentaRepository ventaRepo,
            NotaCreditoRepository notaCreditoRepo, AfipFacturacionService afipFacturacionService,
            @Value("${afip.punto-venta}") int puntoVenta) {
        this.facturaRepo = facturaRepo;
        this.ventaRepo = ventaRepo;
        this.notaCreditoRepo = notaCreditoRepo;
        this.afipFacturacionService = afipFacturacionService;
        this.puntoVenta = puntoVenta;
    }

    /** Factura a nombre de un cliente del catálogo y, si ARCA aprueba, se lo asigna a la venta. */
    @Transactional
    public FacturaFiscal facturarACliente(Integer idVenta, Cliente cliente) {
        return emitir(idVenta, cliente.getDocTipo(), cliente.getDocNro(), cliente.getNombre(),
            cliente.getCondicionIva(), cliente);
    }

    @Transactional
    public FacturaFiscal facturar(Integer idVenta, Integer clienteDocTipo, String clienteDocNro, String clienteNombre,
            Integer condicionIvaReceptor) {
        return emitir(idVenta, clienteDocTipo, clienteDocNro, clienteNombre, condicionIvaReceptor, null);
    }

    private FacturaFiscal emitir(Integer idVenta, Integer clienteDocTipo, String clienteDocNro, String clienteNombre,
            Integer condicionIvaReceptor, Cliente cliente) {
        // El lock evita que dos pedidos simultáneos pasen juntos el chequeo y obtengan dos CAE.
        Venta venta = ventaRepo.buscarPorIdConLock(idVenta)
            .orElseThrow(() -> new IllegalArgumentException("Venta no existe: " + idVenta));

        if (!"CONFIRMADA".equals(venta.getEstado())) {
            throw new IllegalStateException("Solo se puede facturar una venta CONFIRMADA");
        }
        facturaRepo.findByVentaIdVenta(idVenta).ifPresent(f -> {
            if ("EMITIDA".equals(f.getEstado())) {
                throw new IllegalStateException("La venta #" + idVenta + " ya tiene una factura fiscal emitida");
            }
        });
        // La factura sale siempre por el total: tras una nota de crédito estaría facturando plata devuelta.
        if (notaCreditoRepo.existsByVentaOriginalIdVenta(idVenta)) {
            throw new IllegalStateException("La venta #" + idVenta + " tiene notas de crédito: no se puede facturar");
        }
        validarClienteDeLaVenta(venta, cliente);
        if (clienteDocTipo != DOC_CONSUMIDOR_FINAL && (clienteDocNro == null || clienteDocNro.isBlank())) {
            throw new IllegalArgumentException("Falta el número de documento del cliente");
        }
        // El nombre no lo pide ARCA (WSFEv1 no lo tiene), pero toda factura real lo imprime — se
        // exige acá igual que el número de documento, mismo criterio.
        if (clienteDocTipo != DOC_CONSUMIDOR_FINAL && (clienteNombre == null || clienteNombre.isBlank())) {
            throw new IllegalArgumentException("Falta el nombre o razón social del cliente");
        }
        int condicionIva = resolverCondicionIva(clienteDocTipo, condicionIvaReceptor);

        FacturaFiscal factura = facturaRepo.findByVentaIdVenta(idVenta).orElseGet(FacturaFiscal::new);
        factura.setVenta(venta);
        factura.setPuntoVenta(puntoVenta);
        factura.setTipoComprobante(TIPO_FACTURA_C);
        factura.setClienteDocTipo(clienteDocTipo);
        factura.setClienteDocNro(clienteDocTipo == DOC_CONSUMIDOR_FINAL ? null : clienteDocNro);
        factura.setClienteNombre(clienteDocTipo == DOC_CONSUMIDOR_FINAL ? null : clienteNombre);
        factura.setCondicionIvaReceptor(condicionIva);
        factura.setImporte(venta.getTotalVenta());
        factura.setEstado("PENDIENTE");
        factura.setErrorDetalle(null);

        LocalDate fecha = venta.getFecha().toLocalDate();
        int concepto = concepto(venta.getDetalles());
        AfipFacturacionService.DatosFactura datos = new AfipFacturacionService.DatosFactura(
            puntoVenta, TIPO_FACTURA_C, concepto, clienteDocTipo,
            clienteDocTipo == DOC_CONSUMIDOR_FINAL ? "0" : clienteDocNro,
            venta.getTotalVenta(), fecha,
            concepto != 1 ? fecha : null, concepto != 1 ? fecha : null, concepto != 1 ? fecha : null,
            null, null, null, condicionIva);

        try {
            AfipFacturacionService.ResultadoCae resultado = afipFacturacionService.emitirFacturaC(datos);
            if (resultado.aprobado()) {
                factura.setNumero(resultado.numero());
                factura.setCae(resultado.cae());
                factura.setCaeVencimiento(resultado.caeVencimiento());
                factura.setEstado("EMITIDA");
                if (cliente != null) {
                    venta.setCliente(cliente);
                    ventaRepo.save(venta);
                }
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

    // Una venta que movió la cuenta corriente de un cliente no puede facturarse a otro.
    private void validarClienteDeLaVenta(Venta venta, Cliente cliente) {
        if (cliente == null || venta.getCliente() == null
                || venta.getCliente().getIdCliente().equals(cliente.getIdCliente())) {
            return;
        }
        boolean movioCuenta = "CUENTA_CORRIENTE".equals(venta.getMedioPago()) || venta.getMontoSaldoAplicado() != null;
        if (movioCuenta) {
            throw new IllegalStateException("La venta #" + venta.getIdVenta()
                + " está en la cuenta corriente de " + venta.getCliente().getNombre() + ": hay que facturarla a ese cliente");
        }
    }

    private int resolverCondicionIva(Integer clienteDocTipo, Integer condicionIvaReceptor) {
        if (clienteDocTipo == DOC_CONSUMIDOR_FINAL) {
            return CONDICION_CONSUMIDOR_FINAL;
        }
        if (condicionIvaReceptor == null) {
            if (clienteDocTipo == 80) {
                throw new IllegalArgumentException("Falta la condición frente al IVA del cliente");
            }
            return CONDICION_CONSUMIDOR_FINAL;
        }
        if (!CONDICIONES_IVA.contains(condicionIvaReceptor)) {
            throw new IllegalArgumentException("Condición frente al IVA inválida: " + condicionIvaReceptor);
        }
        return condicionIvaReceptor;
    }

    public java.util.Optional<FacturaFiscal> obtenerPorVenta(Integer idVenta) {
        return facturaRepo.findByVentaIdVenta(idVenta);
    }

    /**
     * 1 = Productos, 2 = Servicios, 3 = Productos y Servicios (según AFIP). Package-private (no
     * private): NotaCreditoFiscalService lo reusa para calcular el concepto de la nota de
     * crédito a partir de las líneas de venta originales que acredita.
     */
    static int concepto(List<DetalleVenta> detalles) {
        boolean hayServicio = detalles.stream().anyMatch(d -> "SERVICIO".equals(d.getTipo()));
        boolean hayProducto = detalles.stream().anyMatch(d -> !"SERVICIO".equals(d.getTipo()));
        if (hayServicio && hayProducto) {
            return 3;
        }
        return hayServicio ? 2 : 1;
    }
}
