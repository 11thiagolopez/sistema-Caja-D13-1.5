
	package persistence;

	import java.io.File;
import java.sql.Connection;
	import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
	import java.sql.Statement;

	public class ConexionDB {
	    // Usamos una ruta fuera de la carpeta de usuario para que sea compartida
	    private static final String URL = "jdbc:sqlite:C:/CajaCompartida/sistema_d13.db";

	    public static Connection conectar() {
	        try {
	            // 1. Verificamos si la carpeta existe, si no, la creamos
	            File directorio = new File("C:/CajaCompartida");
	            if (!directorio.exists()) {
	                directorio.mkdirs();
	                System.out.println("📁 Carpeta 'CajaCompartida' creada automáticamente.");
	            }

	            return DriverManager.getConnection(URL);
	        } catch (SQLException e) {
	            System.err.println("❌ Error al conectar: " + e.getMessage());
	            return null;
	        }
	    }
	    public static Double obtenerMontoInicialHoy() {
	        // Buscamos una sesión abierta con la fecha de hoy
	        String sql = "SELECT monto_inicial FROM sesiones WHERE fecha = date('now','localtime') AND estado = 'ABIERTA' LIMIT 1";

	        try (Connection conn = conectar(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
	            if (rs.next()) {
	                return rs.getDouble("monto_inicial"); // Encontró una sesión
	            }
	        } catch (SQLException e) {
	            System.err.println("❌ Error al verificar sesión: " + e.getMessage());
	        }
	        return null; // No hay sesión abierta hoy
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
	    public static void insertarSesion(double montoInicial) {
	        // Insertamos la sesión con fecha de hoy y estado ABIERTA
	        String sql = "INSERT INTO sesiones (monto_inicial, fecha, estado) VALUES (?, date('now','localtime'), 'ABIERTA')";

	        try (Connection conn = conectar(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
	            pstmt.setDouble(1, montoInicial);
	            pstmt.executeUpdate();
	            System.out.println("✅ Sesión de caja iniciada en la base de datos.");
	        } catch (SQLException e) {
	            System.err.println("❌ Error al iniciar sesión: " + e.getMessage());
	        }
	    }
	}

