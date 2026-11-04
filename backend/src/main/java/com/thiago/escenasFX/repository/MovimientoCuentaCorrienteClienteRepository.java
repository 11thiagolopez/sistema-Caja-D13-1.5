package com.thiago.escenasFX.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.thiago.escenasFX.model.MovimientoCuentaCorrienteCliente;

public interface MovimientoCuentaCorrienteClienteRepository
        extends JpaRepository<MovimientoCuentaCorrienteCliente, Integer> {

    List<MovimientoCuentaCorrienteCliente> findByClienteIdClienteOrderByFechaDesc(Integer idCliente);

    // Los pagos del cliente son los únicos movimientos con medio de pago: son lo que entra al arqueo.
    List<MovimientoCuentaCorrienteCliente> findBySesion_IdSesionAndMedioPagoNotNull(Integer idSesion);

    List<MovimientoCuentaCorrienteCliente> findByFechaBetweenAndMedioPagoNotNull(LocalDateTime desde, LocalDateTime hasta);

    // Cada fila: [idCliente, saldo]. Para no recorrer el historial de cada cliente al listarlos.
    @Query("select m.cliente.idCliente,"
        + " coalesce(sum(case when m.tipo = 'CREDITO' then m.monto else (0 - m.monto) end), 0)"
        + " from MovimientoCuentaCorrienteCliente m group by m.cliente.idCliente")
    List<Object[]> saldosPorCliente();
}
