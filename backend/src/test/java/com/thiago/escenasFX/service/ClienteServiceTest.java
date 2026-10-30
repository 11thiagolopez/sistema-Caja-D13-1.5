package com.thiago.escenasFX.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thiago.escenasFX.dto.ClienteRequest;
import com.thiago.escenasFX.model.Cliente;
import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;
import com.thiago.escenasFX.repository.ClienteRepository;
import com.thiago.escenasFX.repository.MovimientoCuentaCorrienteClienteRepository;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepo;
    @Mock
    private MovimientoCuentaCorrienteClienteRepository movimientoRepo;

    @InjectMocks
    private ClienteService clienteService;

    @BeforeEach
    void configuracionPorDefecto() {
        lenient().when(clienteRepo.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Cliente cliente(int id) {
        Cliente c = new Cliente();
        c.setIdCliente(id);
        c.setNombre("Cliente de prueba");
        c.setActivo(true);
        return c;
    }

    private MovimientoCuentaCorrienteCliente movimiento(String tipo, String monto) {
        MovimientoCuentaCorrienteCliente m = new MovimientoCuentaCorrienteCliente();
        m.setTipo(tipo);
        m.setMonto(new BigDecimal(monto));
        return m;
    }

    @Test
    void crear_aplicaTodosLosCamposDelRequest() {
        ClienteRequest req = new ClienteRequest();
        req.setNombre("Juan Pérez");
        req.setDocTipo(96);
        req.setDocNro("30111222");
        req.setTelefono("1122334455");
        req.setEmail("juan@test.com");
        req.setDireccion("Arce 790");

        Cliente guardado = clienteService.crear(req);

        assertThat(guardado.getNombre()).isEqualTo("Juan Pérez");
        assertThat(guardado.getDocTipo()).isEqualTo(96);
        assertThat(guardado.getDocNro()).isEqualTo("30111222");
        assertThat(guardado.isActivo()).isTrue();
    }

    @Test
    void eliminar_hacaBajaLogicaSinBorrarLaFila() {
        Cliente cliente = cliente(1);
        when(clienteRepo.findById(1)).thenReturn(Optional.of(cliente));

        clienteService.eliminar(1);

        assertThat(cliente.isActivo()).isFalse();
    }

    @Test
    void calcularSaldo_sumaCreditosYRestaDebitos() {
        when(movimientoRepo.findByClienteIdClienteOrderByFechaDesc(1)).thenReturn(List.of(
            movimiento("CREDITO", "1000"),
            movimiento("CREDITO", "500"),
            movimiento("DEBITO", "300")));

        BigDecimal saldo = clienteService.calcularSaldo(1);

        assertThat(saldo).isEqualByComparingTo("1200");
    }

    @Test
    void calcularSaldo_sinMovimientos_esCero() {
        when(movimientoRepo.findByClienteIdClienteOrderByFechaDesc(1)).thenReturn(List.of());

        assertThat(clienteService.calcularSaldo(1)).isEqualByComparingTo("0");
    }
}
