package com.thiago.escenasFX.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.thiago.escenasFX.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    List<Cliente> findByActivoTrueOrderByNombreAsc();

    Optional<Cliente> findByDocTipoAndDocNro(Integer docTipo, String docNro);

    // Nada impide cargar dos clientes con el mismo documento: el Optional de arriba explotaría.
    Optional<Cliente> findFirstByDocTipoAndDocNroOrderByIdClienteAsc(Integer docTipo, String docNro);

    List<Cliente> findByActivoTrueAndNombreContainingIgnoreCaseOrActivoTrueAndDocNroContainingIgnoreCaseOrderByNombreAsc(
        String nombre, String docNro);

    // Lock pessimista para debitar saldo sin condición de carrera (usado desde la fase 3, cuando
    // el saldo a favor se aplique como medio de pago en Cobros) — mismo patrón que
    // ProductoRepository.buscarPorIdConLock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cliente c WHERE c.idCliente = :id")
    Optional<Cliente> buscarPorIdConLock(@Param("id") Integer id);
}
