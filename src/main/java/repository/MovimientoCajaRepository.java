package repository;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {
    List<MovimientoCaja> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);
} 
