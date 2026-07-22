package service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import repository.MovimientoCajaRepository;
import repository.VentaRepository;

public class CajaService {
	@Autowired private VentaRepository ventaRepo;
    @Autowired private MovimientoCajaRepository movRepo;

    public ResumenDiaDTO calcularResumenDelDia() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime desde = hoy.atStartOfDay();
        LocalDateTime hasta = hoy.atTime(23, 59, 59);

        List<Venta> ventas = ventaRepo.findByFechaBetween(desde, hasta);
        List<MovimientoCaja> retiros = movRepo.findByFechaBetween(desde, hasta);

        double ventasEfectivo = ventas.stream()
            .filter(v -> "EFECTIVO".equals(v.getMedioPago()))
            .mapToDouble(Venta::getTotalVenta).sum();

        double retirosEfectivo = retiros.stream()
            .filter(m -> "EFECTIVO".equals(m.getMedioPago()))
            .mapToDouble(MovimientoCaja::getMonto).sum();

        // mismo patrón para transferencia y tarjeta...

        return new ResumenDiaDTO(ventas, retiros, ventasEfectivo, retirosEfectivo /* , ... */);
    }
}
