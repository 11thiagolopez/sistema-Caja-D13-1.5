
	package model;

	import jakarta.persistence.*;

	@Entity
	@Table(name = "detalle_ventas")
	public class DetalleVenta {

	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    @Column(name = "id_detalle")
	    private Long idDetalle;

	    @ManyToOne
	    @JoinColumn(name = "id_venta")
	    private Venta venta;

	    @ManyToOne
	    @JoinColumn(name = "id_producto")
	    private Producto producto;

	    private int cantidad;

	    @Column(name = "precio_unitario")
	    private double precioUnitario;

	    private double subtotal;

}
