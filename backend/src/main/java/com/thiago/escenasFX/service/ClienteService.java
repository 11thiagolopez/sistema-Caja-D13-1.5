package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thiago.escenasFX.dto.ClienteRequest;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.FacturaFiscal;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;
import com.thiago.escenasFX.repository.VentaRepository;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepo;
    private final MovimientoCuentaCorrienteClienteRepository movimientoRepo;
    private final VentaRepository ventaRepo;

    public ClienteService(ClienteRepository clienteRepo, MovimientoCuentaCorrienteClienteRepository movimientoRepo,
            VentaRepository ventaRepo) {
        this.clienteRepo = clienteRepo;
        this.movimientoRepo = movimientoRepo;
        this.ventaRepo = ventaRepo;
    }

    /**
     * Guarda en el catálogo al cliente de una factura emitida, si es nuevo, y lo vincula a la venta
     * si no tenía uno (la Nota de Crédito lo toma de ahí). La factura conserva su propia copia del
     * nombre y documento: editar el cliente después no altera un comprobante ya emitido.
     */
    @Transactional
    public void registrarDesdeFactura(FacturaFiscal factura) {
        if (factura.getClienteDocTipo() == 99) {
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

    public Cliente crear(ClienteRequest req) {
        Cliente cliente = new Cliente();
        aplicar(cliente, req);
        return clienteRepo.save(cliente);
    }

    public Cliente actualizar(Integer id, ClienteRequest req) {
        Cliente cliente = obtenerPorId(id);
        aplicar(cliente, req);
        return clienteRepo.save(cliente);
    }

    public void eliminar(Integer id) {
        Cliente cliente = obtenerPorId(id);
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

    // El saldo nunca se guarda: se recalcula siempre sumando CREDITO menos DEBITO del ledger, para
    // que la cuenta corriente quede auditable (ver MovimientoCuentaCorrienteCliente).
    public BigDecimal calcularSaldo(Integer idCliente) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (MovimientoCuentaCorrienteCliente m : movimientos(idCliente)) {
            saldo = "CREDITO".equals(m.getTipo()) ? saldo.add(m.getMonto()) : saldo.subtract(m.getMonto());
        }
        return saldo;
    }

    private void aplicar(Cliente cliente, ClienteRequest req) {
        cliente.setNombre(req.getNombre());
        cliente.setDocTipo(req.getDocTipo());
        cliente.setDocNro(req.getDocNro());
        cliente.setCondicionIva(req.getCondicionIva());
        cliente.setTelefono(req.getTelefono());
        cliente.setEmail(req.getEmail());
        cliente.setDireccion(req.getDireccion());
    }
}
