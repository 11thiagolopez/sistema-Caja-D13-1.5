package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.thiago.escenasFX.dto.ClienteRequest;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepo;
    private final MovimientoCuentaCorrienteClienteRepository movimientoRepo;

    public ClienteService(ClienteRepository clienteRepo, MovimientoCuentaCorrienteClienteRepository movimientoRepo) {
        this.clienteRepo = clienteRepo;
        this.movimientoRepo = movimientoRepo;
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
        cliente.setTelefono(req.getTelefono());
        cliente.setEmail(req.getEmail());
        cliente.setDireccion(req.getDireccion());
    }
}
