package model;

	import jakarta.persistence.*;

	@Entity
	@Table(name = "productos")
	public class Producto {

	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    @Column(name = "id_producto")
	    private Long idProducto;

	    private String rubro;
	    private String familia;
	    private String marca;
	    private String correlativo;

	    @Column(name = "codigo_interno")
	    private String codigoInterno;

	    private String proveedor;

	    @Column(name = "codigo_fabrica")
	    private String codigoFabrica;

	    private String descripcion;

	    @Column(name = "precio_venta")
	    private double precioVenta;

	    @Column(name = "stock_actual")
	    private int stockActual;
}

