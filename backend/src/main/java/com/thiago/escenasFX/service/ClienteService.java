package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.ClienteRequest;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.model.SesionCaja;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;
import com.thiago.escenasFX.repository.SesionCajaRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class ClienteService {

    private static final int DOC_CUIT = 80;
    private static final int DOC_DNI = 96;
    private static final int DOC_SIN_DOCUMENTO = 99;
    private static final int CONDICION_CONSUMIDOR_FINAL = 5;
    private static final String NOMBRE_CONSUMIDOR_FINAL = "Consumidor Final";
    private static final List<String> MEDIOS_PAGO_CUENTA = List.of("EFECTIVO", "TRANSFERENCIA", "TARJETA");

    private final ClienteRepository clienteRepo;
    private final MovimientoCuentaCorrienteClienteRepository movimientoRepo;
    private final VentaRepository ventaRepo;
    private final SesionCajaRepository sesionRepo;

    public ClienteService(ClienteRepository clienteRepo, MovimientoCuentaCorrienteClienteRepository movimientoRepo,
            VentaRepository ventaRepo, SesionCajaRepository sesionRepo) {
        this.clienteRepo = clienteRepo;
        this.movimientoRepo = movimientoRepo;
        this.ventaRepo = ventaRepo;
        this.sesionRepo = sesionRepo;
    }

    /**
     * Guarda en el catálogo al cliente de una factura emitida, si es nuevo, y lo vincula a la venta
     * si no tenía uno (la Nota de Crédito lo toma de ahí). La factura conserva su propia copia del
     * nombre y documento: editar el cliente después no altera un comprobante ya emitido.
     */
    @Transactional
    public void registrarDesdeFactura(FacturaFiscal factura) {
        if (factura.getClienteDocTipo() == DOC_SIN_DOCUMENTO) {
            return;
        }
        Cliente cliente = clienteRepo
            .findFirstByDocTipoAndDocNroOrderByIdClienteAsc(factura.getClienteDocTipo(), factura.getClienteDocNro())
            .orElseGet(() -> {
                Cliente nuevo = new Cliente();
                nuevo.setNombre(factura.getClienteNombre());
                nuevo.setDocTipo(factura.getClienteDocTipo());
                nuevo.setDocNro(factura.getClienteDocNro());
                return nuevo;
            });
        if (cliente.getCondicionIva() == null) {
            cliente.setCondicionIva(factura.getCondicionIvaReceptor());
        }
        Cliente guardado = clienteRepo.save(cliente);

        ventaRepo.findById(factura.getVenta().getIdVenta())
            .filter(venta -> venta.getCliente() == null)
            .ifPresent(venta -> {
                venta.setCliente(guardado);
                ventaRepo.save(venta);
            });
    }

    public List<Cliente> listar() {
        return clienteRepo.findByActivoTrueOrderByNombreAsc();
    }

    public List<Cliente> buscar(String q) {
        return clienteRepo
            .findByActivoTrueAndNombreContainingIgnoreCaseOrActivoTrueAndDocNroContainingIgnoreCaseOrderByNombreAsc(
                q, q);
    }

    @Transactional
    public Cliente crear(ClienteRequest req) {
        validar(req);
        Optional<Cliente> existente = buscarPorDocumento(req);
        if (existente.filter(Cliente::isActivo).isPresent()) {
            throw new IllegalStateException("Ya existe un cliente con ese documento: " + existente.get().getNombre());
        }
        // Dado de baja: se reactiva, porque su historial y su saldo siguen siendo de la misma persona.
        Cliente cliente = existente.orElseGet(Cliente::new);
        cliente.setActivo(true);
        aplicar(cliente, req);
        return clienteRepo.save(cliente);
    }

    @Transactional
    public Cliente actualizar(Integer id, ClienteRequest req) {
        validar(req);
        Cliente cliente = obtenerPorId(id);
        buscarPorDocumento(req)
            .filter(otro -> !otro.getIdCliente().equals(id))
            .ifPresent(otro -> {
                throw new IllegalStateException("Ya existe un cliente con ese documento: " + otro.getNombre());
            });
        aplicar(cliente, req);
        return clienteRepo.save(cliente);
    }

    private Optional<Cliente> buscarPorDocumento(ClienteRequest req) {
        if (req.getDocTipo() == DOC_SIN_DOCUMENTO) {
            return Optional.empty();
        }
        return clienteRepo.findFirstByDocTipoAndDocNroOrderByIdClienteAsc(req.getDocTipo(), req.getDocNro());
    }

    @Transactional
    public void eliminar(Integer id) {
        Cliente cliente = obtenerPorId(id);
        if (calcularSaldo(id).signum() != 0) {
            throw new IllegalStateException("No se puede dar de baja a un cliente con saldo en su cuenta corriente");
        }
        cliente.setActivo(false);
        clienteRepo.save(cliente);
    }

    public Cliente obtenerPorId(Integer id) {
        return clienteRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Cliente no existe: " + id));
    }

    public List<MovimientoCuentaCorrienteCliente> movimientos(Integer idCliente) {
        return movimientoRepo.findByClienteIdClienteOrderByFechaDesc(idCliente);
    }

    public List<Venta> ventas(Integer idCliente) {
        return ventaRepo.findByCliente_IdClienteOrderByFechaDesc(idCliente);
    }

    // El saldo nunca se guarda: se recalcula siempre sumando CREDITO menos DEBITO del ledger, para
    // que la cuenta corriente quede auditable (ver MovimientoCuentaCorrienteCliente).
    public BigDecimal calcularSaldo(Integer idCliente) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (MovimientoCuentaCorrienteCliente m : movimientos(idCliente)) {
            saldo = "CREDITO".equals(m.getTipo()) ? saldo.add(m.getMonto()) : saldo.subtract(m.getMonto());
        }
        return saldo;
    }

    public Map<Integer, BigDecimal> saldosPorCliente() {
        Map<Integer, BigDecimal> saldos = new HashMap<>();
        for (Object[] fila : movimientoRepo.saldosPorCliente()) {
            saldos.put((Integer) fila[0], (BigDecimal) fila[1]);
        }
        return saldos;
    }

    /**
     * Pago del cliente a su cuenta corriente. Es dinero real: queda en la caja abierta y entra al
     * arqueo por su medio de pago. El efectivo exige caja abierta porque, sin sesión, quedaría fuera
     * del arqueo de cualquier turno. Pagar de más deja saldo a favor.
     */
    @Transactional
    public MovimientoCuentaCorrienteCliente registrarPago(Integer idCliente, BigDecimal monto, String medioPago,
            String descripcion, Empleado empleado) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor a cero");
        }
        if (!MEDIOS_PAGO_CUENTA.contains(medioPago)) {
            throw new IllegalArgumentException("Medio de pago inválido: " + medioPago);
        }
        Cliente cliente = clienteRepo.buscarPorIdConLock(idCliente)
            .orElseThrow(() -> new IllegalArgumentException("Cliente no existe: " + idCliente));
        Optional<SesionCaja> sesion = sesionRepo.findByEstado("ABIERTA");
        if ("EFECTIVO".equals(medioPago) && sesion.isEmpty()) {
            throw new IllegalStateException("Para registrar un pago en efectivo tiene que haber una caja abierta");
        }

        MovimientoCuentaCorrienteCliente pago = new MovimientoCuentaCorrienteCliente();
        pago.setCliente(cliente);
        pago.setTipo("CREDITO");
        pago.setMonto(monto);
        pago.setMedioPago(medioPago);
        sesion.ifPresent(pago::setSesion);
        pago.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion : "Pago de cuenta corriente");
        pago.setEmpleado(empleado);
        return movimientoRepo.save(pago);
    }

    // Consumidor Final puede no tener nombre si trae documento. Las otras condiciones son contribuyentes:
    // ARCA las identifica por CUIT. Sin condición (clientes viejos) solo se exige lo de siempre.
    private void validar(ClienteRequest req) {
        Integer docTipo = req.getDocTipo();
        if (docTipo != DOC_CUIT && docTipo != DOC_DNI && docTipo != DOC_SIN_DOCUMENTO) {
            throw new IllegalArgumentException("Tipo de documento inválido: " + docTipo);
        }
        boolean sinNumero = req.getDocNro() == null || req.getDocNro().isBlank();
        if (docTipo != DOC_SIN_DOCUMENTO && sinNumero) {
            throw new IllegalArgumentException("Falta el número de documento del cliente");
        }
        if (docTipo == DOC_CUIT && !req.getDocNro().matches("\\d{11}")) {
            throw new IllegalArgumentException("El CUIT debe tener 11 dígitos");
        }
        boolean sinNombre = req.getNombre() == null || req.getNombre().isBlank();
        Integer condicion = req.getCondicionIva();
        if (condicion != null && condicion == CONDICION_CONSUMIDOR_FINAL) {
            if (sinNombre && docTipo == DOC_SIN_DOCUMENTO) {
                throw new IllegalArgumentException("Un consumidor final sin datos no se guarda como cliente");
            }
            return;
        }
        if (condicion != null && docTipo != DOC_CUIT) {
            throw new IllegalArgumentException("Esa condición frente al IVA requiere CUIT");
        }
        if (sinNombre) {
            throw new IllegalArgumentException("Falta el nombre o razón social del cliente");
        }
    }

    private void aplicar(Cliente cliente, ClienteRequest req) {
        boolean sinNombre = req.getNombre() == null || req.getNombre().isBlank();
        cliente.setNombre(sinNombre ? NOMBRE_CONSUMIDOR_FINAL : req.getNombre());
        cliente.setDocTipo(req.getDocTipo());
        cliente.setDocNro(req.getDocTipo() == DOC_SIN_DOCUMENTO ? null : req.getDocNro());
        cliente.setCondicionIva(req.getCondicionIva());
        cliente.setTelefono(req.getTelefono());
        cliente.setEmail(req.getEmail());
        cliente.setDireccion(req.getDireccion());
    }
}
