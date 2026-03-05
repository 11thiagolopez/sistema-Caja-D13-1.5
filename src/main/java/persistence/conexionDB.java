
	package persistence;

	import java.sql.Connection;
	import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
	import java.sql.Statement;

	public class conexionDB {
	    // Usamos una ruta fuera de la carpeta de usuario para que sea compartida
	    private static final String URL = "jdbc:sqlite:C:/CajaCompartida/sistema_d13.db";

	    public static Connection conectar() {
	        try {
	            return DriverManager.getConnection(URL);
	        } catch (SQLException e) {
	            System.err.println("Error al conectar a SQLite: " + e.getMessage());
	            return null;
	        }
	    }

	    public static void crearTablas() {
	        String sqlVentas = "CREATE TABLE IF NOT EXISTS ventas ("
	                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
	                + "fecha TEXT DEFAULT (datetime('now','localtime')),"
	                + "descripcion TEXT,"
	                + "cantidad INTEGER,"
	                + "precio REAL,"
	                + "medio_pago TEXT,"
	                + "tipo TEXT" // VENTA o RETIRO
	                + ");";

	        try (Connection conn = conectar(); Statement stmt = conn.createStatement()) {
	            stmt.execute(sqlVentas);
	            System.out.println("✅ Base de datos lista en ruta compartida.");
	        } catch (SQLException e) {
	            e.printStackTrace();
	        }
	    }
	    public static void insertarVenta(String desc, int cant, double precio, String medio, String tipo) {
	        String sql = "INSERT INTO ventas (descripcion, cantidad, precio, medio_pago, tipo) VALUES (?, ?, ?, ?, ?)";

	        try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
	            pstmt.setString(1, desc);
	            pstmt.setInt(2, cant);
	            pstmt.setDouble(3, precio);
	            pstmt.setString(4, medio);
	            pstmt.setString(5, tipo);
	            pstmt.executeUpdate();
	            System.out.println("✅ Movimiento guardado en SQLite.");
	        } catch (SQLException e) {
	            System.out.println("❌ Error al insertar: " + e.getMessage());
	        }
	    }
	}

