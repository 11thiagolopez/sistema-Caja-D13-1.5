package repository;

public interface VentaRepository  extends JpaRepository<Venta, Long> {
    List<Venta> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);
} 
